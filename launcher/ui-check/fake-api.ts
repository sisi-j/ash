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
  InstanceGlance,
  LaunchOutcome,
  PrepareEvent,
  PrepareOutcome,
  ServerEntry,
  ServerStatus,
} from "../src/api.ts";

export * from "../src/api.ts";

const state = new URLSearchParams(window.location.search).get("state") ?? "idle";

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;
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
  const pen = canvas.getContext("2d");
  if (!pen) return "";
  rows.forEach((row, y) =>
    [...row].forEach((ch, x) => {
      pen.fillStyle = map[ch] ?? "#000";
      pen.fillRect(8 + x, 8 + y, 1, 1);
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
    played_ms: 14 * HOUR + 20 * MINUTE,
    last_session: { started_ms: now - 2 * HOUR - 48 * MINUTE, ended_ms: now - 2 * HOUR },
  },
  {
    id: "b",
    name: "1.8.9 PvP",
    version_id: "1.8.9",
    loader: "legacy_fabric",
    created_at_ms: now - 200 * HOUR,
    last_played_ms: now - 26 * HOUR,
    played_ms: 62 * HOUR + 5 * MINUTE,
    last_session: { started_ms: now - 26 * HOUR - 72 * MINUTE, ended_ms: now - 26 * HOUR },
  },
  {
    id: "c",
    name: "Vanilla",
    version_id: "1.21.11",
    loader: "vanilla",
    created_at_ms: now - 100 * HOUR,
    last_played_ms: null,
    played_ms: 0,
    last_session: null,
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

/**
 * Each instance at a glance, as ash-core would put it together. While a game
 * is playing, the first instance's session is the running one.
 */
function glance(id: string): InstanceGlance {
  const instance = INSTANCES.find((i) => i.id === id)!;
  const running = state === "playing" && id === "a";
  const ashOn: string[] = ["Crosshair", "Hit indicator", "FPS readout", "Toggle sprint"];
  return {
    features:
      instance.loader === "vanilla"
        ? { state: "no_client" }
        : { state: "on", features: notice ? ashOn.filter((f) => !notice.features.includes(f)) : ashOn },
    played_ms: instance.played_ms + (running ? 12 * MINUTE : 0),
    last_session: running ? { started_ms: now - 12 * MINUTE, ended_ms: null } : instance.last_session,
    mods: id === "a" ? ["Sodium"] : [],
  };
}

// ---- servers ----------------------------------------------------------------

/** A server icon drawn here, in the base64 PNG form the game saves one in. */
function serverIcon(colour: string): string {
  const canvas = document.createElement("canvas");
  canvas.width = 8;
  canvas.height = 8;
  const pen = canvas.getContext("2d");
  if (!pen) return "";
  pen.fillStyle = colour;
  pen.fillRect(0, 0, 8, 8);
  pen.fillStyle = "#ffffff";
  pen.fillRect(2, 2, 4, 4);
  return canvas.toDataURL().replace("data:image/png;base64,", "");
}

/** Each instance's list as its game wrote it. The vanilla one has never opened Multiplayer. */
const SERVERS: Record<string, ServerEntry[]> = {
  a: [
    { name: "Hypixel", address: "mc.hypixel.net", icon: serverIcon("#e0a526") },
    { name: "Bedwars Practice", address: "bedwarspractice.club", icon: null },
    { name: "Old SMP", address: "smp.example.net:25570", icon: null },
  ],
  b: [{ name: "Hypixel", address: "mc.hypixel.net", icon: serverIcon("#e0a526") }],
  c: [],
};

const STATUSES: Record<string, ServerStatus> = {
  "mc.hypixel.net": { state: "online", players_online: 31_542, players_max: 200_000, version: "Requires MC 1.8 / 1.21", motd: "Hypixel Network", icon: null },
  "bedwarspractice.club": { state: "online", players_online: 312, players_max: 1000, version: "1.8.9", motd: "Practice", icon: null },
  "smp.example.net:25570": { state: "offline" },
};

/** Where the last Join went, for the check to read back. */
function joined(address: string) {
  (window as unknown as { joined?: string }).joined = address;
  launch();
}

/** States where clicking LAUNCH GAME starts a download, so the click has steps to show. */
const downloads = state === "preparing" || state === "sounds-off" || state === "download-only";

/**
 * A crash: running when the page first asks, and gone - uncleanly - every
 * time after, as a game that fell over a moment after starting would be.
 */
let statusAsked = 0;
function status(): real.GameStatus | null {
  statusAsked += 1;
  if (state === "playing") return { state: "running" };
  if (state === "crashed") return statusAsked === 1 ? { state: "running" } : { state: "exited", code: 1, clean: false };
  return null;
}

const CRASH_LOG = [
  "[Render thread/INFO]: Loaded 1371 recipes",
  "[Render thread/ERROR]: Unreported exception thrown!",
  "java.lang.OutOfMemoryError: Java heap space",
  "	at net.minecraft.client.renderer.LevelRenderer.renderLevel(LevelRenderer.java:1204)",
  "	at net.minecraft.client.Minecraft.runTick(Minecraft.java:1311)",
];

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
  removeAccount: (profileId) => {
    // As ash-core does it: signing out the active account hands the slot to
    // the first one left, so no screen shows a state the app never reaches.
    const left = accounts.accounts.filter((a) => a.profile_id !== profileId);
    const active = accounts.active === profileId ? (left[0]?.profile_id ?? null) : accounts.active;
    return resolve({ accounts: left, active });
  },

  planInstance: (id) =>
    state === "unchecked"
      ? Promise.reject({ kind: "offline", message: "ash could not reach Mojang to check this instance.", retryable: true })
      : resolve({
      version_id: id,
      java_component: null,
      total_files: 4300,
      missing_files: downloads ? 1243 : 0,
      missing_bytes: downloads ? 412_000_000 : 0,
        }),
  prepareInstance: async () => launch(),
  ensureRuntime: () => resolve({ component: "java-runtime-delta", version_name: "21", java_executable: "java" }),
  cancelPreparation: nothing,

  launch: async () => launch(),
  join: async (_id, address) => joined(address),
  servers: (id) => resolve(SERVERS[id] ?? []),
  serverStatus: (_id, address) => resolve(STATUSES[address] ?? { state: "offline" }),
  previewLaunch: () => resolve({ program: "java", args: [], working_directory: "" }),
  gameStatus: () => resolve(status()),
  gameLog: () => resolve(state === "crashed" ? CRASH_LOG : []),
  stopGame: nothing,

  overrides: () => resolve({ memory_mb: null, java_executable: null, resolution: null }),
  degradationNotice: () => resolve(notice),
  instanceGlance: (id) => resolve(glance(id)),
  // ash-core's own refusal for a window it would not open.
  setOverrides: (_id, settings) =>
    settings.resolution &&
    [settings.resolution.width, settings.resolution.height].some((side) => side < 320 || side > 15360)
      ? Promise.reject({ kind: "invalid_setting", message: "Window size must be between 320 and 15360 pixels.", retryable: false })
      : resolve(settings),
  defaultMemoryMb: () => resolve(4096),
  launcherPreferences: () => resolve({ launch_sounds: state !== "sounds-off" }),
  setLauncherPreferences: (preferences) => resolve(preferences),

  instances: () => resolve(instances),
  loadersFor: (versionId) => resolve(versionId === "1.8.9" ? ["vanilla", "legacy_fabric"] : ["vanilla", "fabric"]),
  createInstance: () => Promise.reject({ kind: "unknown", message: "Not in the check.", retryable: false }),
  renameInstance: (id, name) => resolve({ ...INSTANCES.find((i) => i.id === id)!, name }),
  previewDeletion: (id) =>
    resolve({
      instance: INSTANCES.find((i) => i.id === id)!,
      worlds: ["Survival", "Bedwars practice"],
      resource_packs: 2,
      screenshots: 41,
      total_bytes: 1_840_000_000,
    }),
  deleteInstance: nothing,
  revealGameDirectory: nothing,
  revealLog: nothing,
  chooseJava: () => resolve(String.raw`C:\Program Files\Java\jdk-21\bin\javaw.exe`),
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
