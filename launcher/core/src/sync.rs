//! Synced settings (spec 0004, ADR-0022, research 0011): an account's feature
//! settings, launcher preferences, instances and server lists, following the
//! player between computers.
//!
//! One flat map per account on ash's backend, `key -> (value, deleted,
//! updated_at)`, where each key's latest change wins. This machine keeps, per
//! account, what it last agreed with the backend (the ledger). A local value
//! that differs from it is a local change; a remote one that differs from
//! what's here is applied - only while that instance's game is closed.
//!
//! Two rules keep a fresh computer from undoing the account:
//! - a machine's first sync only pulls a key the account already has;
//! - a settings file sync hasn't seen before, such as the one the game
//!   writes on an instance's first run, only adds keys the account lacks.

use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};
use std::time::UNIX_EPOCH;

use serde::{Deserialize, Serialize};

use crate::client_settings;
use crate::error::AshError;
use crate::http::{HttpPort, HttpRequest, HttpResponse};
use crate::instance::{self, Instance, InstanceId};
use crate::loader::Loader;
use crate::preferences::{self, LauncherPreferences, OnGameStart};
use crate::server_list::{self, Change};

/// The client setting that stays on each machine: it suits a screen, as the
/// window size does (ADR-0022, amended).
const MACHINE_LOCAL_SETTINGS: &[&str] = &["panel.size"];

// ---- what the launcher sees -------------------------------------------------

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "state", rename_all = "snake_case")]
pub enum SyncState {
    /// Turned off on this machine.
    Off,
    /// No ash session: sync starts once ash signs in.
    NotSignedIn,
    /// Not run yet since the launcher started.
    Waiting,
    Synced {
        at_ms: u64,
    },
    /// ash's servers couldn't be reached or answered badly. Tried again later.
    Unreachable,
}

/// An instance deleted on another computer that is still here. Nothing is
/// deleted until the player says (spec 0004).
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct DeletedElsewhere {
    pub instance_id: InstanceId,
    pub name: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct SyncStatus {
    pub enabled: bool,
    pub state: SyncState,
    pub deleted_elsewhere: Vec<DeletedElsewhere>,
}

// ---- storage ------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct Entry {
    pub key: String,
    pub value: String,
    pub deleted: bool,
    pub updated_at: u64,
}

impl Entry {
    fn same_as(&self, other: &Entry) -> bool {
        self.deleted == other.deleted && (self.deleted || self.value == other.value)
    }
}

/// What this machine last agreed with the backend, for one account.
#[derive(Debug, Default, Serialize, Deserialize)]
struct Ledger {
    agreed: BTreeMap<String, Entry>,
    /// Remote values not yet applied here because a game was running.
    #[serde(default)]
    unapplied: BTreeSet<String>,
    /// Each modded instance's settings file modification time, as last
    /// seen: a file not modified since has no local change in it.
    #[serde(default)]
    seen: BTreeMap<String, u64>,
    #[serde(default)]
    last_synced_ms: Option<u64>,
}

#[derive(Debug, Serialize, Deserialize)]
struct Switch {
    enabled: bool,
}

fn switch_path(data_root: &Path) -> PathBuf {
    data_root.join("sync.json")
}

fn ledger_path(data_root: &Path, uuid: &str) -> PathBuf {
    data_root.join("sync").join(format!("{uuid}.json"))
}

fn read<T: serde::de::DeserializeOwned>(path: &Path) -> Option<T> {
    serde_json::from_slice(&std::fs::read(path).ok()?).ok()
}

fn write(path: &Path, value: &impl Serialize) -> Result<(), AshError> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(AshError::writing("creating the sync folder"))?;
    }
    let encoded = serde_json::to_vec_pretty(value)
        .map_err(|e| AshError::Storage { detail: format!("encoding sync state: {e}") })?;
    std::fs::write(path, encoded).map_err(AshError::writing("writing sync state"))
}

/// Sync is on unless turned off on this machine.
pub(crate) fn enabled(data_root: &Path) -> bool {
    read::<Switch>(&switch_path(data_root)).is_none_or(|s| s.enabled)
}

