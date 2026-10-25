/**
 * The launcher UI check: the real screens, built as the app builds them,
 * rendered in Chromium against a fake of the launcher's API, once per state
 * that matters. The Windows app's web view is Chromium too.
 *
 * A state fails if the page throws or logs an error, if it never shows what
 * that state should show, or if any text on it is drawn in a face other
 * than the bundled Inter - asked of the browser's renderer, which reports
 * the fonts it actually drew with, not the ones the stylesheet asked for.
 *
 * Screenshots go to `ui-check/screenshots/` for a person to look at. They
 * are not compared against anything: font rendering differs by machine.
 *
 *     npm run check:ui
 */
import { mkdirSync } from "node:fs";
import path from "node:path";
import { chromium, type Page } from "playwright";
import { build, preview } from "vite";

const here = import.meta.dirname;
const configFile = path.join(here, "vite.config.ts");
const screenshots = path.join(here, "screenshots");

type State = {
  name: string;
  /** Text that is only on screen once this state has been reached. */
  shows: string;
  /** What to click, after load, to reach it. */
  reach?: (page: Page) => Promise<void>;
  /** Anything else this state must be true of; a problem, or null. */
  verify?: (page: Page) => Promise<string | null>;
};

/**
 * LAUNCH GAME, once it can be clicked: before the instance's files have
 * been checked it is shown but ignores clicks.
 */
/** The Mods page's Browse Modrinth view. */
const browseMods = async (page: Page) => {
  await openPage("Mods")(page);
  await page.getByRole("tab", { name: "Browse Modrinth" }).click();
};

const launchGame = (page: Page) => page.locator('.launch-button[aria-disabled="false"]').click();
const openInstance = (page: Page) => page.getByRole("button", { name: "1.21.11 settings" }).click();
const openPage = (label: string) => (page: Page) => page.getByRole("navigation").getByRole("button", { name: label }).click();

/** What the launcher did to its own window, if anything: minimise or close. */
const windowDid = (page: Page) => page.evaluate(() => (window as unknown as { windowDid?: string }).windowDid);

/** How many audio contexts the page made: one the first time a sound plays, none if none ever does. */
const soundsMade = (page: Page) => page.evaluate(() => (window as unknown as { audioContexts?: number }).audioContexts ?? 0);

