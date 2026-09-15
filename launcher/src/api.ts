import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

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
export type Loader = "vanilla" | "fabric";

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
};

export type Instance = {
  id: InstanceId;
  name: string;
  version_id: string;
  /** Fixed when the instance was created; nothing can change it afterwards. */
  loader: Loader;
  created_at_ms: number;
  last_played_ms: number | null;
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
};

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

  planInstance: (id: InstanceId) => invoke<Plan>("plan_instance", { id }),
  /** Returns as soon as the work is scheduled; watch the events for outcome. */
  prepareInstance: (id: InstanceId) => invoke<void>("prepare_instance", { id }),
  ensureRuntime: (id: InstanceId) => invoke<Runtime>("ensure_runtime", { id }),
  cancelPreparation: (id: InstanceId) => invoke<void>("cancel_preparation", { id }),

  /** Returns as soon as the work is scheduled; watch the events for outcome. */
  launch: (id: InstanceId) => invoke<void>("launch", { id }),
  previewLaunch: (id: InstanceId) => invoke<InvocationView>("preview_launch", { id }),
  gameStatus: (id: InstanceId) => invoke<GameStatus | null>("game_status", { id }),
  gameLog: (id: InstanceId) => invoke<string[]>("game_log", { id }),
  stopGame: (id: InstanceId) => invoke<void>("stop_game", { id }),

  overrides: (id: InstanceId) => invoke<MachineOverrides>("overrides", { id }),
  setOverrides: (id: InstanceId, settings: MachineOverrides) =>
    invoke<MachineOverrides>("set_overrides", { id, settings }),
  defaultMemoryMb: () => invoke<number>("default_memory_mb"),

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
  revealGameDirectory: (id: InstanceId) =>
    invoke<void>("reveal_game_directory", { id }),
  /** ash's own log: the first thing anyone asks for when a launch fails. */
  revealLog: () => invoke<void>("reveal_log"),
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
