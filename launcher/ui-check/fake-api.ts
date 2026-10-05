/**
 * The launcher's API module with nothing behind it, for the launcher UI
 * check. The check's Vite config hands this module to every screen that
 * imports `src/api.ts`, so the real screens render in a plain browser.
 *
 * Everything not faked here is the real module's own: its types, labels and
 * formatting. What is faked is typed against the real thing, so a command
 * that changes shape in `src/api.ts` stops this file compiling.
 *
 * Which state the launcher is in comes from the page's `?state=`; the check
 * then clicks whatever reaches the rest (Play, a page, a menu).
 */
import * as real from "../src/api.ts";
import type {
  Account,
  DegradationNotice,
  Instance,
  LaunchOutcome,
  PrepareEvent,
  PrepareOutcome,
} from "../src/api.ts";

export * from "../src/api.ts";

const state = new URLSearchParams(window.location.search).get("state") ?? "idle";

const HOUR = 60 * 60 * 1000;
const now = Date.now();

// ---- faces ----------------------------------------------------------------

/**
 * A whole 64 by 64 skin with only its face drawn, at (8, 8) where Mojang's
 * skins have it, so the launcher's crop is exercised as it is with a real
 * skin. Made here rather than fetched: the check has no network.
 */
function skin(map: Record<string, string>, rows: string[]): string {
  const canvas = document.createElement("canvas");
  canvas.width = 64;
  canvas.height = 64;
  const g = canvas.getContext("2d");
  if (!g) return "";
  rows.forEach((row, y) =>
    [...row].forEach((ch, x) => {
      g.fillStyle = map[ch] ?? "#000";
      g.fillRect(8 + x, 8 + y, 1, 1);
    }),
  );
  return canvas.toDataURL();
}

const steve: Account = {
  profile_id: "steve",
  username: "Steve",
  added_at_ms: now - 400 * HOUR,
  skin_url: skin(
    { h: "#2c1e0e", k: "#b88a6e", d: "#9c6e52", w: "#ffffff", e: "#4a3aa8", m: "#6a4030", n: "#8d5d43" },
    ["hhhhhhhh", "hhhhhhhh", "hkkkkkkh", "kkkkkkkk", "kweknewk", "kkknnkkk", "kkmmmmkk", "kkdddddk"],
  ),
};

const alex: Account = {
  profile_id: "alex",
  username: "Alex",
  added_at_ms: now - 90 * HOUR,
  skin_url: skin(
    { h: "#e98a3a", k: "#f1c9a5", d: "#e2b28c", w: "#ffffff", e: "#3d8a3d", m: "#c98a6e", n: "#e6b896" },
    ["hhhhhhhh", "hhhhhhhh", "hkkkkkkh", "hkkkkkkh", "hweknewh", "hkknnkkk", "kkkmmkkk", "kkdddddk"],
  ),
};

// ---- the launcher's state -------------------------------------------------

const INSTANCES: Instance[] = [
  {
    id: "a",
    name: "1.21.11",
    version_id: "1.21.11",
    loader: "fabric",
    created_at_ms: now - 300 * HOUR,
    last_played_ms: now - 2 * HOUR,
  },
  {
    id: "b",
    name: "1.8.9 PvP",
    version_id: "1.8.9",
    loader: "legacy_fabric",
    created_at_ms: now - 200 * HOUR,
    last_played_ms: now - 26 * HOUR,
  },
  {
    id: "c",
    name: "Vanilla",
    version_id: "1.21.11",
    loader: "vanilla",
    created_at_ms: now - 100 * HOUR,
    last_played_ms: null,
  },
];

const signedIn = state !== "signed-out";
const instances = state === "no-instances" ? [] : INSTANCES;
const accounts: real.Accounts = signedIn
  ? { accounts: [steve, alex], active: steve.profile_id }
  : { accounts: [], active: null };

const notice: DegradationNotice | null =
  state === "degraded"
    ? {
        features: ["Hit indicator"],
        message: "Hit indicator did not load last time. An update to ash will fix it.",
      }
    : null;

// ---- events ---------------------------------------------------------------

const progress = new Set<(event: PrepareEvent) => void>();
const prepared = new Set<(outcome: PrepareOutcome) => void>();
const launched = new Set<(outcome: LaunchOutcome) => void>();