const STATES: State[] = [
  { name: "signed-out", shows: "Sign in with Microsoft" },
  {
    // One click: the browser opens Microsoft's code page, the code copied to paste.
    name: "signed-out-signing-in",
    shows: "Your browser has opened",
    reach: (page) => page.getByRole("button", { name: "Sign in with Microsoft" }).click(),
    verify: async (page) => {
      const opened = await page.evaluate(() => (window as unknown as { openedSignIn?: boolean }).openedSignIn);
      return opened ? null : "the sign-in page didn't open";
    },
  },
  { name: "no-instances", shows: "No instances yet" },
  {
    // The first time, a spotlight tour: one part of the window at a time,
    // starting with LAUNCH GAME, until Done; and it isn't shown again.
    name: "tour",
    shows: "1 of 7",
    verify: async (page) => {
      const spot = await page.locator(".tour-spot").boundingBox();
      const launch = await page.locator(".launch-button").boundingBox();
      if (!spot || !launch) return "nothing lit up";
      if (!(spot.x < launch.x && spot.x + spot.width > launch.x + launch.width && spot.y < launch.y))
        return "the first stop doesn't light up LAUNCH GAME";
      for (let i = 1; i < 7; i++) await page.getByRole("button", { name: "Next" }).click();
      if ((await page.getByText("7 of 7").count()) !== 1) return "the tour didn't reach its last stop";
      await page.getByRole("button", { name: "Done" }).click();
      if ((await page.locator(".tour").count()) !== 0) return "Done didn't end the tour";
      const marked = await page.evaluate(() => (window as unknown as { tourMarked?: boolean }).tourMarked);
      return marked ? null : "the tour would show again next time";
    },
  },
  {
    name: "idle",
    shows: "LAUNCH GAME",
    // With reduced motion the scene is one still frame: the same picture
    // half a second apart. And a picture at all, not a blank canvas.
    verify: async (page) => {
      const frame = () => page.locator(".scene").evaluate((c: HTMLCanvasElement) => c.toDataURL());
      const first = await frame();
      await page.waitForTimeout(500);
      const blank = await page.locator(".scene").evaluate((c: HTMLCanvasElement) => {
        const pixels = c.getContext("2d")!.getImageData(0, 0, c.width, c.height).data;
        return pixels.every((v, i) => i % 4 === 3 || v === 0);
      });
      if (blank) return "the scene drew nothing";
      return first === (await frame()) ? null : "the scene moved with reduced motion on";
    },
  },
  {
    name: "account-menu",
    shows: "Add account",
    reach: (page) => page.getByRole("button", { name: "Steve" }).click(),
  },
  {
    name: "preparing",
    shows: "DOWNLOADING",
    reach: launchGame,
    verify: async (page) => ((await soundsMade(page)) > 0 ? null : "the click made no sound with launch sounds on"),
  },
  {
    name: "sounds-off",
    shows: "DOWNLOADING",
    reach: launchGame,
    // The proof that nothing plays: no audio is ever set up.
    verify: async (page) => ((await soundsMade(page)) === 0 ? null : "a sound played with launch sounds off"),
  },
  { name: "playing", shows: "Playing now" },
  {
    name: "servers",
    shows: "31,542 online",
    verify: async (page) => {
      const card = page.locator(".servers-card");
      const open = await card.locator(".join:not(:disabled)").count();
      const offline = await card.getByRole("button", { name: "Join Old SMP" }).isDisabled();
      return open === 2 && offline ? null : `expected two servers to join and Old SMP not to be (${open} open)`;
    },
  },
  {
    name: "servers-recent",
    shows: "Recent servers",
    verify: async (page) =>
      (await page.locator(".servers-card .server-row").first().innerText()).includes("Hypixel") ? null : "the most recent server is not first",
  },
  {
    name: "servers-not-joined",
    shows: "Join starts 1.8.9 PvP",
    reach: (page) => page.getByRole("option", { name: /1.8.9 PvP/ }).click(),
    verify: async (page) =>
      (await page.locator(".servers-card h3").innerText()).startsWith("Servers") ? null : "an instance that has joined nothing is titled as if it had",
  },
  {
    name: "servers-edit",
    shows: "Edit servers",
    reach: (page) => page.locator(".servers-card .server-edit-open").click(),
    verify: async (page) => {
      const card = page.locator(".servers-card");
      const rows = await card.locator(".server-row").count();
      const firstUp = await card.getByRole("button", { name: "Move Hypixel up" }).isDisabled();
      const lastDown = await card.getByRole("button", { name: "Move Old SMP down" }).isDisabled();
      return rows === 3 && firstUp && lastDown
        ? null
        : `expected the three servers in the game's order, the ends unable to move past them (${rows} rows)`;
    },
  },
  {
    name: "servers-add",
    shows: "play.example.net",
    reach: async (page) => {
      await page.locator(".servers-card .server-edit-open").click();
      await page.getByRole("button", { name: "Add server" }).click();
      await page.getByLabel("Address").fill("play.example.net");
      await page.getByRole("button", { name: "Add", exact: true }).click();
    },
    verify: async (page) =>
      (await page.locator(".servers-card .server-row").count()) === 4 ? null : "the added server is not in the list",
  },
  {
    name: "servers-edit-form",
    shows: "Save",
    reach: async (page) => {
      await page.locator(".servers-card .server-edit-open").click();
      await page.getByRole("button", { name: "Edit Old SMP" }).click();
    },
    verify: async (page) =>
      (await page.getByLabel("Address").inputValue()) === "smp.example.net:25570"
        ? null
        : "the form does not start from the server's own address",
  },
  {
    name: "servers-join",
    shows: "DOWNLOADING",
    reach: (page) => page.getByRole("button", { name: "Join Hypixel" }).click(),
    verify: async (page) => {
      const joined = await page.evaluate(() => (window as unknown as { joined?: string }).joined);
      return joined === "mc.hypixel.net" ? null : `joined ${joined ?? "nothing"}`;
    },
  },
  {
    name: "servers-empty",
    shows: "No servers yet",
    reach: (page) => page.getByRole("option", { name: /Vanilla/ }).click(),
  },
  {
    name: "glance-ash",
    shows: "Toggle sprint",
    verify: async (page) => ((await page.locator(".glance-card").getByText("Sodium").count()) === 1 ? null : "the player's mod was not shown"),
  },
  {
    name: "glance-vanilla",
    shows: "Vanilla, no ash client",
    reach: (page) => page.getByRole("option", { name: /Vanilla/ }).click(),
    verify: async (page) =>
      (await page.locator(".glance-card").getByText("Never played").count()) === 1 ? null : "the card did not follow the selected instance",
  },
  {
    name: "glance-open",
    shows: "Window size",
    reach: (page) => page.getByRole("button", { name: "Instance settings" }).click(),
  },
  {
    name: "failed-launch",
    shows: "Java could not start the game.",
    reach: launchGame,
    // Set to minimise once the game starts: a launch that failed must leave ash where the player can read why.
    verify: async (page) => ((await windowDid(page)) === undefined ? null : `ash ${await windowDid(page)}d after a failed launch`),
  },
  {
    // Between Java starting and the game's window, the button says which step
    // the game is on, and the bar eases along within that step's stretch.
    name: "launch-loading",
    shows: "Loading your 7 mods",
    reach: launchGame,
    verify: async (page) => {
      const width = async () => parseFloat(await page.locator(".launch-fill").evaluate((el) => (el as HTMLElement).style.width));
      const first = await width();
      await page.waitForTimeout(800);
      const later = await width();
      if (!(first >= 55 && later > first && later < 100)) return `the bar didn't ease within loading mods: ${first}% then ${later}%`;
      if ((await page.locator(".launch-button.is-playing").count()) !== 0) return "PLAYING before the game's window";
      return (await page.getByRole("button", { name: "Stop" }).count()) === 1 ? null : "no Stop while the game loads";
    },
  },
  {
    name: "minimise-on-start",
    shows: "PLAYING",
    reach: launchGame,
    verify: async (page) => ((await windowDid(page)) === "minimise" ? null : "ash did not minimise once the game started"),
  },
  {
    name: "close-on-start",
    shows: "PLAYING",
    reach: launchGame,
    verify: async (page) => ((await windowDid(page)) === "close" ? null : "ash did not close once the game started"),
  },
  {
    // Closing the game window is a clean quit, and LAUNCH GAME comes back for it.
    name: "game-closed",
    shows: "LAUNCH GAME",
    verify: async (page) => {
      await page.waitForTimeout(4000);
      return (await page.locator(".launch-button.is-playing").count()) === 0 ? null : "still PLAYING after the game closed";
    },
  },
  {
    name: "download-cancelled",
    shows: "LAUNCH GAME",
    reach: async (page) => {
      await launchGame(page);
      await page.getByText("DOWNLOADING").waitFor();
      await page.getByRole("button", { name: "Cancel", exact: true }).click();
    },
    verify: async (page) => ((await page.getByText("DOWNLOADING").count()) === 0 ? null : "still DOWNLOADING after Cancel"),
  },
  {
    name: "crashed",
    shows: "OutOfMemoryError",
    // The player's own mods were off, so the crash is not pinned on them.
    verify: async (page) =>
      (await page.getByRole("button", { name: "Play without them" }).count()) === 0 ? null : "offered to play without mods that were off",
  },
  {
    name: "crashed-with-mods",
    shows: "one of them is the likely cause: Sodium.",
    verify: async (page) =>
      (await page.getByRole("button", { name: "Play without them" }).count()) === 1 ? null : "no way to play without them",
  },
  {
    name: "crashed-play-without-mods",
    shows: "DOWNLOADING",
    reach: (page) => page.getByRole("button", { name: "Play without them" }).click(),
    verify: async (page) =>
      (await page.evaluate(() => (window as unknown as { withoutMods?: boolean }).withoutMods)) ? null : "it launched with the mods on",
  },
  { name: "instances-with-mods", shows: "ash client · your mods" },
  {
    name: "instance-page-mods",
    shows: "Open mods folder",
    reach: openInstance,
    verify: async (page) =>
      (await page.getByRole("switch", { name: "Your mods" }).getAttribute("aria-checked")) === "true" ? null : "the instance's mods do not show as on",
  },
  {
    name: "unchecked",
    shows: "ash could not reach Mojang to check this instance.",
    // Nothing was launched, so nothing failed: no card over the launcher.
    verify: async (page) => ((await page.locator(".failure-scrim").count()) === 0 ? null : "a failed check opened the failure card"),
  },
  {
    name: "download-only",
    shows: "DOWNLOADING",
    reach: async (page) => {
      await page.getByRole("button", { name: "1.21.11 settings" }).click();
      await page.getByRole("button", { name: /Download only/ }).click();
      await page.getByRole("button", { name: "Play", exact: true }).first().click();
    },
    // The scene answers a launch, not files fetched ahead of time.
    verify: async (page) => ((await page.locator(".launch-area.is-launching").count()) === 0 ? null : "the scene reacted to Download only"),
  },
  { name: "degraded", shows: "Hit indicator did not load last time." },
  { name: "instance-page", shows: "Window size", reach: openInstance },
  {
    name: "instance-memory",
    shows: "Saved",
    reach: async (page) => {
      await openInstance(page);
      await page.getByRole("radio", { name: "Custom" }).click();
    },
    verify: async (page) => ((await page.locator('input[type="range"]').count()) === 1 ? null : "Custom memory showed no slider"),
  },
  {
    name: "instance-refused",
    shows: "Window size must be between",
    reach: async (page) => {
      await openInstance(page);
      await page.getByLabel("Window width").fill("100");
      await page.getByLabel("Window height").fill("100");
    },
  },
  {
    // A refused value must not ride along with another row's save.
    name: "instance-refused-then-memory",
    shows: "Saved",
    reach: async (page) => {
      await openInstance(page);
      await page.getByLabel("Window width").fill("100");
      await page.getByLabel("Window height").fill("100");
      await page.getByText("Window size must be between").waitFor();
      await page.getByRole("radio", { name: "Custom" }).click();
    },
    verify: async (page) =>
      (await page.locator('input[type="range"]').count()) === 1 &&
      (await page.getByText("Window size must be between").count()) === 1
        ? null
        : "the memory save carried the refused window size",
  },
  {
    name: "instance-delete",
    shows: "cannot be undone",
    reach: async (page) => {
      await openInstance(page);
      await page.getByRole("button", { name: "Delete instance" }).click();
    },
  },
  {
    name: "instance-deleted",
    shows: "LAUNCH GAME",
    // Confirmed on the page, and back to Play.
    reach: async (page) => {
      await openInstance(page);
      await page.getByRole("button", { name: "Delete instance" }).click();
      await page.getByRole("button", { name: "Delete", exact: true }).click();
    },
    verify: async (page) => ((await page.locator(".instance-page").count()) === 0 ? null : "still on the instance page after deleting"),
  },
  { name: "new-instance", shows: "Built for", reach: (page) => page.getByRole("button", { name: "New", exact: true }).click() },
  {
    name: "news",
    shows: "ash 0.2: your ash account",
    reach: openPage("News"),
    verify: async (page) => {
      if ((await page.locator(".news-body strong", { hasText: "ash accounts" }).count()) !== 1) return "bold was not drawn";
      if ((await page.locator(".news-body script").count()) !== 0) return "a post's <script> became markup";
      if ((await page.getByText('<script>alert("x")</script>', { exact: false }).count()) !== 1) return "a post's <script> text vanished";
      await page.getByRole("link", { name: "privacy statement" }).click();
      const opened = await page.evaluate(() => (window as unknown as { openedLink?: string }).openedLink);
      return opened === "https://ashlauncher.com/privacy.html" ? null : `the link opened ${opened}`;
    },
  },
  {
    name: "news-filtered",
    shows: "Synced settings are next",
    reach: async (page) => {
      await openPage("News")(page);
      await page.getByRole("radio", { name: "News", exact: true }).click();
    },
    verify: async (page) =>
      (await page.getByText("ash 0.2: your ash account").count()) === 0 ? null : "patch notes showed under News",
  },
  { name: "news-empty", shows: "No news yet.", reach: openPage("News") },
  { name: "news-offline", shows: "this is the news ash last saw", reach: openPage("News") },
  {
    name: "news-dot",
    shows: "LAUNCH GAME",
    verify: async (page) => {
      if ((await page.locator(".nav-dot").count()) !== 1) return "no dot on News";
      await openPage("News")(page);
      return (await page.locator(".nav-dot").count()) === 0 ? null : "the dot stayed after opening News";
    },
  },
  { name: "settings", shows: "When the game starts", reach: openPage("Settings") },
  {
    name: "wardrobe",
    shows: "Only ash players see it.",
    reach: openPage("Wardrobe"),
    verify: async (page) => {
      if ((await page.getByRole("radio", { name: "Ember" }).getAttribute("aria-checked")) !== "true") return "Ember not shown as worn";
      await page.getByRole("radio", { name: "Midnight" }).click();
      await page.waitForTimeout(200);
      return (await page.getByRole("radio", { name: "Midnight" }).getAttribute("aria-checked")) === "true"
        ? null
        : "choosing Midnight didn't wear it";
    },
  },
  { name: "wardrobe-none", shows: "Only ash players see it.", reach: openPage("Wardrobe") },
  {
    name: "wardrobe-signed-out",
    shows: "ash couldn't sign you in to ash just now",
    reach: openPage("Wardrobe"),
    verify: async (page) => ((await page.getByRole("radio", { name: "Ember" }).isDisabled()) ? null : "a cape could be chosen without ash"),
  },
  // Mods from Modrinth (ADR-0026).
  {
    name: "mods",
    shows: "Mod Menu",
    reach: browseMods,
    verify: async (page) => {
      await page.getByLabel("Search mods").fill("iri");
      await page.waitForTimeout(600);
      return (await page.getByText("Sodium", { exact: true }).count()) === 0 ? null : "the search didn't narrow the list";
    },
  },
  {
    name: "mods-fabric-api",
    shows: "Included with ash",
    reach: async (page) => {
      await browseMods(page);
      await page.getByRole("radio", { name: "Most downloaded" }).click();
    },
    verify: async (page) =>
      (await page.getByRole("button", { name: "Fabric API comes with ash" }).isDisabled()) ? null : "Fabric API offered an Install",
  },
  {
    // ash's picks first; typing switches to Best match, clearing switches back.
    name: "mods-sorts",
    shows: "Picked by ash",
    reach: browseMods,
    verify: async (page) => {
      const checked = (name: string) => page.getByRole("radio", { name }).getAttribute("aria-checked");
      if ((await checked("Suggested by ash")) !== "true") return "Suggested isn't the first view";
      if ((await page.getByText("Fabric API", { exact: true }).count()) !== 0) return "Suggested shows more than ash's picks";
      await page.getByLabel("Search mods").fill("so");
      if ((await checked("Best match")) !== "true") return "typing didn't switch to Best match";
      await page.getByLabel("Search mods").fill("");
      if ((await checked("Suggested by ash")) !== "true") return "clearing didn't go back to Suggested";
      return (await page.getByRole("radio", { name: "Best match" }).count()) === 0 ? null : "Best match shows with nothing typed";
    },
  },
  {
    name: "mods-installed",
    shows: "Your mods are now on for",
    reach: async (page) => {
      await browseMods(page);
      await page.getByRole("button", { name: "Install Iris Shaders" }).click();
    },
    verify: async (page) => {
      if ((await page.getByText("Installed Iris Shaders and Sodium.").count()) !== 1) return "didn't say what it installed";
      if (!(await page.getByRole("button", { name: "Sodium installed" }).isDisabled())) return "the dependency still offers Install";
      return (await page.getByRole("button", { name: "Iris Shaders installed" }).isDisabled()) ? null : "Install still offered";
    },
  },
  {
    name: "mods-incompatible",
    shows: "Remove OptiFabric first",
    reach: async (page) => {
      await browseMods(page);
      await page.getByRole("button", { name: "Install Iris Shaders" }).click();
    },
  },
  { name: "mods-empty", shows: "No mods in 1.21.11 yet.", reach: openPage("Mods") },
  {
    name: "mods-list",
    shows: "Does what ash’s Freelook does.",
    reach: openPage("Mods"),
    verify: async (page) => {
      if ((await page.getByText("added by hand").count()) !== 1) return "a hand-added jar isn't marked";
      const handMod = page.getByRole("switch", { name: "Hand Mod on" });
      if ((await handMod.getAttribute("aria-checked")) !== "false") return "a mod that's off shows on";
      await handMod.click();
      if ((await handMod.getAttribute("aria-checked")) !== "true") return "the switch didn't turn it on";
      await page.getByRole("button", { name: "Remove Sodium" }).click();
      await page.getByRole("button", { name: "Remove", exact: true }).click();
      return (await page.getByText("Sodium", { exact: true }).count()) === 0 ? null : "Remove left it listed";
    },
  },
  {
    name: "mods-vanilla",
    shows: "Mods need a Fabric instance.",
    reach: async (page) => {
      await openPage("Mods")(page);
      await page.getByRole("radio", { name: /Vanilla/ }).click();
    },
  },
  // "Copy report" (spec 0005): shown in full before it's copied.
  {
    name: "crashed-report",
    shows: "Windows 11 Pro 24H2",
    reach: (page) => page.getByRole("button", { name: "Copy report" }).click(),
    verify: async (page) => {
      if ((await page.getByText("OutOfMemoryError").count()) < 1) return "the game's output isn't in the report";
      if ((await page.getByText("can include server addresses").count()) !== 1) return "no word on what the game's output can hold";
      await page.keyboard.press("Escape");
      return (await page.getByText("The game crashed").count()) === 1 ? null : "Escape closed the crash panel too";
    },
  },
  // The alpha (spec 0005): the tag, the note, "Send feedback", the version.
  {
    name: "alpha-note",
    shows: "This is an alpha: things may break.",
    verify: async (page) => {
      if ((await page.locator(".alpha-tag").count()) !== 1) return "no alpha tag by the logo";
      await page.locator(".ash-account-notice").getByRole("button", { name: "Send feedback" }).click();
      const opened = await page.evaluate(() => (window as unknown as { openedFeedback?: boolean }).openedFeedback);
      if (!opened) return "Send feedback opened nothing";
      await page.getByRole("button", { name: "OK", exact: true }).click();
      return (await page.getByText("This is an alpha").count()) === 0 ? null : "OK didn't dismiss the note";
    },
  },
  {
    name: "feedback-in-sidebar",
    shows: "LAUNCH GAME",
    verify: async (page) =>
      (await page.locator("nav.sidebar").getByRole("button", { name: "Send feedback" }).count()) === 1
        ? null
        : "no Send feedback in the sidebar",
  },
  {
    name: "no-feedback-link",
    shows: "LAUNCH GAME",
    verify: async (page) =>
      (await page.getByRole("button", { name: "Send feedback" }).count()) === 0 ? null : "a feedback link with nowhere to go",
  },
  { name: "settings-version", shows: "ash 0.1.0-alpha.1", reach: openPage("Settings") },
  // Updates (ADR-0025): offered with what's new, never while a game runs.
  {
    name: "update-available",
    shows: "ash 0.1.0-alpha.2 is ready.",
    verify: async (page) => {
      await page.getByRole("button", { name: "What's new" }).click();
      return (await page.getByText("search Modrinth from the Mods page").count()) === 1 ? null : "what's new didn't open";
    },
  },
  {
    name: "update-installing",
    shows: "Downloading… 40%",
    reach: (page) => page.getByRole("button", { name: "Update", exact: true }).click(),
  },
  {
    name: "update-failed",
    shows: "the download was interrupted",
    reach: (page) => page.getByRole("button", { name: "Update", exact: true }).click(),
  },
  {
    name: "update-blocked",
    shows: "Close the game to update",
    verify: async (page) =>
      (await page.getByRole("button", { name: "Update", exact: true }).isDisabled()) ? null : "Update could start mid-game",
  },
  {
    name: "update-later",
    shows: "LAUNCH GAME",
    reach: (page) => page.getByRole("button", { name: "Later" }).click(),
    verify: async (page) => ((await page.getByText("is ready.").count()) === 0 ? null : "Later didn't hide the bar"),
  },
  {
    // Not signed in on opening; Try again signs in, and the capes can be chosen.
    name: "wardrobe-try-again",
    shows: "ash couldn't sign you in to ash just now",
    reach: openPage("Wardrobe"),
    verify: async (page) => {
      await page.getByRole("button", { name: "Try again" }).click();
      await page.waitForTimeout(300);
      if ((await page.getByText("ash couldn't sign you in").count()) !== 0) return "still not signed in after Try again";
      return (await page.getByRole("radio", { name: "Ember" }).isDisabled()) ? "the capes stayed disabled" : null;
    },
  },
  {
    // LAUNCH GAME takes the first click, even while the files are still being
    // checked - and the check finishing afterwards doesn't undo the launch.
    name: "launch-during-check",
    shows: "DOWNLOADING",
    reach: (page) => page.locator(".launch-button").click(),
    verify: async (page) => {
      await page.waitForTimeout(3000);
      return (await page.getByText("LAUNCH GAME").count()) === 0 ? null : "the check finishing put LAUNCH GAME back mid-launch";
    },
  },
  { name: "sync-on", shows: "On. Synced 2 minutes ago.", reach: openPage("Settings") },
  {
    name: "sync-off",
    shows: "Off on this computer.",
    reach: openPage("Settings"),
    verify: async (page) =>
      (await page.getByRole("switch", { name: "Sync between computers" }).getAttribute("aria-checked")) === "false"
        ? null
        : "the switch shows on",
  },
  { name: "sync-unreachable", shows: "ash's servers can't be reached right now. ash will try again.", reach: openPage("Settings") },
  {
    name: "sync-deleted-elsewhere",
    shows: "was deleted on another computer",
    verify: async (page) => {
      await page.getByRole("button", { name: "Keep here" }).click();
      return (await page.getByText("was deleted on another computer").count()) === 0 ? null : "the question stayed after Keep here";
    },
  },
  // The ash account (spec 0004): a notice once, a quiet line in Settings, and deleting it.
  { name: "ash-notice", shows: "ash made an ash account for Steve." },
  {
    name: "ash-notice-dismissed",
    shows: "ash made an ash account for Steve.",
    verify: async (page) => {
      await page.getByRole("button", { name: "Privacy statement" }).click();
      const opened = await page.evaluate(() => (window as unknown as { openedPrivacy?: boolean }).openedPrivacy);
      if (!opened) return "Privacy statement opened nothing";
      await page.getByRole("button", { name: "OK", exact: true }).click();
      return (await page.getByText("ash made an ash account").count()) === 0 ? null : "OK did not dismiss the notice";
    },
  },
  { name: "ash-signed-in", shows: "Signed in to ash as Steve.", reach: openPage("Settings") },
  { name: "ash-unreachable", shows: "ash's servers can't be reached right now.", reach: openPage("Settings") },
  { name: "ash-refused", shows: "Multiplayer is turned off for this Microsoft account", reach: openPage("Settings") },
  {
    name: "ash-delete",
    shows: "This deletes Steve",
    reach: async (page) => {
      await openPage("Settings")(page);
      await page.getByRole("button", { name: "Delete ash account" }).click();
    },
  },
  {
    name: "ash-deleted",
    shows: "You deleted your ash account.",
    reach: async (page) => {
      await openPage("Settings")(page);
      await page.getByRole("button", { name: "Delete ash account" }).click();
      await page.getByRole("button", { name: "Delete", exact: true }).click();
    },
    verify: async (page) =>
      (await page.getByRole("button", { name: "Delete ash account" }).count()) === 0 ? null : "still offers to delete a deleted account",
  },
  {
    name: "ash-delete-failed",
    shows: "nothing was deleted",
    reach: async (page) => {
      await openPage("Settings")(page);
      await page.getByRole("button", { name: "Delete ash account" }).click();
      await page.getByRole("button", { name: "Delete", exact: true }).click();
    },
    verify: async (page) =>
      (await page.getByText("You deleted your ash account.").count()) === 0 ? null : "showed a failed deletion as done",
  },
  // Where the tour's card sits: below LAUNCH GAME, and beside the sidebar.
  { name: "tour-start", shows: "1 of 7" },
  {
    name: "tour-mods",
    shows: "3 of 7",
    reach: async (page) => {
      await page.getByText("1 of 7").waitFor();
      await page.getByRole("button", { name: "Next" }).click();
      await page.getByRole("button", { name: "Next" }).click();
    },
  },
  {
    // The tour again, whenever it's wanted: from Settings, back on Play.
    name: "tour-replay",
    shows: "1 of 7",
    reach: async (page) => {
      await openPage("Settings")(page);
      await page.getByRole("button", { name: "Show the tour" }).click();
    },
    verify: async (page) => {
      await page.keyboard.press("Escape");
      return (await page.locator(".tour").count()) === 0 ? null : "Escape didn't end the tour";
    },
  },
  {
    name: "settings-memory",
    shows: "6 GB",
    reach: openPage("Settings"),
    verify: async (page) => ((await page.locator('input[type="range"]').count()) === 1 ? null : "a custom default memory showed no slider"),
  },
];

