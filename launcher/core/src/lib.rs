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
mod glance;
mod instance;
mod launch;
mod load_report;
mod loader;
mod natives;
mod overrides;
mod preferences;
mod profile;
mod runtime;
mod server_list;
mod version;

pub mod credentials;
pub mod http;
pub mod process;
pub mod servers;

pub use account::{Account, Accounts};
pub use catalogue::{
    Catalogue, CatalogueEntry, CatalogueSource, VersionKind, VERSION_MANIFEST_URL,
};
pub use config::Config;
pub use depot::{Artifact, Cancel, NullSink, Plan, PrepareEvent, ProgressSink};
pub use diagnostics::Diagnostics;
pub use error::AshError;
pub use glance::{AshFeatures, InstanceGlance, LastSession};
pub use instance::{DeletionPreview, Instance, InstanceId, Session};
pub use load_report::DegradationNotice;
pub use loader::{Loader, LoaderPin, PinnedFile, PinnedLibrary, PinnedNative};
pub use overrides::{MachineDefaults, MachineOverrides, Resolution, DEFAULT_MEMORY_MB};
pub use preferences::{LauncherPreferences, OnGameStart};
pub use process::{GameProcess, GameStatus, Invocation, InvocationView, ProcessPort};
pub use runtime::Runtime;
pub use server_list::{Handshake, ServerEntry, ServerStatus};
pub use version::Os;

use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::Serialize;

use crate::credentials::CredentialStore;
use crate::http::HttpPort;
use crate::servers::ServerPort;

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

/// How long a server's answer stands before it is asked again.
const STATUS_INTERVAL_MS: u64 = 60_000;

/// How a launch goes: straight into a server or not, and whether the
/// player's own mods are left out this once.
#[derive(Debug, Clone, Copy, Default)]
struct How<'a> {
    join: Option<&'a str>,
    without_third_party_mods: bool,
}

/// A game ash started, and the session it is.
struct Game {
    process: Box<dyn GameProcess>,
    /// Which session in the instance's metadata this game is.
    started_ms: u64,
    /// Whether that session has been ended, so it is ended once.
    end_recorded: bool,
}

