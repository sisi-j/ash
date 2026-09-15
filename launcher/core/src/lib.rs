//! All of ash's launcher behaviour.
//!
//! This crate knows nothing about Tauri, webviews or the UI. It is driven
//! entirely through [`Ash`], which is the single inbound seam: every test in
//! the project exercises the product through this API, and the Tauri layer is
//! a thin adapter over it that holds no logic of its own.
//!
//! Outbound, ash talks to the world only through ports - [`http::HttpPort`]
//! for the network, [`credentials::CredentialStore`] for the OS credential
//! store, and [`process::ProcessPort`] for starting the game. Tests supply
//! fakes, so no test touches the network, the credential store, or spawns a
//! JVM.

mod account;
mod auth;
mod catalogue;
mod config;
mod depot;
mod diagnostics;
mod error;
mod gamelog;
mod instance;
mod launch;
mod loader;
mod natives;
mod overrides;
mod runtime;
mod version;

pub mod credentials;
pub mod http;
pub mod process;

pub use account::{Account, Accounts};
pub use catalogue::{
    Catalogue, CatalogueEntry, CatalogueSource, VersionKind, VERSION_MANIFEST_URL,
};
pub use config::Config;
pub use depot::{Artifact, Cancel, NullSink, Plan, PrepareEvent, ProgressSink};
pub use diagnostics::Diagnostics;
pub use error::AshError;
pub use instance::{DeletionPreview, Instance, InstanceId};
pub use loader::Loader;
pub use overrides::{MachineOverrides, Resolution, DEFAULT_MEMORY_MB};
pub use process::{GameProcess, GameStatus, Invocation, InvocationView, ProcessPort};
pub use runtime::Runtime;
pub use version::Os;

use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::Serialize;

use crate::credentials::CredentialStore;
use crate::http::HttpPort;

/// A sign-in waiting on the player, as the UI needs to see it.
///
/// The device code itself is deliberately absent: it stays inside ash-core,
/// so the only thing crossing to the UI is what a player must read off the
/// screen and type into a browser.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct PendingSignIn {
    pub user_code: String,
    pub verification_uri: String,
    pub expires_at_ms: u64,
    pub interval_secs: u64,
}

/// The result of one poll.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "status", rename_all = "snake_case")]
pub enum SignInStatus {
    /// Ask again in this many seconds. The service can raise the interval.
    Waiting {
        interval_secs: u64,
    },
    Complete {
        account: Account,
    },
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or_default()
}

/// The whole of ash's public API.
pub struct Ash {
    config: Config,
    http: Arc<dyn HttpPort>,
    credentials: Arc<dyn CredentialStore>,
    process: Arc<dyn ProcessPort>,
    client_id: String,
    /// One sign-in at a time. The device code lives here rather than
    /// travelling to the UI and back.
    pending: Mutex<Option<auth::Pending>>,
    /// ash's own log. Written to from the operations worth a record: what
    /// was launched, what preparation did, and what failed.
    diagnostics: Diagnostics,
    /// Games ash has started, by instance.
    ///
    /// An exited game stays in the map: its status and the tail of its log
    /// are what the player needs *after* a crash, and dropping the entry on
    /// exit would throw both away at the moment they matter.
    games: Mutex<HashMap<String, Box<dyn GameProcess>>>,
}

impl Ash {
    /// Every collaborator is injected. There is no constructor that reaches
    /// for the real network, the real credential store, or a real directory
    /// on its own, because that is what would make the library untestable.
    pub fn new(
        config: Config,
        http: Arc<dyn HttpPort>,
        credentials: Arc<dyn CredentialStore>,
        process: Arc<dyn ProcessPort>,
        client_id: impl Into<String>,
    ) -> Self {
        Self {
            diagnostics: Diagnostics::new(&config.data_root),
            config,
            http,
            credentials,
            process,
            client_id: client_id.into(),
            pending: Mutex::new(None),
            games: Mutex::new(HashMap::new()),
        }
    }

    pub fn config(&self) -> &Config {
        &self.config
    }