pub(crate) fn set_enabled(data_root: &Path, enabled: bool) -> Result<(), AshError> {
    write(&switch_path(data_root), &Switch { enabled })
}

/// Forget what this machine agreed for an account, so its next sync starts
/// afresh - after the account is deleted, say.
pub(crate) fn forget(data_root: &Path, uuid: &str) {
    let _ = std::fs::remove_file(ledger_path(data_root, uuid));
}

// ---- the values ----------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
struct InstanceDefinition {
    name: String,
    version_id: String,
    loader: Loader,
    created_at_ms: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
struct SyncedServer {
    name: String,
    address: String,
}

fn instance_key(sync_id: &str) -> String {
    format!("instance:{sync_id}")
}

fn servers_key(sync_id: &str) -> String {
    format!("servers:{sync_id}")
}

fn on_game_start_text(value: OnGameStart) -> String {
    serde_json::to_value(value).ok().and_then(|v| v.as_str().map(str::to_owned)).unwrap_or_default()
}

fn modified_ms(path: &Path) -> Option<u64> {
    let modified = std::fs::metadata(path).ok()?.modified().ok()?;
    Some(modified.duration_since(UNIX_EPOCH).ok()?.as_millis() as u64)
}

// ---- one sync ---------------------------------------------------------------------

/// What a sync needs from the rest of ash.
pub(crate) struct Context<'a> {
    pub http: &'a dyn HttpPort,
    pub base: &'a str,
    pub token: &'a str,
    pub uuid: &'a str,
    pub data_root: &'a Path,
    pub instances_root: &'a Path,
    /// Whether ash's game for this instance is running right now.
    pub running: &'a (dyn Fn(&InstanceId) -> bool + Sync),
    pub now: u64,
}

#[derive(Serialize, Deserialize)]
struct Settings {
    settings: Vec<Entry>,
}

#[derive(Serialize)]
struct Changes<'a> {
    changes: &'a [Entry],
}

/// The backend's answer, or `None` when the session has ended.
fn settings_from(
    url: &str,
    response: &HttpResponse,
) -> Result<Option<BTreeMap<String, Entry>>, AshError> {
    if response.status == 401 {
        return Ok(None);
    }
    if !response.is_success() {
        return Err(AshError::UnexpectedStatus { url: url.to_owned(), status: response.status });
    }
    let settings: Settings = serde_json::from_slice(&response.body)
        .map_err(|e| AshError::Malformed { url: url.to_owned(), detail: e.to_string() })?;
    Ok(Some(settings.settings.into_iter().map(|e| (e.key.clone(), e)).collect()))
}

async fn get(ctx: &Context<'_>) -> Result<Option<BTreeMap<String, Entry>>, AshError> {
    let url = format!("{}/v1/settings", ctx.base);
    let response = ctx.http.send(HttpRequest::get(&url).bearer(ctx.token)).await?;
    settings_from(&url, &response)
}

async fn put(
    ctx: &Context<'_>,
    changes: &[Entry],
) -> Result<Option<BTreeMap<String, Entry>>, AshError> {
    let url = format!("{}/v1/settings", ctx.base);
    let request = HttpRequest::put_json(&url, &Changes { changes })?.bearer(ctx.token);
    let response = ctx.http.send(request).await?;
    settings_from(&url, &response)
}

/// Give every instance that should sync a sync id: instances made before
/// sync existed have none.
fn ensure_sync_ids(instances_root: &Path) -> Result<Vec<Instance>, AshError> {
    let mut instances = instance::list(instances_root)?;
    for inst in &mut instances {
        if inst.sync_id.is_none() && !inst.local_only {
            *inst = instance::set_sync(
                instances_root,
                &inst.id,
                Some(uuid::Uuid::new_v4().to_string()),
                false,
            )?;
        }
    }
    Ok(instances)
}