/// The whole of ash's public API.
pub struct Ash {
    config: Config,
    http: Arc<dyn HttpPort>,
    credentials: Arc<dyn CredentialStore>,
    process: Arc<dyn ProcessPort>,
    servers: Arc<dyn ServerPort>,
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
    games: Mutex<HashMap<String, Game>>,
    /// The last load-report line written to ash's log, by instance, so the
    /// same report read again - by the notice, then by a launch - is logged
    /// once, while a new session's report is logged even if it says the same.
    reports_logged: Mutex<HashMap<String, String>>,
    /// Each server's last answer and when it came, by protocol and address,
    /// so no server is asked more than once a minute however often the
    /// window asks.
    statuses: Mutex<HashMap<(i32, String), (u64, ServerStatus)>>,
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
        servers: Arc<dyn ServerPort>,
        client_id: impl Into<String>,
    ) -> Self {
        Self {
            diagnostics: Diagnostics::new(&config.data_root),
            config,
            http,
            credentials,
            process,
            servers,
            client_id: client_id.into(),
            pending: Mutex::new(None),
            games: Mutex::new(HashMap::new()),
            reports_logged: Mutex::new(HashMap::new()),
            statuses: Mutex::new(HashMap::new()),
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
        // Refused here rather than at the first launch. ash pins the loaders
        // it has tested per version target, and an instance for a pairing it
        // has no pin for is one that could never start - which is a thing to
        // say while the player is still choosing, not after they have named
        // it and put worlds in it.
        if !loader::is_supported(self.config.loaders, loader, version_id) {
            return Err(AshError::LoaderUnavailable { loader, version_id: version_id.to_owned() });
        }
        instance::create(&self.config.instances_root, name, version_id, loader)
    }

    /// The loaders a version target can run, for a UI that has to offer them.
    ///
    /// Served rather than duplicated in the UI: a hardcoded copy would drift
    /// from the pins, and the first symptom would be a player creating an
    /// instance that can never launch.
    pub fn loaders_for(&self, version_id: &str) -> Vec<Loader> {
        loader::loaders_for(self.config.loaders, version_id)
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
        let (source, pin) = self.preparation_inputs(id).await?;
        depot::plan(self.http.as_ref(), &self.config.depot_root, &source, pin, Os::current()).await
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
        let (source, pin) = self.preparation_inputs(id).await?;
        let plan = depot::prepare(
            self.http.as_ref(),
            &self.config.depot_root,
            &source,
            pin,
            Os::current(),
            sink,
            cancel,
        )
        .await?;

        // ash's own jars reach the loader by path, from the depot and the
        // installation, so the mods folder is the player's alone. What Phase
        // 2 copied into it comes out, by exact name and pinned hash, and
        // nothing else does.
        if let Some(pin) = pin {
            let removed =
                instance::remove_left_behind(&self.config.instances_root, id, &left_behind(pin)?)?;
            if !removed.is_empty() {
                self.diagnostics.info(
                    "mods-folder-migrated",
                    &format!("instance={id} removed={}", removed.join(",")),
                );
            }

            // Checked here as well as at launch, so Download only says so
            // too. Last, so a damaged installation is not reported before
            // the things that can be re-downloaded have been.
            self.ash_jars(pin)?;
        }

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

    /// Which of ash's features did not load in this instance's last session,
    /// as the client reported it. `None` when every one loaded - or when the
    /// client has not run yet to say, or did not say in the last session.
    pub fn degradation_notice(
        &self,
        id: &InstanceId,
    ) -> Result<Option<DegradationNotice>, AshError> {
        let instance = self.instance(id)?;
        Ok(self.load_report(&instance).and_then(|report| report.notice()))
    }

    /// The client's report on this instance's last session, or `None`.
    ///
    /// Each report read goes into ash's own log once, so it arrives in
    /// anything a player sends in - whether they read the notice straight
    /// after a session or launch again first.
    ///
    /// A report written before the last launch is not about that session: its
    /// client never wrote one, because it crashed before it could or could not
    /// write. Its verdict belongs to an older session, and passing it off as
    /// the last one's would say "all loaded" about a session nobody reported
    /// on. So it is logged as missing and not returned.
    fn load_report(&self, instance: &Instance) -> Option<load_report::LoadReport> {
        let id = &instance.id;
        let (event, line, report) = match load_report::read(&self.game_directory(id)) {
            Ok(None) => return None,
            Err(unreadable) => {
                ("load-report-unreadable", format!("instance={id} {unreadable}"), None)
            }
            Ok(Some(found)) => {
                let stale = matches!(
                    (found.written_ms, instance.last_played_ms),
                    (Some(written), Some(played)) if written < played
                );
                if stale {
                    let line = format!(
                        "instance={id} the client did not report on the last session; the report \
                         on disk is older than it"
                    );
                    ("load-report-stale", line, None)
                } else {
                    let line = format!(
                        "instance={id} written_ms={} {}",
                        found.written_ms.unwrap_or(0),
                        found.report.describe()
                    );
                    let event = "load-report";
                    (event, line, Some(found.report))
                }
            }
        };

        let mut logged = self.reports_logged.lock().unwrap();
        if logged.get(id.as_str()) != Some(&line) {
            let degraded = report.as_ref().is_some_and(|r| r.any_degraded());
            if event == "load-report" && !degraded {
                self.diagnostics.info(event, &line);
            } else {
                self.diagnostics.warn(event, &line);
            }
            logged.insert(id.as_str().to_owned(), line);
        }
        report
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

    // ---- launcher preferences ---------------------------------------------

    /// The launcher's own settings. Defaults when there is no file, or one
    /// that cannot be read: a preference never stops the launcher opening.
    pub fn launcher_preferences(&self) -> LauncherPreferences {
        preferences::load(&self.config.data_root)
    }

    pub fn set_launcher_preferences(
        &self,
        preferences: LauncherPreferences,
    ) -> Result<LauncherPreferences, AshError> {
        preferences::save(&self.config.data_root, &preferences)?;
        Ok(preferences)
    }

    /// This machine's defaults for every instance. Like the overrides, never
    /// among the preferences, which sync.
    pub fn machine_defaults(&self) -> MachineDefaults {
        overrides::load_defaults(&self.config.data_root)
    }

    /// Validated as an instance's own memory is, so a figure the JVM would
    /// refuse is refused while the player is looking at the field.
    pub fn set_machine_defaults(
        &self,
        defaults: MachineDefaults,
    ) -> Result<MachineDefaults, AshError> {
        defaults.validate()?;
        overrides::save_defaults(&self.config.data_root, &defaults)?;
        Ok(defaults)
    }

    /// The memory an instance with none of its own is given on this machine:
    /// what an instance page's Automatic stands for.
    pub fn default_memory_mb(&self) -> u32 {
        self.machine_defaults().memory_mb_or_default()
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
        Ok(self.assemble(id, How::default(), sink, cancel).await?.view())
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
        self.start(id, How::default(), sink, cancel).await
    }

    /// Start the game straight into one of the instance's servers.
    ///
    /// Only a server in the instance's own list, the one the game shows. The
    /// server is this launch's alone and is not stored anywhere.
    pub async fn join<S: ProgressSink + ?Sized>(
        &self,
        id: &InstanceId,
        address: &str,
        sink: &S,
        cancel: &Cancel,
    ) -> Result<InvocationView, AshError> {
        let address = self.listed(id, address)?;
        self.start(id, How { join: Some(&address), ..How::default() }, sink, cancel).await
    }

    /// Start the game with the player's own mods left out, this once.
    ///
    /// What a crash with them on offers, so that "one of your mods may have
    /// done this" comes with a way to find out. The setting itself is not
    /// touched: the player's choice stays theirs.
    pub async fn launch_without_third_party_mods<S: ProgressSink + ?Sized>(
        &self,
        id: &InstanceId,
        sink: &S,
        cancel: &Cancel,
    ) -> Result<InvocationView, AshError> {
        self.start(id, How { without_third_party_mods: true, ..How::default() }, sink, cancel).await
    }

    /// The instance's `mods` folder, the player's, for opening in the file
    /// manager. Made if something removed it, so there is always a folder
    /// to open.
    pub fn mods_directory(&self, id: &InstanceId) -> Result<PathBuf, AshError> {
        self.instance(id)?;
        let mods = self.game_directory(id).join("mods");
        std::fs::create_dir_all(&mods).map_err(AshError::writing("creating the mods folder"))?;
        Ok(mods)
    }

    async fn start<S: ProgressSink + ?Sized>(
        &self,
        id: &InstanceId,
        how: How<'_>,
        sink: &S,
        cancel: &Cancel,
    ) -> Result<InvocationView, AshError> {
        if matches!(self.game_status(id), Some(GameStatus::Running)) {
            return Err(AshError::AlreadyRunning { id: id.as_str().to_owned() });
        }

        let invocation = self.assemble(id, how, sink, cancel).await?;
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

        // What the client said about the last session, into ash's own log if
        // it is not there already - read before the game this launch starts
        // replaces it.
        if let Ok(instance) = self.instance(id) {
            let _ = self.load_report(&instance);
        }

        let process = match self.process.spawn(&invocation) {
            Ok(process) => process,
            Err(e) => {
                self.diagnostics.warn("launch-failed", &format!("instance={id} {}", e.kind()));
                return Err(e);
            }
        };
        let started_ms = now_ms();
        self.games
            .lock()
            .unwrap()
            .insert(id.as_str().to_owned(), Game { process, started_ms, end_recorded: false });

        // The session starts now rather than when it ends: a session that
        // ends in a crash still happened, and the player looking for "what
        // did I play last" means the same thing either way.
        instance::begin_session(&self.config.instances_root, id, started_ms)?;

        Ok(view)
    }

    /// How a launched game is doing. `None` if ash never started it.
    ///
    /// The first time it answers that the game has exited, the session ends
    /// in the instance's metadata - which is why the window asks until then.
    pub fn game_status(&self, id: &InstanceId) -> Option<GameStatus> {
        let mut games = self.games.lock().unwrap();
        let game = games.get_mut(id.as_str())?;
        let status = game.process.status();
        if matches!(status, GameStatus::Exited { .. }) && !game.end_recorded {
            match instance::end_session(&self.config.instances_root, id, game.started_ms, now_ms())
            {
                Ok(()) => game.end_recorded = true,
                // Tried again on the next ask. Losing the time is worse
                // than a line in the log for each attempt.
                Err(e) => {
                    self.diagnostics.warn("session-end", &format!("instance={id} {}", e.kind()))
                }
            }
        }
        Some(status)
    }

    /// What the Play page shows of an instance beside LAUNCH GAME.
    pub fn instance_glance(&self, id: &InstanceId) -> Result<InstanceGlance, AshError> {
        // Asked first, so a game that has exited since the window last
        // looked has its session ended before it is read.
        let running = matches!(self.game_status(id), Some(GameStatus::Running));
        let instance = self.instance(id)?;
        let game = self.game_directory(id);

        let last_session = instance.last_session.map(|session| LastSession {
            started_ms: session.started_ms,
            ended_ms: match session.ended_ms {
                Some(ended) => Some(ended),
                None if running => None,
                // Open, with no game of ash's running for it: the launcher
                // was closed while it ran. Shown as it will be closed.
                None => {
                    Some(instance::unseen_end(&self.config.instances_root, id, session.started_ms))
                }
            },
        });
        let open_ms = match instance.last_session {
            Some(instance::Session { ended_ms: None, started_ms }) => {
                let ended = last_session.and_then(|s| s.ended_ms).unwrap_or_else(now_ms);
                ended.saturating_sub(started_ms)
            }
            _ => 0,
        };

        let pin = loader::pin_for(self.config.loaders, instance.loader, &instance.version_id);
        Ok(InstanceGlance {
            features: if instance.loader == Loader::Vanilla {
                AshFeatures::NoClient
            } else {
                glance::features(&game)
            },
            played_ms: instance.played_ms + open_ms,
            last_session,
            // Listed only when they load: with them off, the loader reads an
            // empty folder of ash's and nothing in this one runs.
            mods: glance::mods(
                &game,
                pin,
                overrides::load(&self.config.data_root, id).third_party_mods,
            ),
        })
    }

    /// The tail of the game's own output.
    ///
    /// Available while it runs and after it exits, which is the only time it
    /// is worth reading.
    pub fn game_log(&self, id: &InstanceId) -> Vec<String> {
        let raw = self
            .games
            .lock()
            .unwrap()
            .get(id.as_str())
            .map(|game| game.process.log())
            .unwrap_or_default();
        // Mojang's log4j configuration makes the game write XML to stdout,
        // and ash applies that configuration because for old versions it is
        // the Log4Shell mitigation. Rendering it back is the price.
        gamelog::readable(&raw)
    }

    /// Ask a running game to stop.
    pub fn stop_game(&self, id: &InstanceId) {
        if let Some(game) = self.games.lock().unwrap().get(id.as_str()) {
            game.process.stop();
        }
    }

    /// Prepare whatever is missing, then build the command line.
    async fn assemble<S: ProgressSink + ?Sized>(
        &self,
        id: &InstanceId,
        how: How<'_>,
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

        let join = match how.join {
            None => None,
            Some(address) if launch::takes_quick_play(&metadata) => {
                Some(launch::Join::QuickPlay(address.to_owned()))
            }
            // A version that connects to exactly what it is given does no
            // SRV lookup of its own, so a server reached through one would be
            // unreachable unless ash looks it up first.
            Some(address) => {
                let (host, port) = self.destination(address).await?;
                Some(launch::Join::Direct { host, port })
            }
        };

        // A modded instance's loader is told where every mod is: ash's own
        // by path, and the player's in the instance's `mods` folder when they
        // have turned them on - otherwise in one of ash's with nothing in it.
        // A path the loader cannot find is only a warning to it, so each of
        // ash's is checked here, before a game with half of ash in it starts.
        let instance = self.instance(id)?;
        let overrides = overrides::load(&self.config.data_root, id);
        let players_mods = overrides.third_party_mods && !how.without_third_party_mods;
        let mods = match loader::pin_for(self.config.loaders, instance.loader, &instance.version_id)
        {
            None => None,
            Some(pin) => Some(launch::Mods {
                add: self.ash_jars(pin)?,
                folder: if players_mods { None } else { Some(self.no_mods_folder()?) },
            }),
        };

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
            overrides: &overrides,
            defaults: &overrides::load_defaults(&self.config.data_root),
            join: join.as_ref(),
            mods: mods.as_ref(),
        })
    }

    // ---- servers ----------------------------------------------------------

    /// The instance's servers, as the game's multiplayer screen lists them.
    ///
    /// A list the game wrote but ash cannot read shows as no servers, as it
    /// does in the game, and ash's log says where reading stopped.
    pub fn servers(&self, id: &InstanceId) -> Result<Vec<ServerEntry>, AshError> {
        self.instance(id)?;
        match server_list::read(&self.game_directory(id)) {
            Ok(servers) => Ok(servers),
            Err(unreadable) => {
                self.diagnostics.warn(
                    "servers-unreadable",
                    &format!("instance={id} stopped at byte {}", unreadable.at),
                );
                Ok(Vec::new())
            }
        }
    }

    /// Ask one of the instance's servers how it is, as the game's
    /// multiplayer screen does: in the instance's own protocol, through an
    /// SRV redirect if the address has one, and giving up after
    /// [`Config::server_timeout`].
    ///
    /// At most once a minute per server. Asked again sooner, it answers with
    /// what the server last said - a ping tells the server the player's
    /// address, and the window asking more often is no reason to tell it
    /// more often.
    pub async fn server_status(
        &self,
        id: &InstanceId,
        address: &str,
    ) -> Result<ServerStatus, AshError> {
        let address = self.listed(id, address)?;
        let protocol = server_list::protocol_for(&self.instance(id)?.version_id);
        let key = (protocol, address.clone());

        if let Some((asked_ms, status)) = self.statuses.lock().unwrap().get(&key) {
            if now_ms().saturating_sub(*asked_ms) < STATUS_INTERVAL_MS {
                return Ok(status.clone());
            }
        }

        let ask = async {
            let (host, port) = self.destination(&address).await.ok()?;
            let mut stream = self.servers.connect(&host, port).await.ok()?;
            server_list::exchange(&mut *stream, protocol, &host, port).await.ok()
        };
        let status = tokio::time::timeout(self.config.server_timeout, ask)
            .await
            .ok()
            .flatten()
            .unwrap_or(ServerStatus::Offline);

        self.statuses.lock().unwrap().insert(key, (now_ms(), status.clone()));
        Ok(status)
    }

    /// `address` as it appears in the instance's list, or why it cannot be
    /// used. Nothing reaches a server the player has not listed.
    fn listed(&self, id: &InstanceId, address: &str) -> Result<String, AshError> {
        let listed = self.servers(id)?.into_iter().any(|server| server.address == address);
        if !listed {
            return Err(AshError::ServerNotListed { address: address.to_owned() });
        }
        if server_list::Address::parse(address).is_none() {
            return Err(AshError::InvalidServerAddress { address: address.to_owned() });
        }
        Ok(address.to_owned())
    }

    /// The host and port to connect to: the SRV record's target for an
    /// address with no port, where there is one, as the game resolves it.
    async fn destination(&self, address: &str) -> Result<(String, u16), AshError> {
        let parsed = server_list::Address::parse(address)
            .ok_or_else(|| AshError::InvalidServerAddress { address: address.to_owned() })?;
        if !parsed.port_given {
            // Bounded on its own, for a join: a DNS server that never answers
            // must not hold a launch.
            let lookup = self.servers.srv(&parsed.host);
            if let Ok(Some(redirect)) =
                tokio::time::timeout(self.config.server_timeout, lookup).await
            {
                return Ok(redirect);
            }
        }
        Ok((parsed.host, parsed.port))
    }

    // ---- ash's own jars ---------------------------------------------------

    /// Every jar of ash's a modded instance loads, as the paths the loader
    /// is given: the bundled mods in the depot, then ash's client in the
    /// installation. Each must exist, and a missing one is named.
    fn ash_jars(&self, pin: &LoaderPin) -> Result<Vec<PathBuf>, AshError> {
        let mut jars = Vec::new();
        for bundled in pin.bundled_mods {
            let relative = bundled.depot_path()?;
            let path = self.config.depot_root.join(&relative);
            if !path.is_file() {
                // Verified in the depot moments ago, so something removed it
                // since - almost always antivirus software.
                return Err(AshError::FileVanished { path: relative });
            }
            jars.push(path);
        }
        if let Some(file_name) = pin.client_jar {
            let path = self.config.client_root.join(file_name);
            if !path.is_file() {
                return Err(AshError::ClientMissing { version_id: pin.version_id.to_owned() });
            }
            jars.push(path);
        }
        Ok(jars)
    }

    /// The directory the loader is told is the mods folder while the
    /// player's own mods are off: one of ash's, under the data root, where a
    /// player has no reason to put anything.
    ///
    /// The loader cannot be told to read no folder, only a different one,
    /// so this one has to be empty. A jar found in it is removed first: it is
    /// ash's directory, and a jar in it would load in every instance.
    fn no_mods_folder(&self) -> Result<PathBuf, AshError> {
        let folder = self.config.data_root.join("no-mods");
        std::fs::create_dir_all(&folder)
            .map_err(AshError::writing("creating the empty mods folder"))?;
        for entry in std::fs::read_dir(&folder).into_iter().flatten().flatten() {
            let name = entry.file_name().to_string_lossy().to_lowercase();
            if name.ends_with(".jar") {
                self.diagnostics
                    .warn("no-mods-folder", "removed a jar from ash's empty mods folder");
                std::fs::remove_file(entry.path())
                    .map_err(AshError::writing("emptying the empty mods folder"))?;
            }
        }
        Ok(folder)
    }

    /// The version id an instance runs, where its metadata lives, and that
    /// metadata's published hash.
    ///
    /// Served from the cached catalogue, so preparing an already-known
    /// version does not require Mojang to be reachable to get started.
    async fn preparation_inputs(
        &self,
        id: &InstanceId,
    ) -> Result<(depot::VersionSource, Option<&'static LoaderPin>), AshError> {
        let instance = self.instance(id)?;
        let catalogue = self.catalogue().await?;
        let entry = catalogue
            .entry(&instance.version_id)
            .ok_or_else(|| AshError::UnknownVersion { version_id: instance.version_id.clone() })?;

        // Both come from the one instance read. They always travel together
        // and are always derived from the same two fields, so splitting them
        // into two calls would mean reading the instance twice to learn the
        // same thing.
        Ok((
            depot::VersionSource {
                id: entry.id.clone(),
                url: entry.url.clone(),
                sha1: entry.sha1.clone(),
            },
            loader::pin_for(self.config.loaders, instance.loader, &instance.version_id),
        ))
    }
}

/// Where Phase 2 copied a pin's jars into an instance's mods folder, and how
/// to know them: a bundled mod by its name and pinned hash, ash's client by
/// its fixed name alone.
fn left_behind(pin: &LoaderPin) -> Result<Vec<instance::LeftBehind>, AshError> {
    let mut jars = Vec::new();
    for bundled in pin.bundled_mods {
        jars.push(instance::LeftBehind {
            file_name: bundled.file_name()?,
            sha1: Some(bundled.sha1.to_owned()),
        });
    }
    if let Some(file_name) = pin.client_jar {
        jars.push(instance::LeftBehind { file_name: file_name.to_owned(), sha1: None });
    }
    Ok(jars)
}
