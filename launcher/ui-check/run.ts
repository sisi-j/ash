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
const launchGame = (page: Page) => page.locator('.launch-button[aria-disabled="false"]').click();
const openInstance = (page: Page) => page.getByRole("button", { name: "1.21.11 settings" }).click();
const openPage = (label: string) => (page: Page) => page.getByRole("navigation").getByRole("button", { name: label }).click();

/** What the launcher did to its own window, if anything: minimise or close. */
const windowDid = (page: Page) => page.evaluate(() => (window as unknown as { windowDid?: string }).windowDid);

/** How many audio contexts the page made: one the first time a sound plays, none if none ever does. */
const soundsMade = (page: Page) => page.evaluate(() => (window as unknown as { audioContexts?: number }).audioContexts ?? 0);

const STATES: State[] = [
  { name: "signed-out", shows: "Sign in with Microsoft" },
  { name: "no-instances", shows: "No instances yet" },
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
  { name: "mods", shows: "Mods are coming soon", reach: openPage("Mods") },
  { name: "news", shows: "News is coming soon", reach: openPage("News") },
  { name: "settings", shows: "When the game starts", reach: openPage("Settings") },
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