/// Everything here, as keys, with the time each local change was made.
fn local_values(
    ctx: &Context<'_>,
    instances: &[Instance],
    ledger: &Ledger,
    known_remotely: &dyn Fn(&str) -> bool,
) -> BTreeMap<String, Entry> {
    let mut local = BTreeMap::new();
    let mut put = |key: String, value: String, at: u64| {
        local.insert(key.clone(), Entry { key, value, deleted: false, updated_at: at });
    };

    let prefs = preferences::load(ctx.data_root);
    put("launcher:launch_sounds".into(), prefs.launch_sounds.to_string(), ctx.now);
    put("launcher:on_game_start".into(), on_game_start_text(prefs.on_game_start), ctx.now);

    for inst in instances.iter().filter(|i| !i.local_only) {
        let Some(sync_id) = &inst.sync_id else { continue };
        let definition = InstanceDefinition {
            name: inst.name.clone(),
            version_id: inst.version_id.clone(),
            loader: inst.loader,
            created_at_ms: inst.created_at_ms,
        };
        put(instance_key(sync_id), serde_json::to_string(&definition).unwrap_or_default(), ctx.now);
        let game = instance::game_dir(ctx.instances_root, &inst.id);
        // A list the game can't read is left out, and so is never changed.
        if let Ok(list) = server_list::read(&game) {
            let servers: Vec<SyncedServer> = list
                .into_iter()
                .map(|s| SyncedServer { name: s.name, address: s.address })
                .collect();
            put(servers_key(sync_id), serde_json::to_string(&servers).unwrap_or_default(), ctx.now);
        }
    }

    // Feature settings: a file modified since it was last seen holds the
    // player's changes; the newest file wins a key, as the newest change.
    let mut features: BTreeMap<String, (String, u64)> = BTreeMap::new();
    for inst in instances.iter().filter(|i| i.loader != Loader::Vanilla) {
        let game = instance::game_dir(ctx.instances_root, &inst.id);
        let Some(text) = client_settings::read_text(&game) else { continue };
        let Some(modified) = modified_ms(&client_settings::path(&game)) else { continue };
        let seen = ledger.seen.get(inst.id.as_str()).copied();
        if seen.is_some_and(|s| modified <= s) {
            continue;
        }
        let values: BTreeMap<String, String> =
            client_settings::properties(&text).into_iter().collect();
        for (property, value) in values {
            if MACHINE_LOCAL_SETTINGS.contains(&property.as_str())
                || !client_settings::writable(&property, &value)
            {
                continue;
            }
            let key = format!("feature:{property}");
            let agreed = ledger.agreed.get(&key);
            // A file never seen before only adds what the account lacks.
            if seen.is_none() && (agreed.is_some() || known_remotely(&key)) {
                continue;
            }
            if agreed.is_some_and(|a| !a.deleted && a.value == value) {
                continue;
            }
            if features.get(&key).is_none_or(|(_, at)| modified > *at) {
                features.insert(key, (value, modified));
            }
        }
    }
    for (key, (value, modified)) in features {
        // Newer than what was agreed, or the backend would keep the agreed.
        let at = ledger.agreed.get(&key).map_or(modified, |a| modified.max(a.updated_at + 1));
        put(key, value, at.min(ctx.now));
    }
    local
}

/// What changed here since the last agreement, ready to send.
fn local_changes(
    instances: &[Instance],
    local: &BTreeMap<String, Entry>,
    ledger: &Ledger,
    known_remotely: &dyn Fn(&str) -> bool,
    bootstrap: bool,
    now: u64,
) -> Vec<Entry> {
    let mut changes = Vec::new();
    for (key, entry) in local {
        if ledger.unapplied.contains(key) {
            continue;
        }
        match ledger.agreed.get(key) {
            Some(agreed) if agreed.same_as(entry) => continue,
            // Deleted elsewhere and still here: a question, not a change.
            Some(agreed)
                if agreed.deleted
                    && !key.starts_with("feature:")
                    && !key.starts_with("launcher:") =>
            {
                continue
            }
            _ => {}
        }
        if bootstrap && known_remotely(key) {
            continue;
        }
        changes.push(entry.clone());
    }
    // An instance synced before and gone from here was deleted here.
    let here: BTreeSet<&str> = instances.iter().filter_map(|i| i.sync_id.as_deref()).collect();
    for (key, agreed) in &ledger.agreed {
        let Some(sync_id) = key.strip_prefix("instance:") else { continue };
        if agreed.deleted || here.contains(sync_id) {
            continue;
        }
        for key in [instance_key(sync_id), servers_key(sync_id)] {
            changes.push(Entry { key, value: String::new(), deleted: true, updated_at: now });
        }
    }
    changes
}

