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

export type Instance = {
  id: InstanceId;
  name: string;
  version_id: string;
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

export type Plan = {
  version_id: string;
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
  | { event: "reverifying"; path: string }
  | { event: "cancelled" }
  | { event: "done"; version_id: string };

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
  cancelPreparation: () => invoke<void>("cancel_preparation"),

  instances: () => invoke<Instance[]>("instances"),
  createInstance: (name: string, versionId: string) =>
    invoke<Instance>("create_instance", { name, versionId }),
  renameInstance: (id: InstanceId, name: string) =>
    invoke<Instance>("rename_instance", { id, name }),
  previewDeletion: (id: InstanceId) =>
    invoke<DeletionPreview>("preview_deletion", { id }),
  deleteInstance: (id: InstanceId) => invoke<void>("delete_instance", { id }),
  revealGameDirectory: (id: InstanceId) =>
    invoke<void>("reveal_game_directory", { id }),
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
