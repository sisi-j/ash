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
  AshAccountEvent,
  AshAccountStatus,
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
  ash_account_notice: state === "ash-notice" || state === "ash-notice-dismissed",
  ash_account_deleted: false,
  skin_url: skin(
    { h: "#2c1e0e", k: "#b88a6e", d: "#9c6e52", w: "#ffffff", e: "#4a3aa8", m: "#6a4030", n: "#8d5d43" },
    ["hhhhhhhh", "hhhhhhhh", "hkkkkkkh", "kkkkkkkk", "kweknewk", "kkknnkkk", "kkmmmmkk", "kkdddddk"],
  ),
};

const alex: Account = {
  profile_id: "alex",
  username: "Alex",
  added_at_ms: now - 90 * HOUR,
  ash_account_notice: false,
  ash_account_deleted: false,
  skin_url: skin(
    { h: "#e98a3a", k: "#f1c9a5", d: "#e2b28c", w: "#ffffff", e: "#3d8a3d", m: "#c98a6e", n: "#e6b896" },
    ["hhhhhhhh", "hhhhhhhh", "hkkkkkkh", "hkkkkkkh", "hweknewh", "hkknnkkk", "kkkmmkkk", "kkdddddk"],
  ),
};

/** A cover made here, as the faces are: the check has no network. */
function cover(): string {
  const canvas = document.createElement("canvas");
  canvas.width = 320;
  canvas.height = 140;
  const pen = canvas.getContext("2d");
  if (!pen) return "";
  const sky = pen.createLinearGradient(0, 0, 0, 140);
  sky.addColorStop(0, "#1b2a4a");
  sky.addColorStop(1, "#3c6e5a");
  pen.fillStyle = sky;
  pen.fillRect(0, 0, 320, 140);
  pen.fillStyle = "#5b3a24";
  pen.fillRect(0, 110, 320, 30);
  return canvas.toDataURL();
}

/** A placeholder cape, made here as the faces are: one colour on the cape's faces. */
function capeTexture(colour: string): string {
  const canvas = document.createElement("canvas");
  canvas.width = 64;
  canvas.height = 32;
  const pen = canvas.getContext("2d");
  if (!pen) return "";
  pen.fillStyle = colour;
  pen.fillRect(0, 0, 22, 17);
  pen.fillStyle = "#ffffff";
  pen.fillRect(16, 5, 3, 3);
  return canvas.toDataURL();
}

const CAPES: real.Cosmetic[] = [
  { id: "cape.ash", slot: "cape", name: "Ash", texture: capeTexture("#585860") },
  { id: "cape.ember", slot: "cape", name: "Ember", texture: capeTexture("#e0702a") },
  { id: "cape.midnight", slot: "cape", name: "Midnight", texture: capeTexture("#28346e") },
];

let wearing: string | null = state === "wardrobe-none" ? null : "cape.ember";
/** How often the Wardrobe has been asked for: "wardrobe-try-again" signs in on the second. */
let wardrobeAsked = 0;
const wardrobe = (): real.Wardrobe => {
  const out = state === "wardrobe-signed-out" || (state === "wardrobe-try-again" && wardrobeAsked < 2);
  return {
    signed_in: !out,
    ash_account: out ? { state: "unreachable" } : { state: "signed_in" },
    cosmetics: CAPES,
    equipped: { cape: out ? null : wearing, head: null, back: null },
    skin: steve.skin_url,
  };
};

const NEWS: real.NewsPost[] = [
  {
    id: "n1",
    kind: "patch_notes",
    title: "ash 0.2: your ash account",
    body: `## What's new

- **ash accounts**, made when you sign in.
- Delete yours any time in *Settings*.

More in the [privacy statement](https://ashlauncher.com/privacy.html).

Text like <script>alert("x")</script> stays text.`,
    cover_url: cover(),
    published_at: now - 26 * HOUR,
    updated_at: now - 26 * HOUR,
  },
  {
    id: "n2",
    kind: "news",
    title: "Synced settings are next",
    body: `Your crosshair, readouts and servers will follow you between computers.

1. Settings
2. News
3. Cosmetics`,
    cover_url: null,
    published_at: now - 72 * HOUR,
    updated_at: now - 72 * HOUR,
  },
];

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
    sync_id: null,
    local_only: false,
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
    sync_id: null,
    local_only: false,
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
    sync_id: null,
    local_only: false,
  },
];

