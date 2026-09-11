import { invoke } from "@tauri-apps/api/core";

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

/** `kind` is the stable discriminant; `message` is what to show a player. */
export type UiError = { kind: string; message: string };

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