/** The bundled weights, by the names their files give them. */
const BUNDLED = /^Inter( Medium| SemiBold| Bold| ExtraBold)?$/;

/**
 * Every face the renderer drew text in, with how many glyphs each drew. A
 * fallback face shows up here by name even when the stylesheet says Inter.
 */
async function facesDrawn(page: Page): Promise<Map<string, number>> {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send("DOM.enable");
  await cdp.send("CSS.enable");
  const { root } = await cdp.send("DOM.getDocument", { depth: -1 });
  const { nodeIds } = await cdp.send("DOM.querySelectorAll", { nodeId: root.nodeId, selector: "body *" });
  const faces = new Map<string, number>();
  for (const nodeId of nodeIds) {
    const { fonts } = await cdp.send("CSS.getPlatformFontsForNode", { nodeId });
    for (const font of fonts) {
      const name = font.isCustomFont ? font.familyName : `${font.familyName} (installed on this machine)`;
      faces.set(name, (faces.get(name) ?? 0) + font.glyphCount);
    }
  }
  await cdp.detach();
  return faces;
}

async function check(page: Page, base: string, state: State): Promise<string[]> {
  const problems: string[] = [];
  page.on("pageerror", (error) => problems.push(`threw: ${error.message}`));
  page.on("console", (message) => {
    if (message.type() === "error") problems.push(`logged an error: ${message.text()}`);
  });

  await page.goto(`${base}?state=${state.name}`);
  try {
    await state.reach?.(page);
    await page.getByText(state.shows).first().waitFor({ timeout: 5000 });
  } catch {
    problems.push(`never showed "${state.shows}"`);
  }
  const verified = await state.verify?.(page);
  if (verified) problems.push(verified);
  await page.evaluate(() => document.fonts.ready);

  const faces = await facesDrawn(page);
  const glyphs = [...faces.values()].reduce((a, b) => a + b, 0);
  // The check proves nothing about the face if no text was drawn at all.
  if (glyphs === 0) problems.push("drew no text, so its face could not be checked");
  for (const [face, count] of faces) {
    if (!BUNDLED.test(face) && count > 0) problems.push(`drew ${count} glyphs in ${face}, not Inter`);
  }

  await page.screenshot({ path: path.join(screenshots, `${state.name}.png`) });
  return problems;
}

