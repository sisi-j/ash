import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { open } from "@tauri-apps/plugin-dialog";

// ---- mirrors of ash-core types --------------------------------------------

export type VersionKind =
  | "release"
  | "snapshot"
  | "old_beta"
  | "old_alpha"
  | "other";

export type CatalogueSource = "network" | "cache";

export type CatalogueEntry = {
  id: string;
  kind: VersionKind;
  released_at: string;
  url: string;
  first_class: boolean;
};

export type Catalogue = {
  source: CatalogueSource;
  fetched_at_ms: number;
  latest_release: string;
  latest_snapshot: string;
  versions: CatalogueEntry[];
};

/** `InstanceId` is a newtype over String, so it crosses the wire as a string. */
export type InstanceId = string;

/**
 * Mirrors `ash_core::Loader`: the mod-loading layer an instance runs under.
 *
 * Vanilla is a loader rather than the absence of one, so every instance has
 * exactly one and nothing here handles "no loader" as its own case.
 *
 * Which of these a given version target can actually run is not decided
 * here - ash pins the loaders it has tested per target and `loadersFor`
 * answers it, so a loader ash cannot install is never offered.
 */
export type Loader = "vanilla" | "fabric" | "legacy_fabric";

/**
 * What to call a loader in front of a player, never a version number.
 *
 * A `Record` on purpose: adding a loader to the union above stops this
 * compiling until it has been given a name, so the picker can never render
 * a blank chip.
 */
export const LOADER_LABELS: Record<Loader, string> = {
  vanilla: "Vanilla",
  fabric: "Fabric",
  legacy_fabric: "Legacy Fabric",
};

/**
 * "ash client" or "vanilla", as an instance's row and LAUNCH GAME call it.
 * Every modded instance ash makes runs the ash client, and a vanilla one
 * does not, so the loader is what tells them apart.
 *
 * Unlike the version-and-loader line this replaced, where "Vanilla" on
 * every row distinguished nothing, these two words are the choice the
 * player is making, so a vanilla row says so (the spec's Instances card).
 */
export function describeKind(instance: Instance): string {
  return instance.loader === "vanilla" ? "vanilla" : "ash client";
}

export type Instance = {
  id: InstanceId;
  name: string;
  version_id: string;
  /** Fixed when the instance was created; nothing can change it afterwards. */
  loader: Loader;
  created_at_ms: number;
  last_played_ms: number | null;
  /** Finished sessions added together. `instanceGlance` adds a running one. */
  played_ms: number;
  last_session: Session | null;
};

/** One run of the game, from ash starting it to its exit. */
export type Session = {
  started_ms: number;
  /** `null` while it runs. */
  ended_ms: number | null;
};

/** A server in an instance's own list, as the game's multiplayer screen shows it. */
export type ServerEntry = {
  name: string;
  /** As the player typed it: `mc.hypixel.net`, `localhost:25570`. */
  address: string;
  /** A base64 PNG, as the game saved it. */
  icon: string | null;
  /** When the player last joined it, from ash's own record; null if not since ash began keeping one. */
  last_joined_ms: number | null;
};

/** Mirrors `ash_core::ServerStatus`, an internally tagged enum. */
export type ServerStatus =
  | {
      state: "online";
      players_online: number;
      players_max: number;
      version: string;
      motd: string;
      /** The icon the server sent, a base64 PNG. */
      icon: string | null;
    }
  | { state: "offline" };

/** Mirrors `ash_core::AshFeatures`, an internally tagged enum. */
export type AshFeatures =
  | { state: "no_client" }
  | { state: "not_reported" }
  | { state: "on"; features: string[] };

/** Mirrors `ash_core::InstanceGlance`: the Play page's "This instance" card. */
export type InstanceGlance = {
  features: AshFeatures;
  /** Every session added together, a running one up to now. */
  played_ms: number;
  last_session: Session | null;
  /** The player's mods, never ash's own. */
  mods: string[];
};

/**
 * One of ash's features did not load in an instance's last session. Read
 * from what the client reported, so it stays until a session where it loads.
 */
export type DegradationNotice = {
  /** The features that did not load, by the name the client gave them. */
  features: string[];
  /** What to show the player: ash's problem, not theirs. */
  message: string;
};

export type DeletionPreview = {
  instance: Instance;
  worlds: string[];
  resource_packs: number;
  screenshots: number;
  total_bytes: number;
};

export type Runtime = {
  component: string;
  version_name: string;
  java_executable: string;
};

export type Plan = {
  version_id: string;
  java_component: string | null;
  total_files: number;
  missing_files: number;
  missing_bytes: number;
};