function subscribe<T>(handlers: Set<(value: T) => void>, handler: (value: T) => void) {
  handlers.add(handler);
  return Promise.resolve(() => void handlers.delete(handler));
}

function emit<T>(handlers: Set<(value: T) => void>, value: T) {
  handlers.forEach((handler) => handler(value));
}

/** What a launch does in each state: stall partway through downloading, or fail. */
function launch() {
  window.setTimeout(() => {
    if (state === "failed-launch") {
      emit(launched, {
        ok: false,
        invocation: null,
        error: {
          kind: "java_failed",
          message: "Java could not start the game. Check that no antivirus is blocking it.",
          retryable: true,
        },
      });
      return;
    }
    emit(progress, { event: "planned", total_files: 4300, missing_files: 1243, missing_bytes: 412_000_000, already_present: 3057 });
    emit(progress, {
      event: "downloaded",
      path: "assets/objects/ab/abcdef",
      bytes: 1,
      done_files: 512,
      done_bytes: 168_000_000,
    });
  }, 50);
}

// ---- the API --------------------------------------------------------------

const resolve = <T>(value: T) => Promise.resolve(value);
const nothing = () => Promise.resolve();

export const api: typeof real.api = {
  catalogue: () =>
    resolve({
      source: "cache",
      fetched_at_ms: now - HOUR,
      latest_release: "1.21.11",
      latest_snapshot: "1.21.11",
      versions: [
        { id: "1.21.11", kind: "release", released_at: "", url: "", first_class: true },
        { id: "1.8.9", kind: "release", released_at: "", url: "", first_class: true },
      ],
    }),
  refreshCatalogue: () => api.catalogue(),

  beginSignIn: () =>
    resolve({
      user_code: "ABCD-EFGH",
      verification_uri: "https://www.microsoft.com/link",
      expires_at_ms: now + HOUR,
      interval_secs: 3600,
    }),
  pollSignIn: () => resolve({ status: "waiting", interval_secs: 3600 }),
  cancelSignIn: nothing,
  accounts: () => resolve(accounts),
  selectAccount: (profileId) => resolve({ ...accounts, active: profileId }),
  removeAccount: (profileId) =>
    resolve({ accounts: accounts.accounts.filter((a) => a.profile_id !== profileId), active: null }),

  planInstance: (id) =>
    resolve({
      version_id: id,
      java_component: null,
      total_files: 4300,
      missing_files: state === "preparing" ? 1243 : 0,
      missing_bytes: state === "preparing" ? 412_000_000 : 0,
    }),
  prepareInstance: nothing,
  ensureRuntime: () => resolve({ component: "java-runtime-delta", version_name: "21", java_executable: "java" }),
  cancelPreparation: nothing,

  launch: async () => launch(),
  previewLaunch: () => resolve({ program: "java", args: [], working_directory: "" }),
  gameStatus: () => resolve(state === "playing" ? { state: "running" as const } : null),
  gameLog: () => resolve([]),
  stopGame: nothing,

  overrides: () => resolve({ memory_mb: null, java_executable: null, resolution: null }),
  degradationNotice: () => resolve(notice),
  setOverrides: (_id, settings) => resolve(settings),
  defaultMemoryMb: () => resolve(4096),

  instances: () => resolve(instances),
  loadersFor: (versionId) => resolve(versionId === "1.8.9" ? ["vanilla", "legacy_fabric"] : ["vanilla", "fabric"]),
  createInstance: () => Promise.reject({ kind: "unknown", message: "Not in the check.", retryable: false }),
  renameInstance: (id, name) => resolve({ ...INSTANCES.find((i) => i.id === id)!, name }),
  previewDeletion: (id) =>
    resolve({ instance: INSTANCES.find((i) => i.id === id)!, worlds: [], resource_packs: 0, screenshots: 0, total_bytes: 0 }),
  deleteInstance: nothing,
  revealGameDirectory: nothing,
  revealLog: nothing,
};

/** No window to move: the buttons do nothing here, which is all a screenshot needs. */
export const appWindow: typeof real.appWindow = {
  minimise: nothing,
  toggleMaximise: nothing,
  close: nothing,
};

export const onPrepareProgress: typeof real.onPrepareProgress = (handler) => subscribe(progress, handler);
export const onPrepareFinished: typeof real.onPrepareFinished = (handler) => subscribe(prepared, handler);
export const onLaunchFinished: typeof real.onLaunchFinished = (handler) => subscribe(launched, handler);