    /// ash's own log, for a UI that wants to offer "show me the log".
    pub fn diagnostics(&self) -> &Diagnostics {
        &self.diagnostics
    }

    // ---- catalogue --------------------------------------------------------

    /// The version catalogue, served from cache when one exists.
    pub async fn catalogue(&self) -> Result<Catalogue, AshError> {
        catalogue::load(self.http.as_ref(), &self.config.depot_root).await
    }

    /// Fetch the catalogue from Mojang and update the cache.
    ///
    /// Falls back to the cache if Mojang cannot be reached, flagging the
    /// result as [`CatalogueSource::Cache`] so the UI can say so rather than
    /// pretending the refresh worked.
    pub async fn refresh_catalogue(&self) -> Result<Catalogue, AshError> {
        catalogue::refresh(self.http.as_ref(), &self.config.depot_root).await
    }

    // ---- sign-in ----------------------------------------------------------

    /// Start a device-code sign-in.
    ///
    /// Microsoft's codes expire in about fifteen minutes and the docs say to
    /// request one only when the player is ready, so this is called on the
    /// click and not on app open.
    pub async fn begin_sign_in(&self) -> Result<PendingSignIn, AshError> {
        let pending = auth::begin(self.http.as_ref(), &self.client_id, now_ms()).await?;
        let view = PendingSignIn {
            user_code: pending.user_code.clone(),
            verification_uri: pending.verification_uri.clone(),
            expires_at_ms: pending.expires_at_ms,
            interval_secs: pending.interval_secs,
        };
        *self.pending.lock().unwrap() = Some(pending);
        Ok(view)
    }

    /// Ask whether the player has approved it yet.
    ///
    /// The caller waits `interval_secs` between calls; the service can raise
    /// that by answering `slow_down`, which is honoured rather than treated
    /// as a failure.
    pub async fn poll_sign_in(&self) -> Result<SignInStatus, AshError> {
        let pending = self.pending.lock().unwrap().clone().ok_or(AshError::NoSignInPending)?;

        match auth::poll(self.http.as_ref(), &self.client_id, &pending, now_ms()).await {
            Ok(auth::Poll::Waiting { interval_secs }) => {
                Ok(SignInStatus::Waiting { interval_secs })
            }
            Ok(auth::Poll::Complete(session)) => {
                *self.pending.lock().unwrap() = None;
                let account = account::upsert(
                    &self.config.data_root,
                    self.credentials.as_ref(),
                    &session.profile_id,
                    &session.username,
                    session.skin_url.clone(),
                    session.refresh_token.as_deref(),
                )?;
                Ok(SignInStatus::Complete { account })
            }
            Err(e) => {
                // A terminated sign-in cannot be polled again. Clearing it
                // means the next poll says so plainly instead of replaying
                // the same failure forever.
                *self.pending.lock().unwrap() = None;
                Err(e)
            }
        }
    }

    /// Abandon a sign-in in progress.
    pub fn cancel_sign_in(&self) {
        *self.pending.lock().unwrap() = None;
    }

    // ---- accounts ---------------------------------------------------------

    pub fn accounts(&self) -> Accounts {
        account::load(&self.config.data_root)
    }

    pub fn select_account(&self, profile_id: &str) -> Result<Accounts, AshError> {
        account::select(&self.config.data_root, profile_id)
    }

    /// Forget an account and erase its stored refresh token.
    pub fn remove_account(&self, profile_id: &str) -> Result<Accounts, AshError> {
        account::remove(&self.config.data_root, self.credentials.as_ref(), profile_id)
    }

    /// Exchange the stored refresh token for a usable Minecraft session.
    ///
    /// Called before a launch. A refresh token Microsoft rejects is dead, and
    /// surfaces as [`AshError::SessionExpired`] so the UI can ask for a fresh
    /// sign-in rather than failing the launch with something cryptic.
    pub async fn ensure_session(&self, profile_id: &str) -> Result<Account, AshError> {
        let token = account::refresh_token(self.credentials.as_ref(), profile_id)?;
        let session = auth::refresh(self.http.as_ref(), &self.client_id, &token).await?;
        account::upsert(
            &self.config.data_root,
            self.credentials.as_ref(),
            &session.profile_id,
            &session.username,
            session.skin_url.clone(),
            session.refresh_token.as_deref(),
        )
    }