/** Mirrors `ash_core::PrepareEvent`, an internally tagged enum. */
export type PrepareEvent =
  | { event: "resolving"; version_id: string }
  | {
      event: "planned";
      total_files: number;
      missing_files: number;
      missing_bytes: number;
      already_present: number;
    }
  | {
      event: "downloaded";
      path: string;
      bytes: number;
      done_files: number;
      done_bytes: number;
    }
  | { event: "resuming"; path: string; from_bytes: number }
  | { event: "runtime"; component: string }
  | { event: "reverifying"; path: string }
  | { event: "cancelled" }
  | { event: "done"; version_id: string };

/**
 * Mirrors `ash_core::GameStatus`. `clean` is the whole point: a player whose
 * game crashed should not be shown the same thing as one who quit.
 */
export type GameStatus =
  | { state: "running" }
  | { state: "exited"; code: number | null; clean: boolean };

/**
 * What ash would run. The access token is already `<redacted>` on the Rust
 * side - the unredacted shape has no serializer, so it cannot reach here.
 */
export type InvocationView = {
  program: string;
  args: string[];
  working_directory: string;
};

export type LaunchOutcome = {
  ok: boolean;
  invocation: InvocationView | null;
  error: UiError | null;
};

export type Resolution = { width: number; height: number };

/**
 * Settings that belong to this machine and must never leave it.
 *
 * Deliberately not part of `Instance`: Phase 4 syncs an account's launcher
 * state between machines, and a memory figure from a 32GB desktop is a game
 * that will not start on an 8GB laptop. `null` means "whatever ash would do
 * anyway", which is not the same as a value that happens to match today's
 * default.
 */
export type MachineOverrides = {
  memory_mb: number | null;
  java_executable: string | null;
  resolution: Resolution | null;
  /** Whether the loader reads the instance's own `mods` folder. Off unless the player turns it on. */
  third_party_mods: boolean;
};

/**
 * Mirrors `ash_core::LauncherPreferences`: the launcher's own settings, not
 * any one instance's.
 */
/** Mirrors `ash_core::OnGameStart`. */
export type OnGameStart = "keep_open" | "minimise" | "close";

/**
 * This machine's defaults for every instance. Machine-local, like the
 * overrides, and so never among the preferences, which sync.
 */
export type MachineDefaults = {
  /** `null` means ash's own default. */
  memory_mb: number | null;
};

export type LauncherPreferences = {
  /** A short sound when LAUNCH GAME is clicked, and a quieter one when the game starts. */
  launch_sounds: boolean;
  /** What the launcher does once a launch has started the game; never after a failed one. */
  on_game_start: OnGameStart;
};

export type PrepareOutcome = {
  ok: boolean;
  plan: Plan | null;
  error: UiError | null;
};

export type Account = {
  profile_id: string;
  username: string;
  skin_url: string | null;
  added_at_ms: number;
  /** ash made an ash account for this player and has not yet said so. */
  ash_account_notice: boolean;
  /** The player deleted their ash account on this machine. */
  ash_account_deleted: boolean;
};

/** Mirrors `ash_core::AshAccountStatus`. Shown quietly: none of these stops play. */
export type AshAccountStatus =
  | { state: "signed_in" }
  | { state: "not_signed_in" }
  | { state: "deleted" }
  | { state: "unreachable" }
  | { state: "refused"; kind: string; message: string };

export type AshAccountEvent = { profile_id: string; status: AshAccountStatus };

export type NewsKind = "news" | "patch_notes";

/** Mirrors `ash_core::NewsPost`; times are milliseconds since the epoch. */
export type NewsPost = {
  id: string;
  kind: NewsKind;
  title: string;
  /** The agreed Markdown subset; render with `parseMarkdown`, never as HTML. */
  body: string;
  cover_url: string | null;
  published_at: number;
  updated_at: number;
};

export type News = { posts: NewsPost[]; unread: boolean; offline: boolean };

export type Accounts = {
  accounts: Account[];
  active: string | null;
};

export type PendingSignIn = {
  user_code: string;
  verification_uri: string;
  expires_at_ms: number;
  interval_secs: number;
};

/** Mirrors `ash_core::SignInStatus`, an internally tagged enum. */
export type SignInStatus =
  | { status: "waiting"; interval_secs: number }
  | { status: "complete"; account: Account };

/**
 * `kind` is the stable discriminant; `message` is what to show a player.
 * `retryable` says whether offering a retry makes sense at all - a banned
 * Xbox account will never succeed on a second attempt.
 */
export type UiError = { kind: string; message: string; retryable: boolean };

export function isUiError(value: unknown): value is UiError {
  return (
    typeof value === "object" &&
    value !== null &&
    "kind" in value &&
    "message" in value
  );
}

