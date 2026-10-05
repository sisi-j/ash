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
};

/** The instance's Play button, not the sidebar's page of the same name. */
const clickPlay = (page: Page) => page.getByRole("main").getByRole("button", { name: "Play", exact: true }).click();
const openPage = (label: string) => (page: Page) => page.getByRole("navigation").getByRole("button", { name: label }).click();

const STATES: State[] = [
  { name: "signed-out", shows: "Sign in with Microsoft" },
  { name: "no-instances", shows: "No instances yet." },
  { name: "idle", shows: "Everything this instance needs is in the depot." },
  {
    name: "account-menu",
    shows: "Add account",
    reach: (page) => page.getByRole("button", { name: "Steve" }).click(),
  },
  { name: "preparing", shows: "already in the depot", reach: clickPlay },
  { name: "playing", shows: "Running" },
  { name: "failed-launch", shows: "Java could not start the game.", reach: clickPlay },
  { name: "degraded", shows: "Hit indicator did not load last time." },
  { name: "mods", shows: "Mods are coming soon", reach: openPage("Mods") },
  { name: "news", shows: "News is coming soon", reach: openPage("News") },
  { name: "settings", shows: "Settings are coming soon", reach: openPage("Settings") },
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