#[derive(PartialEq)]
enum Applied {
    Done,
    /// A game was running; tried again on the next sync.
    Deferred,
}

/// Bring a server list to `target` through ADR-0019's changes, so icons,
/// hidden entries and unknown tags stay exactly as the game wrote them.
fn reconcile_servers(game: &Path, target: &[SyncedServer]) -> Result<(), ()> {
    let mut target: Vec<&SyncedServer> = target.iter().collect();
    let mut addresses = BTreeSet::new();
    target.retain(|s| addresses.insert(s.address.clone()));

    let mut current: Vec<(String, String)> =
        server_list::read(game).map_err(|_| ())?.into_iter().map(|s| (s.name, s.address)).collect();
    let apply = |change: Change| server_list::change(game, change).map_err(|_| ());

    for i in (0..current.len()).rev() {
        if !target.iter().any(|t| t.address == current[i].1) {
            apply(Change::Remove { position: i, expected: current[i].1.clone() })?;
            current.remove(i);
        }
    }
    for t in &target {
        if !current.iter().any(|c| c.1 == t.address) {
            apply(Change::Add { name: t.name.clone(), address: t.address.clone() })?;
            current.push((t.name.clone(), t.address.clone()));
        }
    }
    for (i, t) in target.iter().enumerate() {
        let Some(j) = current.iter().position(|c| c.1 == t.address) else { return Err(()) };
        if j != i {
            apply(Change::Move { position: j, expected: t.address.clone(), to: i })?;
            let moving = current.remove(j);
            current.insert(i, moving);
        }
        if current[i].0 != t.name {
            apply(Change::Edit {
                position: i,
                expected: t.address.clone(),
                name: t.name.clone(),
                address: t.address.clone(),
            })?;
            current[i].0 = t.name.clone();
        }
    }
    Ok(())
}

/// Write the account's feature settings into every modded instance whose
/// game is closed. Returns the keys some running instance still lacks.
fn apply_features(
    ctx: &Context<'_>,
    values: &[(String, String)],
    instances: &[Instance],
) -> Result<BTreeSet<String>, AshError> {
    let mut deferred = BTreeSet::new();
    for inst in instances.iter().filter(|i| i.loader != Loader::Vanilla) {
        let game = instance::game_dir(ctx.instances_root, &inst.id);
        if (ctx.running)(&inst.id) {
            let have: BTreeMap<String, String> = client_settings::read_text(&game)
                .map(|t| client_settings::properties(&t).into_iter().collect())
                .unwrap_or_default();
            for (property, value) in values {
                if have.get(property) != Some(value) {
                    deferred.insert(format!("feature:{property}"));
                }
            }
            continue;
        }
        // An instance whose game has never run gets them too, so the client
        // starts with the account's settings rather than its defaults.
        client_settings::set_values(&game, values)?;
    }
    Ok(deferred)
}