    // ---- instances --------------------------------------------------------
    //
    // These touch only the filesystem, so they are honestly synchronous
    // rather than async for symmetry. The adapter puts them on Tauri's async
    // runtime so a large directory walk cannot stall the window.

    /// Create an instance for a version target and a loader, with its own
    /// game directory.
    ///
    /// Both are arguments rather than one of them defaulted: an instance is
    /// a version target paired with exactly one loader, and a creation call
    /// that could leave the loader unsaid is how "vanilla" quietly becomes
    /// the absence of a choice instead of one of them. Neither can be
    /// changed afterwards.
    pub fn create_instance(
        &self,
        name: &str,
        version_id: &str,
        loader: Loader,
    ) -> Result<Instance, AshError> {
        instance::create(&self.config.instances_root, name, version_id, loader)
    }

    /// Every instance, most recently played first.
    pub fn instances(&self) -> Result<Vec<Instance>, AshError> {
        instance::list(&self.config.instances_root)
    }

    pub fn instance(&self, id: &InstanceId) -> Result<Instance, AshError> {
        instance::get(&self.config.instances_root, id)
    }

    /// Change the label. The id and every path stay put.
    pub fn rename_instance(&self, id: &InstanceId, name: &str) -> Result<Instance, AshError> {
        instance::rename(&self.config.instances_root, id, name)
    }

    pub fn mark_played(&self, id: &InstanceId) -> Result<Instance, AshError> {
        instance::mark_played(&self.config.instances_root, id)
    }

    /// What deleting this instance would destroy, worlds listed by name.
    pub fn preview_deletion(&self, id: &InstanceId) -> Result<DeletionPreview, AshError> {
        instance::preview_deletion(&self.config.instances_root, id)
    }

    /// Delete an instance and everything inside it. Never touches the depot.
    pub fn delete_instance(&self, id: &InstanceId) -> Result<(), AshError> {
        // The overrides live under the data root, so removing the instance
        // directory does not remove them. An orphan would be inherited by
        // the next instance that happened to take the same id.
        overrides::forget(&self.config.data_root, id);
        instance::delete(&self.config.instances_root, id)
    }

    /// The instance's game directory, for revealing in the file manager.
    pub fn game_directory(&self, id: &InstanceId) -> PathBuf {
        instance::game_dir(&self.config.instances_root, id)
    }

    // ---- preparation ------------------------------------------------------

    /// Work out what an instance still needs, without downloading anything.
    pub async fn plan_instance(&self, id: &InstanceId) -> Result<Plan, AshError> {
        let source = self.version_source(id).await?;
        depot::plan(self.http.as_ref(), &self.config.depot_root, &source, Os::current()).await
    }

    /// Download everything the instance needs into the depot, verified.
    ///
    /// Progress arrives on `sink` as typed events. `cancel` is checked
    /// between files, so cancelling is prompt without abandoning a file
    /// half-written.
    pub async fn prepare_instance<S: ProgressSink + ?Sized>(
        &self,
        id: &InstanceId,
        sink: &S,
        cancel: &Cancel,
    ) -> Result<Plan, AshError> {
        Ok(self.prepare_all(id, sink, cancel).await?.0)
    }