const signedIn = !state.startsWith("signed-out");
const syncStatus: real.SyncStatus = {
  enabled: state !== "sync-off",
  state:
    state === "sync-off"
      ? { state: "off" }
      : state === "sync-unreachable"
        ? { state: "unreachable" }
        : { state: "synced", at_ms: now - 2 * MINUTE },
  deleted_elsewhere: state === "sync-deleted-elsewhere" ? [{ instance_id: "b", name: "1.8.9 PvP" }] : [],
};
const ashStatus: AshAccountStatus =
  state === "ash-unreachable"
    ? { state: "unreachable" }
    : state === "ash-refused"
      ? {
          state: "refused",
          kind: "multiplayer_disabled",
          message:
            "Multiplayer is turned off for this Microsoft account, so ash's online features can't sign in. Playing is unaffected. Multiplayer is allowed in the account's Xbox privacy settings.",
        }
      : { state: "signed_in" };
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
    // A retired target's instance (ADR-0028), in its own state.
    newest: state.startsWith("instance-retired") ? { version_id: "26.4", loader: "fabric" } : null,
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

/** Each instance's servers as ash-core serves them: the 1.21.11 one has joined Hypixel, the 1.8.9 one nothing yet, and the vanilla one has never opened Multiplayer. */
const SERVERS: Record<string, ServerEntry[]> = {
  a: [
    { name: "Hypixel", address: "mc.hypixel.net", icon: serverIcon("#e0a526"), last_joined_ms: now - 2 * HOUR },
    { name: "Bedwars Practice", address: "bedwarspractice.club", icon: null, last_joined_ms: null },
    { name: "Old SMP", address: "smp.example.net:25570", icon: null, last_joined_ms: null },
  ],
  b: [{ name: "Hypixel", address: "mc.hypixel.net", icon: serverIcon("#e0a526"), last_joined_ms: null }],
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
/** States where the game falls over a moment after starting. */
const crashes = state.startsWith("crashed");
function status(): real.GameStatus | null {
  statusAsked += 1;
  if (state === "playing" || state === "update-blocked") return { state: "running" };
  if (crashes) return statusAsked === 1 ? { state: "running" } : { state: "exited", code: 1, clean: false };
  // The player closing the game window: a clean quit, a moment after the page first asks.
  if (state === "game-closed") return statusAsked === 1 ? { state: "running" } : { state: "exited", code: 0, clean: true };
  return null;
}

let bootAsked = 0;

/**
 * A launched game's steps to opening its window, one per ask;
 * "launch-loading" stays loading its mods. A game found already running is
 * already up.
 */
function boot(): real.GameBoot {
  if (state === "launch-loading") return { stage: "loading_mods", players_mods: 7 };
  if (!starts) return { stage: "ready", players_mods: 7 };
  const steps: real.BootStage[] = ["starting", "starting", "loading_mods", "loading_mods", "ready"];
  return { stage: steps[Math.min(bootAsked++, steps.length - 1)] ?? "ready", players_mods: 7 };
}

/** Modrinth, as the Mods page sees it: icons as data URLs, since the check runs offline. */
const MODS = [
  { project_id: "sodium", slug: "sodium", title: "Sodium", description: "The fastest rendering optimization mod for Minecraft.", downloads: 112_400_000 },
  { project_id: "iris", slug: "iris", title: "Iris Shaders", description: "A modern shader pack loader for Minecraft, compatible with Sodium.", downloads: 75_100_000 },
  { project_id: "fabric-api", slug: "fabric-api", title: "Fabric API", description: "Lightweight and modular API providing common hooks and intercompatibility measures.", downloads: 140_000_000 },
  { project_id: "modmenu", slug: "modmenu", title: "Mod Menu", description: "Adds a mod menu to view the list of mods you have installed.", downloads: 61_900_000 },
].map((m, i) => ({ ...m, icon_url: capeTexture(["#2f7d4f", "#6d3fbf", "#8a6d3b", "#3f6dbf"][i]!) }));
const installedProjects = new Set<string>();

/** The instance's mods folder, in the mods-list state; empty elsewhere. */
let folderMods: real.ModEntry[] =
  state === "mods-list"
    ? [
        { file_name: "sodium.jar", enabled: true, title: "Sodium", version: "0.8.14", from_modrinth: true, icon_url: MODS[0]!.icon_url, overlaps: null },
        { file_name: "freelook.jar", enabled: true, title: "Freelook", version: "1.3.0", from_modrinth: true, icon_url: null, overlaps: "Freelook" },
        { file_name: "handmod-2.3.4.jar", enabled: false, title: "Hand Mod", version: "2.3.4", from_modrinth: false, icon_url: null, overlaps: null },
      ]
    : [];
const folder = () => folderMods.map((m) => ({ ...m }));

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
const updating = new Set<(progress: real.UpdateProgress) => void>();
const ashAccount = new Set<(event: AshAccountEvent) => void>();

function subscribe<T>(handlers: Set<(value: T) => void>, handler: (value: T) => void) {
  handlers.add(handler);
  return Promise.resolve(() => void handlers.delete(handler));
}

function emit<T>(handlers: Set<(value: T) => void>, value: T) {
  handlers.forEach((handler) => handler(value));
}

/** States whose launch gets the game up, for what the launcher does then. */
const starts = state === "minimise-on-start" || state === "close-on-start" || state === "launch-loading";

/** What the launcher was set to do once the game starts, in each state. */
const onGameStart: real.OnGameStart =
  state === "close-on-start" ? "close" : state === "minimise-on-start" || state === "failed-launch" ? "minimise" : "keep_open";

/** What a launch does in each state: stall partway through downloading, fail, or start the game. */
function launch() {
  window.setTimeout(() => {
    if (starts) {
      emit(progress, { event: "done", version_id: "1.21.11" });
      emit(launched, { ok: true, invocation: { program: "java", args: ["--username", "Steve"], working_directory: "" }, error: null });
      return;
    }
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
  // The browser never comes back here: the check is of what ash shows meanwhile.
  signInWithBrowser: () => {
    (window as unknown as { openedBrowserSignIn?: boolean }).openedBrowserSignIn = true;
    return new Promise(() => undefined);
  },
  accounts: () => resolve(accounts),
  ashAccountStatus: () => resolve(ashStatus),
  dismissAshAccountNotice: (profileId) =>
    resolve({
      ...accounts,
      accounts: accounts.accounts.map((a) => (a.profile_id === profileId ? { ...a, ash_account_notice: false } : a)),
    }),
  deleteAshAccount: (profileId) =>
    state === "ash-delete-failed"
      ? Promise.reject({ kind: "unexpected_status", message: "x", retryable: true })
      : resolve({
          ...accounts,
          accounts: accounts.accounts.map((a) => (a.profile_id === profileId ? { ...a, ash_account_deleted: true } : a)),
        }),
  news: () =>
    state === "news-offline"
      ? resolve({ posts: NEWS, unread: false, offline: true })
      : resolve({ posts: state === "news-empty" ? [] : NEWS, unread: state === "news-dot", offline: false }),
  markNewsSeen: nothing,
  syncStatus: () => resolve(syncStatus),
  // An update in the update-* states; none elsewhere, as when ash is current.
  checkForUpdate: () =>
    resolve(
      state.startsWith("update-")
        ? {
            version: "0.1.0-alpha.2",
            notes: ["- **Mods:** search Modrinth from the Mods page.", "- Fixed the PLAYING button after a game closes."].join(
              "\n",
            ),
          }
        : null,
    ),
  installUpdate: () => {
    if (state === "update-failed") {
      return Promise.reject({
        kind: "update_failed",
        message: "The update couldn't be installed (the download was interrupted). This version keeps working; try again later.",
        retryable: true,
      });
    }
    // Downloading, and then ash would close: the promise never settles here.
    window.setTimeout(() => emit(updating, { downloaded: 4_000_000, total: 10_000_000 }), 100);
    return new Promise<void>(() => undefined);
  },
  wardrobe: () => {
    wardrobeAsked += 1;
    return resolve(wardrobe());
  },
  equip: async (_slot, cosmeticId) => {
    wearing = cosmeticId;
    return wardrobe();
  },
  setSyncEnabled: (enabled) => resolve({ ...syncStatus, enabled, state: enabled ? { state: "waiting" } : { state: "off" } }),
  resolveDeletedElsewhere: () => resolve({ ...syncStatus, deleted_elsewhere: [] }),
  openSignInPage: async () => {
    (window as unknown as { openedSignIn?: boolean }).openedSignIn = true;
  },
  openNewsLink: async (url) => {
    (window as unknown as { openedLink?: string }).openedLink = url;
  },
  appVersion: () => resolve("0.1.0-alpha.1"),
  // A test build in every state but one, so beta work is checked on every
  // pull request (ADR-0029); `settings-version-release` is the release build.
  betaBuild: () => resolve(state !== "settings-version-release"),
  searchMods: (_id, query, sort) => {
    // ash's picks leave out Fabric API; every other sort is by downloads here.
    const found = MODS.filter(
      (m) => m.title.toLowerCase().includes(query.toLowerCase()) && (sort !== "suggested" || m.project_id !== "fabric-api"),
    );
    if (sort === "downloads") found.sort((a, b) => b.downloads - a.downloads);
    return resolve({
      total: found.length,
      hits: found.map((m) => ({
        ...m,
        installed: installedProjects.has(m.project_id),
        included: m.project_id === "fabric-api",
      })),
    });
  },
  installMod: (_id, projectId) => {
    if (state === "mods-incompatible") {
      return Promise.reject({
        kind: "mod_incompatible",
        message: "Iris Shaders doesn't work with OptiFabric, which this instance has. Remove OptiFabric first to install it.",
        retryable: false,
      });
    }
    installedProjects.add(projectId);
    const mod = MODS.find((m) => m.project_id === projectId)!;
    const installed = [mod, ...(projectId === "iris" ? [MODS[0]!] : [])].map((m) => ({
      project_id: m.project_id,
      title: m.title,
      version_id: "v1",
      version_number: "1.0.0",
      version_type: "release",
      file_name: `${m.slug}.jar`,
      sha512: "",
      icon_url: null,
    }));
    return new Promise((done) => window.setTimeout(() => done({ installed, mods_turned_on: true, optional: [] }), 150));
  },
  installedMods: () => resolve([]),
  listMods: () => resolve(folder()),
  setModEnabled: (_id, fileName, enabled) => {
    folderMods = folderMods.map((m) => (m.file_name === fileName ? { ...m, enabled } : m));
    return resolve(folder());
  },
  removeMod: (_id, fileName) => {
    folderMods = folderMods.filter((m) => m.file_name !== fileName);
    return resolve(folder());
  },
  report: (request) =>
    resolve(
      [
        "ash report",
        "ash: 0.1.0-alpha.1",
        "Windows: Windows 11 Pro 24H2 (build 26200)",
        "Instance: 1.21.11, Fabric, your mods off",
        `Error: ${request.errorKind}: ${request.errorMessage}`,
        ...(request.crashed ? ["", "--- the game's last 200 lines ---", ...CRASH_LOG] : []),
        "",
        "--- ash's last 200 log lines ---",
        "2026-10-24T21:00:00Z  INFO  launch  instance=npm-test version=1.21.11",
      ].join("\n"),
    ),
  links: () => resolve({ feedback_url: state === "no-feedback-link" ? null : "https://discord.gg/ash-alpha" }),
  openFeedbackLink: async () => {
    (window as unknown as { openedFeedback?: boolean }).openedFeedback = true;
  },
  // Shown only where a state is about it, so every other screenshot stays as it was.
  firstRunNoteDismissed: () => resolve(state !== "alpha-note"),
  dismissFirstRunNote: nothing,
  tourSeen: () => resolve(state !== "tour" && state !== "tour-start" && state !== "tour-mods"),
  markTourSeen: async () => {
    (window as unknown as { tourMarked?: boolean }).tourMarked = true;
  },
  openPrivacyStatement: async () => {
    (window as unknown as { openedPrivacy?: boolean }).openedPrivacy = true;
  },
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
      : (state === "launch-during-check" ? new Promise((r) => window.setTimeout(r, 2500)) : resolve(null)).then(() => ({
      version_id: id,
      java_component: null,
      total_files: 4300,
      missing_files: downloads ? 1243 : 0,
      missing_bytes: downloads ? 412_000_000 : 0,
        })),
  prepareInstance: async () => launch(),
  ensureRuntime: () => resolve({ component: "java-runtime-delta", version_name: "21", java_executable: "java" }),
  // As ash-core answers a cancel: the launch it stopped finishes as cancelled.
  cancelPreparation: async () =>
    emit(launched, { ok: false, invocation: null, error: { kind: "cancelled", message: "Cancelled.", retryable: true } }),

  launch: async () => launch(),
  launchWithoutThirdPartyMods: async () => {
    (window as unknown as { withoutMods?: boolean }).withoutMods = true;
    launch();
  },
  join: async (_id, address) => joined(address),
  servers: (id) => resolve(SERVERS[id] ?? []),
  serverList: (id) => resolve([...(SERVERS[id] ?? [])]),
  addServer: async (id, name, address) => {
    (SERVERS[id] ??= []).push({ name: name.trim() || "Minecraft Server", address: address.trim(), icon: null, last_joined_ms: null });
  },
  editServer: async (id, position, _expected, name, address) => {
    const list = SERVERS[id] ?? [];
    const old = list[position];
    if (old) list[position] = { ...old, name: name.trim() || "Minecraft Server", address: address.trim() };
  },
  removeServer: async (id, position) => {
    (SERVERS[id] ?? []).splice(position, 1);
  },
  moveServer: async (id, position, _expected, to) => {
    const list = SERVERS[id] ?? [];
    const [moving] = list.splice(position, 1);
    if (moving) list.splice(to, 0, moving);
  },
  serverStatus: (_id, address) => resolve(STATUSES[address] ?? { state: "offline" }),
  previewLaunch: () => resolve({ program: "java", args: [], working_directory: "" }),
  gameStatus: () => resolve(status()),
  gameBoot: () => resolve(boot()),
  gameLog: () => resolve(crashes ? CRASH_LOG : []),
  stopGame: nothing,

  // The 1.21.11 instance has the player's own mods on, except where a crash must be one of ash's.
  overrides: (id) =>
    resolve({ memory_mb: null, java_executable: null, resolution: null, third_party_mods: id === "a" && state !== "crashed" }),
  degradationNotice: () => resolve(notice),
  instanceGlance: (id) => resolve(glance(id)),
  // ash-core's own refusal for a window it would not open.
  setOverrides: (_id, settings) =>
    settings.resolution &&
    [settings.resolution.width, settings.resolution.height].some((side) => side < 320 || side > 15360)
      ? Promise.reject({ kind: "invalid_setting", message: "Window size must be between 320 and 15360 pixels.", retryable: false })
      : resolve(settings),
  defaultMemoryMb: () => resolve(4096),
  launcherPreferences: () => resolve({ launch_sounds: state !== "sounds-off", on_game_start: onGameStart }),
  ashDefaultMemoryMb: () => resolve(2048),
  machineDefaults: () => resolve({ memory_mb: state === "settings-memory" ? 6144 : null }),
  setMachineDefaults: (defaults) => resolve(defaults),
  setLauncherPreferences: (preferences) => resolve(preferences),

  instances: () => resolve(instances),
  loadersFor: (versionId) => resolve(versionId === "1.8.9" ? ["vanilla", "legacy_fabric"] : ["vanilla", "fabric"]),
  createInstance: (name, versionId, loader) => {
    (window as unknown as { created?: string }).created = `${name} ${versionId} ${loader}`;
    return Promise.reject({ kind: "unknown", message: "Not in the check.", retryable: false });
  },
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
  revealModsFolder: nothing,
  revealLog: nothing,
  chooseJava: () => resolve(String.raw`C:\Program Files\Java\jdk-21\bin\javaw.exe`),
};

/** No window to move: minimise and close are only noted, so the check can ask what was done. */
export const appWindow: typeof real.appWindow = {
  minimise: async () => windowDid("minimise"),
  toggleMaximise: nothing,
  close: async () => windowDid("close"),
};

export const onPrepareProgress: typeof real.onPrepareProgress = (handler) => subscribe(progress, handler);
export const onPrepareFinished: typeof real.onPrepareFinished = (handler) => subscribe(prepared, handler);
export const onLaunchFinished: typeof real.onLaunchFinished = (handler) => subscribe(launched, handler);
export const onUpdateProgress: typeof real.onUpdateProgress = (handler) => subscribe(updating, handler);
export const onAshAccount: typeof real.onAshAccount = (handler) => subscribe(ashAccount, handler);
export const onSync: typeof real.onSync = () => Promise.resolve(() => undefined);

/** What the launcher did to its own window, for the check to read back. */
function windowDid(what: "minimise" | "close") {
  (window as unknown as { windowDid?: string }).windowDid = what;
}