fn apply_entry(
    ctx: &Context<'_>,
    entry: &Entry,
    instances: &mut Vec<Instance>,
) -> Result<Applied, AshError> {
    let key = entry.key.as_str();
    if let Some(name) = key.strip_prefix("launcher:") {
        if entry.deleted {
            return Ok(Applied::Done);
        }
        let mut prefs: LauncherPreferences = preferences::load(ctx.data_root);
        match name {
            "launch_sounds" => prefs.launch_sounds = entry.value == "true",
            "on_game_start" => {
                if let Ok(value) =
                    serde_json::from_value(serde_json::Value::String(entry.value.clone()))
                {
                    prefs.on_game_start = value;
                }
            }
            _ => return Ok(Applied::Done),
        }
        preferences::save(ctx.data_root, &prefs)?;
        return Ok(Applied::Done);
    }
    if let Some(sync_id) = key.strip_prefix("instance:") {
        // A deletion is never applied here: the player is asked.
        if entry.deleted {
            return Ok(Applied::Done);
        }
        let Ok(definition) = serde_json::from_str::<InstanceDefinition>(&entry.value) else {
            return Ok(Applied::Done);
        };
        match instances.iter().position(|i| i.sync_id.as_deref() == Some(sync_id)) {
            Some(i) => {
                if instances[i].name != definition.name {
                    instances[i] =
                        instance::rename(ctx.instances_root, &instances[i].id, &definition.name)?;
                }
            }
            None => {
                let created = instance::create_with(
                    ctx.instances_root,
                    &definition.name,
                    &definition.version_id,
                    definition.loader,
                    sync_id.to_owned(),
                    definition.created_at_ms,
                )?;
                instances.push(created);
            }
        }
        return Ok(Applied::Done);
    }
    if let Some(sync_id) = key.strip_prefix("servers:") {
        if entry.deleted {
            return Ok(Applied::Done);
        }
        let Some(inst) = instances.iter().find(|i| i.sync_id.as_deref() == Some(sync_id)) else {
            return Ok(Applied::Done);
        };
        if (ctx.running)(&inst.id) {
            return Ok(Applied::Deferred);
        }
        let Ok(target) = serde_json::from_str::<Vec<SyncedServer>>(&entry.value) else {
            return Ok(Applied::Done);
        };
        // A list the game can't read is left alone (ADR-0019).
        let _ = reconcile_servers(&instance::game_dir(ctx.instances_root, &inst.id), &target);
        return Ok(Applied::Done);
    }
    Ok(Applied::Done)
}

/// One sync for the account `ctx.uuid`: send what changed here, apply what
/// changed elsewhere. `None` when the backend says the session has ended.
pub(crate) async fn run(ctx: &Context<'_>) -> Result<Option<u64>, AshError> {
    let path = ledger_path(ctx.data_root, ctx.uuid);
    let mut ledger: Ledger = read(&path).unwrap_or_default();
    let mut instances = ensure_sync_ids(ctx.instances_root)?;

    let bootstrap = ledger.last_synced_ms.is_none();
    let remote_before = if bootstrap {
        match get(ctx).await? {
            Some(remote) => remote,
            None => return Ok(None),
        }
    } else {
        BTreeMap::new()
    };
    let known_remotely = |key: &str| remote_before.get(key).is_some_and(|e| !e.deleted);

    let local = local_values(ctx, &instances, &ledger, &known_remotely);
    let changes = local_changes(&instances, &local, &ledger, &known_remotely, bootstrap, ctx.now);
    let Some(remote) = put(ctx, &changes).await? else { return Ok(None) };

    // Apply in key order: feature, instance, launcher, servers - so a new
    // instance exists before its server list arrives.
    let mut unapplied = BTreeSet::new();
    let mut features = Vec::new();
    for (key, entry) in &remote {
        let here = local.get(key);
        if let Some(property) = key.strip_prefix("feature:") {
            if !entry.deleted {
                features.push((property.to_owned(), entry.value.clone()));
            }
            continue;
        }
        if here.is_some_and(|h| h.same_as(entry)) {
            continue;
        }
        if apply_entry(ctx, entry, &mut instances)? == Applied::Deferred {
            unapplied.insert(key.clone());
        }
    }
    unapplied.extend(apply_features(ctx, &features, &instances)?);

    // What each closed modded instance's settings file now looks like.
    for inst in instances.iter().filter(|i| i.loader != Loader::Vanilla && !(ctx.running)(&i.id)) {
        if let Some(modified) =
            modified_ms(&client_settings::path(&instance::game_dir(ctx.instances_root, &inst.id)))
        {
            ledger.seen.insert(inst.id.as_str().to_owned(), modified);
        }
    }
    ledger.agreed = remote;
    ledger.unapplied = unapplied;
    ledger.last_synced_ms = Some(ctx.now);
    write(&path, &ledger)?;
    Ok(Some(ctx.now))
}

