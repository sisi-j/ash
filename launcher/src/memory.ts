/**
 * Memory as the launcher shows it: a slider in gigabytes over ash-core's
 * megabytes. Shared by an instance's own page and the Settings page's
 * default, so the two always offer the same range.
 */

/** The memory slider's range and step, in GB. */
export const MEMORY_MIN_GB = 2;
export const MEMORY_MAX_GB = 16;
export const MEMORY_STEP_GB = 0.5;

/** Megabytes in gigabytes as a label says them: exact, to one decimal. */
export function formatGb(mb: number): string {
  const gb = mb / 1024;
  return Number.isInteger(gb * 2) ? `${gb}` : gb.toFixed(1);
}

/** Megabytes as the slider's gigabytes, to its half-gigabyte step. */
export function toGb(mb: number): number {
  return Math.round((mb / 1024) * 2) / 2;
}

/**
 * Where the knob sits. ash-core allows more than the slider shows (512 MB
 * to 64 GB), so a figure set before this page existed keeps its own label
 * while the knob rests at the nearer end, until the player moves it.
 */
export function onSlider(gb: number): number {
  return Math.min(MEMORY_MAX_GB, Math.max(MEMORY_MIN_GB, gb));
}

/** How far along the slider's track the knob is, for its filled part. */
export function filledPercent(gb: number): string {
  return `${((gb - MEMORY_MIN_GB) / (MEMORY_MAX_GB - MEMORY_MIN_GB)) * 100}%`;
}