await build({ configFile });
const server = await preview({ configFile });
const base = server.resolvedUrls?.local[0];
if (!base) throw new Error("The preview server gave no address.");

mkdirSync(screenshots, { recursive: true });
const browser = await chromium.launch();
let failed = 0;
try {
  for (const state of STATES) {
    const context = await browser.newContext({
      viewport: { width: 1000, height: 660 },
      deviceScaleFactor: 1,
      reducedMotion: "reduce",
    });
    // Counts the audio contexts the page makes, for the launch sound checks.
    await context.addInitScript(() => {
      const Real = window.AudioContext;
      const counted = window as unknown as { audioContexts?: number };
      window.AudioContext = class extends Real {
        constructor(options?: AudioContextOptions) {
          super(options);
          counted.audioContexts = (counted.audioContexts ?? 0) + 1;
        }
      };
    });
    const problems = await check(await context.newPage(), base, state);
    await context.close();
    if (problems.length === 0) {
      console.log(`ok    ${state.name}`);
    } else {
      failed += 1;
      console.log(`FAIL  ${state.name}`);
      for (const problem of problems) console.log(`        ${problem}`);
    }
  }
} finally {
  await browser.close();
  await server.close();
}

console.log(`\nScreenshots: ${screenshots}`);
if (failed > 0) {
  console.log(`${failed} of ${STATES.length} states failed.`);
  process.exit(1);
}
