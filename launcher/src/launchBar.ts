import { useEffect, useRef, useState } from "react";
import type { InstanceId } from "./api";
import type { Phase, Progress } from "./launch";

/**
 * The LAUNCH bar, from the click to the game's window opening, where the
 * launch is done: the rest of the loading shows in the game's own window.
 *
 * Each step owns a stretch of the bar. Downloading moves with the bytes; the
 * other steps can't be measured, so each eases along at the pace it took on
 * this instance's last launch and, if it runs long, slows to a near stop at
 * about 90% of its stretch rather than run out. When a step really ends the
 * bar moves on to the next one: the steps are the real ones, read off the
 * preparation events and the game's own output, never timed.
 */
export type Step = "checking" | "downloading" | "starting" | "loading_mods";

type Timed = Exclude<Step, "downloading">;

/** Each step's stretch of the bar, in percent. The window opening is 100. */
const STRETCH: Record<Step, [number, number]> = {
  checking: [0, 5],
  downloading: [5, 40],
  starting: [40, 55],
  loading_mods: [55, 100],
};

/** A vanilla game loads no mods, so starting Java takes their stretch too. */
const VANILLA_STARTING: [number, number] = [40, 100];

/** How long each step takes before an instance has launched once, in ms. */
const FIRST_GUESS: Record<Timed, number> = {
  checking: 1_500,
  starting: 5_000,
  loading_mods: 10_000,
};

/** How much of its stretch a step that runs long creeps towards. */
const CEILING = 0.92;
/** How quickly a step eases along: at its usual time, it's about 85% of the way. */
const EASE = 2.5;
const TICK_MS = 100;

type Pace = Partial<Record<Timed, number>>;

const paceKey = (id: InstanceId) => `ash.launch-pace.${id}`;

function readPace(id: InstanceId): Pace {
  try {
    return JSON.parse(localStorage.getItem(paceKey(id)) ?? "{}") as Pace;
  } catch {
    return {};
  }
}

function savePace(id: InstanceId, pace: Pace) {
  try {
    localStorage.setItem(paceKey(id), JSON.stringify({ ...readPace(id), ...pace }));
  } catch {
    // Only a smoother bar is lost.
  }
}

/** The step a launch is on, "done" once the game's window opens, or null with no launch under way. */
export function stepOf(phase: Phase): Step | "done" | null {
  if (phase.at === "working") return phase.progress.stage;
  if (phase.at === "running") return phase.boot.stage === "ready" ? "done" : phase.boot.stage;
  return null;
}

function downloaded(progress: Progress): number {
  return progress.missingBytes > 0 ? Math.min(1, progress.doneBytes / progress.missingBytes) : 0;
}

/** How full the LAUNCH bar is, 0 to 100, for this instance's phase. */
export function useLaunchFill(id: InstanceId, phase: Phase, vanilla: boolean): number {
  const step = stepOf(phase);
  const prepareOnly = phase.at === "working" && phase.goal === "prepare";
  const since = useRef({ id, step, at: Date.now() });
  const timings = useRef<Pace>({});
  const pace = useRef<Pace | null>(null);
  // Whether this launch was watched from its first step. One picked up
  // partway - the Play page left and opened again - has times too short to
  // keep.
  const whole = useRef(false);
  const [now, setNow] = useState(Date.now());

  // Each step's time, kept once the game is up, for the next launch's pace.
  useEffect(() => {
    const was = since.current;
    if (id !== was.id) {
      // Another instance: its own pace, and nothing carried over.
      pace.current = null;
      whole.current = false;
    } else if (step === was.step) {
      return;
    } else if (was.step === null) {
      timings.current = {};
      pace.current = null;
      whole.current = step === "checking";
    } else if (was.step !== "done" && was.step !== "downloading") {
      timings.current[was.step] = Date.now() - was.at;
    }
    if (step === "done" && whole.current) savePace(id, timings.current);
    if (step === null || step === "done") whole.current = false;
    since.current = { id, step, at: Date.now() };
  }, [step, id]);

  const easing = step !== null && step !== "done" && step !== "downloading";
  useEffect(() => {
    if (!easing) return;
    const timer = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(timer);
  }, [easing]);

  if (step === null) return 0;
  if (step === "done") return 100;
  if (prepareOnly) {
    // Download only: the files are the whole job.
    if (phase.at !== "working") return 0;
    return step === "downloading" ? 5 + 95 * downloaded(phase.progress) : step === "checking" ? 5 : 100;
  }

  const [from, to] = step === "starting" && vanilla ? VANILLA_STARTING : STRETCH[step];
  if (step === "downloading") {
    return phase.at === "working" ? from + (to - from) * downloaded(phase.progress) : from;
  }
  pace.current ??= readPace(id);
  const usual = pace.current[step] ?? FIRST_GUESS[step];
  const elapsed = Math.max(0, now - (since.current.step === step ? since.current.at : now));
  return from + (to - from) * CEILING * (1 - Math.exp((-EASE * elapsed) / usual));
}