/// Instances here that another computer deleted, by what this machine last
/// agreed for the account.
pub(crate) fn deleted_elsewhere(
    data_root: &Path,
    instances_root: &Path,
    uuid: &str,
) -> Vec<DeletedElsewhere> {
    let Some(ledger) = read::<Ledger>(&ledger_path(data_root, uuid)) else { return Vec::new() };
    instance::list(instances_root)
        .unwrap_or_default()
        .into_iter()
        .filter(|i| {
            i.sync_id
                .as_deref()
                .and_then(|id| ledger.agreed.get(&instance_key(id)))
                .is_some_and(|e| e.deleted)
        })
        .map(|i| DeletedElsewhere { instance_id: i.id, name: i.name })
        .collect()
}

/// The account's feature settings as last agreed, for a new instance here to
/// start with rather than the client's defaults.
pub(crate) fn agreed_features(data_root: &Path, uuid: &str) -> Vec<(String, String)> {
    read::<Ledger>(&ledger_path(data_root, uuid))
        .map(|ledger| {
            ledger
                .agreed
                .values()
                .filter(|e| !e.deleted)
                .filter_map(|e| Some((e.key.strip_prefix("feature:")?.to_owned(), e.value.clone())))
                .collect()
        })
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::http::FakeHttp;

    fn modded(root: &Path, name: &str) -> Instance {
        instance::create(root, name, "1.21.11", Loader::Fabric).unwrap()
    }

    #[test]
    fn the_backend_s_own_example_is_read_as_settings() {
        let example = std::fs::read(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/tests/fixtures/backend/settings.json"
        ))
        .unwrap();
        let read = settings_from("x", &HttpResponse::ok(example)).unwrap().expect("signed in");
        let colour = &read["feature:crosshair.colour"];
        assert_eq!((colour.value.as_str(), colour.deleted), ("#FF2FBF71", false));
        let instance = read.keys().find(|k| k.starts_with("instance:")).unwrap();
        let definition: InstanceDefinition = serde_json::from_str(&read[instance].value).unwrap();
        assert_eq!(
            (definition.version_id.as_str(), definition.loader),
            ("1.8.9", Loader::LegacyFabric)
        );
    }

    #[test]
    fn a_running_game_s_files_are_left_alone_until_it_closes() {
        let tmp = tempfile::tempdir().unwrap();
        let (data, instances_root) = (tmp.path().join("data"), tmp.path().join("instances"));
        let playing = modded(&instances_root, "Playing");
        let closed = modded(&instances_root, "Closed");
        let http = FakeHttp::new();
        let running = |id: &InstanceId| *id == playing.id;
        let ctx = Context {
            http: http.as_ref(),
            base: "https://api.ash.test",
            token: "T",
            uuid: "u",
            data_root: &data,
            instances_root: &instances_root,
            running: &running,
            now: 1,
        };
        let mut all = vec![playing.clone(), closed.clone()];

        let deferred =
            apply_features(&ctx, &[("crosshair.size".into(), "6".into())], &all).unwrap();
        let servers = Entry {
            key: servers_key(playing.sync_id.as_deref().unwrap()),
            value: r#"[{"name":"Hypixel","address":"mc.hypixel.net"}]"#.into(),
            deleted: false,
            updated_at: 1,
        };
        let applied = apply_entry(&ctx, &servers, &mut all).unwrap();

        let game = |i: &Instance| instance::game_dir(&instances_root, &i.id);
        assert!(
            client_settings::read_text(&game(&playing)).is_none(),
            "the running game's file untouched"
        );
        assert_eq!(
            client_settings::read_text(&game(&closed)).as_deref(),
            Some("crosshair.size=6\n")
        );
        assert!(deferred.contains("feature:crosshair.size"), "tried again next sync");
        assert!(applied == Applied::Deferred);
        assert!(
            !game(&playing).join("servers.dat").exists(),
            "the running game's server list untouched"
        );
    }
}