    /// Preparation, keeping the runtime it provisioned.
    ///
    /// Launching needs the java executable's path, and re-deriving it would
    /// mean reading Mojang's runtime manifest a second time to learn
    /// something this call already knew.
    async fn prepare_all<S: ProgressSink + ?Sized>(
        &self,
        id: &InstanceId,
        sink: &S,
        cancel: &Cancel,
    ) -> Result<(Plan, Runtime), AshError> {
        let source = self.version_source(id).await?;
        let plan = depot::prepare(
            self.http.as_ref(),
            &self.config.depot_root,
            &source,
            Os::current(),
            sink,
            cancel,
        )
        .await?;

        // An instance with every game file and no JRE is not prepared. The
        // runtime is part of what it takes to launch, so it is part of this -
        // and `Done` only fires once both are in place.
        let runtime = self.provision_runtime(plan.java_component.as_deref(), sink, cancel).await?;

        sink.emit(PrepareEvent::Done { version_id: plan.version_id.clone() });
        self.diagnostics.info(
            "prepared",
            &format!(
                "instance={id} version={} files={} fetched={} runtime={}",
                plan.version_id, plan.total_files, plan.missing_files, runtime.component
            ),
        );
        Ok((plan, runtime))
    }

    /// Download and lay out the Java runtime a version target needs.
    ///
    /// Nothing here consults `JAVA_HOME` or `PATH`. The runtime a version
    /// wants is named in its own metadata, and the one on the player's
    /// machine is almost certainly a different major version.
    pub async fn ensure_runtime<S: ProgressSink + ?Sized>(
        &self,
        id: &InstanceId,
        sink: &S,
        cancel: &Cancel,
    ) -> Result<Runtime, AshError> {
        let plan = self.plan_instance(id).await?;
        self.provision_runtime(plan.java_component.as_deref(), sink, cancel).await
    }

    async fn provision_runtime<S: ProgressSink + ?Sized>(
        &self,
        java_component: Option<&str>,
        sink: &S,
        cancel: &Cancel,
    ) -> Result<Runtime, AshError> {
        let component = runtime::component_for(java_component);
        runtime::provision(
            self.http.as_ref(),
            &self.config.depot_root,
            &component,
            Os::current(),
            sink,
            cancel,
        )
        .await
    }

    // ---- machine-local settings -------------------------------------------

    /// This machine's settings for an instance.
    ///
    /// Never part of [`Instance`]. These are the values Phase 4 sync must
    /// not carry, and they are stored outside `instances/` so that it
    /// cannot: see [`crate::MachineOverrides`].
    pub fn overrides(&self, id: &InstanceId) -> Result<MachineOverrides, AshError> {
        // Resolved through the instance, so asking about one that does not
        // exist is an error rather than a silent set of defaults.
        self.instance(id)?;
        Ok(overrides::load(&self.config.data_root, id))
    }

    /// Set this machine's settings for an instance.
    ///
    /// Validated here rather than at launch, so a figure the JVM would
    /// refuse is refused while the player is still looking at the field.
    pub fn set_overrides(
        &self,
        id: &InstanceId,
        settings: MachineOverrides,
    ) -> Result<MachineOverrides, AshError> {
        self.instance(id)?;
        settings.validate()?;
        overrides::save(&self.config.data_root, id, &settings)?;
        Ok(settings)
    }

    // ---- launching --------------------------------------------------------

    /// Exactly what ash would run, without running it.
    ///
    /// Preparing is part of this: every path on the command line names a
    /// file that has to exist, and a preview of a classpath pointing at
    /// nothing would be a preview of a launch that fails. On an already
    /// prepared instance it downloads nothing.
    ///
    /// The access token is redacted, and [`InvocationView`] is the only
    /// shape of this that can leave ash-core.
    pub async fn preview_launch<S: ProgressSink + ?Sized>(
        &self,
        id: &InstanceId,
        sink: &S,
        cancel: &Cancel,
    ) -> Result<InvocationView, AshError> {
        Ok(self.assemble(id, sink, cancel).await?.view())
    }