// ---- commands --------------------------------------------------------------

export const api = {
  catalogue: () => invoke<Catalogue>("catalogue"),
  refreshCatalogue: () => invoke<Catalogue>("refresh_catalogue"),

  beginSignIn: () => invoke<PendingSignIn>("begin_sign_in"),
  pollSignIn: () => invoke<SignInStatus>("poll_sign_in"),
  cancelSignIn: () => invoke<void>("cancel_sign_in"),
  accounts: () => invoke<Accounts>("accounts"),
  selectAccount: (profileId: string) =>
    invoke<Accounts>("select_account", { profileId }),
  removeAccount: (profileId: string) =>
    invoke<Accounts>("remove_account", { profileId }),
  ashAccountStatus: (profileId: string) =>
    invoke<AshAccountStatus>("ash_account_status", { profileId }),
  deleteAshAccount: (profileId: string) => invoke<Accounts>("delete_ash_account", { profileId }),
  dismissAshAccountNotice: (profileId: string) =>
    invoke<Accounts>("dismiss_ash_account_notice", { profileId }),
  openPrivacyStatement: () => invoke<void>("open_privacy_statement"),
  news: () => invoke<News>("news"),
  markNewsSeen: () => invoke<void>("mark_news_seen"),
  openNewsLink: (url: string) => invoke<void>("open_news_link", { url }),

  planInstance: (id: InstanceId) => invoke<Plan>("plan_instance", { id }),
  /** Returns as soon as the work is scheduled; watch the events for outcome. */
  prepareInstance: (id: InstanceId) => invoke<void>("prepare_instance", { id }),
  ensureRuntime: (id: InstanceId) => invoke<Runtime>("ensure_runtime", { id }),
  cancelPreparation: (id: InstanceId) => invoke<void>("cancel_preparation", { id }),

  /** Returns as soon as the work is scheduled; watch the events for outcome. */
  launch: (id: InstanceId) => invoke<void>("launch", { id }),
  /** As `launch`, straight into one of the instance's own servers. */
  join: (id: InstanceId, address: string) => invoke<void>("join", { id, address }),
  /** As `launch`, with the player's own mods left out this once. The setting is not changed. */
  launchWithoutThirdPartyMods: (id: InstanceId) =>
    invoke<void>("launch_without_third_party_mods", { id }),
  /** The instance's servers, the most recently joined first, then the rest in the game's order. */
  servers: (id: InstanceId) => invoke<ServerEntry[]>("servers", { id }),
  /** The servers in the game's own order: what `position` counts in for the changes below. */
  serverList: (id: InstanceId) => invoke<ServerEntry[]>("server_list", { id }),
  /**
   * Changes to the game's own server list. Refused while the instance's game runs, and refused
   * if the server at `position` is no longer `expected` - read the list again then.
   */
  addServer: (id: InstanceId, name: string, address: string) =>
    invoke<void>("add_server", { id, name, address }),
  editServer: (id: InstanceId, position: number, expected: string, name: string, address: string) =>
    invoke<void>("edit_server", { id, position, expected, name, address }),
  removeServer: (id: InstanceId, position: number, expected: string) =>
    invoke<void>("remove_server", { id, position, expected }),
  moveServer: (id: InstanceId, position: number, expected: string, to: number) =>
    invoke<void>("move_server", { id, position, expected, to }),
  /** Asks the server at most once a minute; sooner, it answers with the last reply. */
  serverStatus: (id: InstanceId, address: string) =>
    invoke<ServerStatus>("server_status", { id, address }),
  previewLaunch: (id: InstanceId) => invoke<InvocationView>("preview_launch", { id }),
  gameStatus: (id: InstanceId) => invoke<GameStatus | null>("game_status", { id }),
  gameLog: (id: InstanceId) => invoke<string[]>("game_log", { id }),
  stopGame: (id: InstanceId) => invoke<void>("stop_game", { id }),

  overrides: (id: InstanceId) => invoke<MachineOverrides>("overrides", { id }),
  degradationNotice: (id: InstanceId) =>
    invoke<DegradationNotice | null>("degradation_notice", { id }),
  instanceGlance: (id: InstanceId) => invoke<InstanceGlance>("instance_glance", { id }),
  setOverrides: (id: InstanceId, settings: MachineOverrides) =>
    invoke<MachineOverrides>("set_overrides", { id, settings }),
  /** What an instance with no memory of its own is given on this machine. */
  defaultMemoryMb: () => invoke<number>("default_memory_mb"),
  /** ash's own default, which this machine's Automatic follows. */
  ashDefaultMemoryMb: () => invoke<number>("ash_default_memory_mb"),
  machineDefaults: () => invoke<MachineDefaults>("machine_defaults"),
  setMachineDefaults: (defaults: MachineDefaults) =>
    invoke<MachineDefaults>("set_machine_defaults", { defaults }),

  launcherPreferences: () => invoke<LauncherPreferences>("launcher_preferences"),
  setLauncherPreferences: (preferences: LauncherPreferences) =>
    invoke<LauncherPreferences>("set_launcher_preferences", { preferences }),

  instances: () => invoke<Instance[]>("instances"),
  /** The loaders this version target can run, vanilla always among them. */
  loadersFor: (versionId: string) =>
    invoke<Loader[]>("loaders_for", { versionId }),
  createInstance: (name: string, versionId: string, loader: Loader) =>
    invoke<Instance>("create_instance", { name, versionId, loader }),
  renameInstance: (id: InstanceId, name: string) =>
    invoke<Instance>("rename_instance", { id, name }),
  previewDeletion: (id: InstanceId) =>
    invoke<DeletionPreview>("preview_deletion", { id }),
  deleteInstance: (id: InstanceId) => invoke<void>("delete_instance", { id }),
  /** The instance's `mods` folder, the player's, opened in the file manager. */
  revealModsFolder: (id: InstanceId) => invoke<void>("reveal_mods_folder", { id }),
  revealGameDirectory: (id: InstanceId) =>
    invoke<void>("reveal_game_directory", { id }),
  /** ash's own log: the first thing anyone asks for when a launch fails. */
  revealLog: () => invoke<void>("reveal_log"),

  /**
   * The Windows open-file dialog, for a Java the player picks; `null` if
   * they cancel. Only a path comes back: whether it is a Java ash can use is
   * ash-core's to say, when the setting is saved.
   */
  chooseJava: async (): Promise<string | null> => {
    const picked = await open({
      title: "Choose Java",
      multiple: false,
      directory: false,
      filters: [{ name: "Java (java.exe, javaw.exe)", extensions: ["exe"] }],
    });
    return typeof picked === "string" ? picked : null;
  },
};

