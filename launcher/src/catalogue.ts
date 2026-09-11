/** Mirrors `ash_core::VersionKind`. */
export type VersionKind =
  | "release"
  | "snapshot"
  | "old_beta"
  | "old_alpha"
  | "other";

/** Mirrors `ash_core::CatalogueSource`. */
export type CatalogueSource = "network" | "cache";

/** Mirrors `ash_core::CatalogueEntry`. */
export type CatalogueEntry = {
  id: string;
  kind: VersionKind;
  released_at: string;
  first_class: boolean;
};

/** Mirrors `ash_core::Catalogue`. */
export type Catalogue = {
  source: CatalogueSource;
  fetched_at_unix: number;
  latest_release: string;
  latest_snapshot: string;
  versions: CatalogueEntry[];
};

/** Mirrors the adapter's `UiError`. `kind` is the stable thing to branch on. */
export type UiError = { kind: string; message: string };

/**
 * ash-core records when data was fetched and leaves the wording to us, so
 * "3 hours ago" is computed here against the viewer's own clock.
 */
export function describeAge(fetchedAtUnix: number, now = Date.now()): string {
  const seconds = Math.max(0, Math.round(now / 1000 - fetchedAtUnix));
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