    /// Start the game, and return once it is running.
    ///
    /// Does not wait for the game to exit - ash stays usable while it runs,
    /// and the player can watch [`Ash::game_status`] or close the launcher.
    pub async fn launch<S: ProgressSink + ?Sized>(
        &self,
        id: &InstanceId,
        sink: &S,
        cancel: &Cancel,
    ) -> Result<InvocationView, AshError> {
        if matches!(self.game_status(id), Some(GameStatus::Running)) {
            return Err(AshError::AlreadyRunning { id: id.as_str().to_owned() });
        }

        let invocation = self.assemble(id, sink, cancel).await?;
        let view = invocation.view();

        // Logged from the view, which has no serialiser for the access token
        // and no field holding one. Writing the raw invocation here would be
        // the single easiest way to put a credential on disk.
        self.diagnostics.info(
            "launch",
            &format!(
                "instance={} version={}
  {}",
                id,
                self.instance(id).map(|i| i.version_id).unwrap_or_default(),
                diagnostics::describe(&view)
            ),
        );

        let process = match self.process.spawn(&invocation) {
            Ok(process) => process,
            Err(e) => {
                self.diagnostics.warn("launch-failed", &format!("instance={id} {}", e.kind()));
                return Err(e);
            }
        };
        self.games.lock().unwrap().insert(id.as_str().to_owned(), process);

        // Recorded now rather than on exit: a session that ends in a crash
        // still happened, and the player looking for "what did I play last"
        // means the same thing either way.
        self.mark_played(id)?;

        Ok(view)
    }

    /// How a launched game is doing. `None` if ash never started it.
    pub fn game_status(&self, id: &InstanceId) -> Option<GameStatus> {
        self.games.lock().unwrap().get(id.as_str()).map(|game| game.status())
    }

    /// The tail of the game's own output.
    ///
    /// Available while it runs and after it exits, which is the only time it
    /// is worth reading.
    pub fn game_log(&self, id: &InstanceId) -> Vec<String> {
        let raw =
            self.games.lock().unwrap().get(id.as_str()).map(|game| game.log()).unwrap_or_default();
        // Mojang's log4j configuration makes the game write XML to stdout,
        // and ash applies that configuration because for old versions it is
        // the Log4Shell mitigation. Rendering it back is the price.
        gamelog::readable(&raw)
    }

    /// Ask a running game to stop.
    pub fn stop_game(&self, id: &InstanceId) {
        if let Some(game) = self.games.lock().unwrap().get(id.as_str()) {
            game.stop();
        }
    }

    /// Prepare whatever is missing, then build the command line.
    async fn assemble<S: ProgressSink + ?Sized>(
        &self,
        id: &InstanceId,
        sink: &S,
        cancel: &Cancel,
    ) -> Result<Invocation, AshError> {
        // The session is settled first, before any download and long before
        // anything spawns. A signed-out player should be told in a moment,
        // not after several minutes of preparation.
        let accounts = self.accounts();
        let account = accounts.active_account().ok_or(AshError::NoAccountSelected)?;
        let stored = account::refresh_token(self.credentials.as_ref(), &account.profile_id)?;
        let session = auth::refresh(self.http.as_ref(), &self.client_id, &stored).await?;

        let (plan, runtime) = self.prepare_all(id, sink, cancel).await?;
        let metadata = depot::read_metadata(&self.config.depot_root, &plan.version_id)?;

        launch::assemble(&launch::LaunchContext {
            metadata: &metadata,
            runtime: &runtime,
            depot_root: &self.config.depot_root,
            game_directory: self.game_directory(id),
            username: &session.username,
            profile_id: &session.profile_id,
            access_token: &session.minecraft_token,
            xuid: &session.xuid,
            client_id: &self.client_id,
            os: Os::current(),
            overrides: &overrides::load(&self.config.data_root, id),
        })
    }

    /// The version id an instance runs, where its metadata lives, and that
    /// metadata's published hash.
    ///
    /// Served from the cached catalogue, so preparing an already-known
    /// version does not require Mojang to be reachable to get started.
    async fn version_source(&self, id: &InstanceId) -> Result<depot::VersionSource, AshError> {
        let instance = self.instance(id)?;
        let catalogue = self.catalogue().await?;
        let entry = catalogue
            .entry(&instance.version_id)
            .ok_or_else(|| AshError::UnknownVersion { version_id: instance.version_id.clone() })?;
        Ok(depot::VersionSource {
            id: entry.id.clone(),
            url: entry.url.clone(),
            sha1: entry.sha1.clone(),
        })
    }
}