/**
 * The launcher's own window, for the title bar that replaces the system one.
 *
 * Fetched on each call rather than once at load: outside the app - the
 * launcher UI check renders these screens in a plain browser - there is no
 * window to fetch, and nothing should fail until a button is pressed.
 */
export const appWindow = {
  minimise: () => getCurrentWindow().minimize(),
  toggleMaximise: () => getCurrentWindow().toggleMaximize(),
  close: () => getCurrentWindow().close(),
};

/**
 * Preparation runs for minutes over thousands of files, so it is not an
 * awaited call - progress and the final outcome both arrive as events.
 */
export function onPrepareProgress(handler: (event: PrepareEvent) => void) {
  return listen<PrepareEvent>("prepare-progress", (e) => handler(e.payload));
}

export function onPrepareFinished(handler: (outcome: PrepareOutcome) => void) {
  return listen<PrepareOutcome>("prepare-finished", (e) => handler(e.payload));
}

/**
 * Launching prepares whatever is missing first, so it reports progress on
 * the same `prepare-progress` channel and finishes on this one.
 */
/** An ash sign-in in the background finished, however it went. */
export function onAshAccount(handler: (event: AshAccountEvent) => void) {
  return listen<AshAccountEvent>("ash-account", (e) => handler(e.payload));
}

export function onLaunchFinished(handler: (outcome: LaunchOutcome) => void) {
  return listen<LaunchOutcome>("launch-finished", (e) => handler(e.payload));
}

// ---- formatting ------------------------------------------------------------

/**
 * ash-core records when something happened and leaves the wording to us, so
 * relative time is computed here against the viewer's own clock.
 *
 * Every timestamp crossing the boundary is milliseconds since the epoch.
 */
export function describeAge(timestamp: number, now = Date.now()): string {
  const seconds = Math.max(0, Math.round((now - timestamp) / 1000));
  if (seconds < 60) return "just now";

  const units: [number, string][] = [
    [60 * 60 * 24, "day"],
    [60 * 60, "hour"],
    [60, "minute"],
  ];
  for (const [size, name] of units) {
    const n = Math.floor(seconds / size);
    if (n >= 1) return `${n} ${name}${n === 1 ? "" : "s"} ago`;
  }
  return "just now";
}

/** "48 min", "14 h 20 min": a length of time, as the Play page shows one. */
export function describeDuration(ms: number): string {
  const minutes = Math.floor(Math.max(0, ms) / 60_000);
  if (minutes < 60) return `${minutes} min`;
  return `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
}

export function describeBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB"];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unit]}`;
}
