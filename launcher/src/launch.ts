import { useCallback, useEffect, useRef, useState } from "react";
import {
  api,
  onLaunchFinished,
  onPrepareFinished,
  onPrepareProgress,
  type DegradationNotice,
  type GameBoot,
  type InstanceId,
  type InvocationView,
  type Plan,
  type UiError,
} from "./api";

/**
 * The step a launch is on, as LAUNCH GAME names it. Read off the real
 * preparation events, never timed: a player whose files are all in the depot
 * goes straight from checking to starting.
 */
export type Stage = "checking" | "downloading" | "starting";

export type Progress = {
  stage: Stage;
  /** What is downloading: the game's files, or the Java runtime first. */
  fetching: "game" | "java";
  totalFiles: number;
  doneFiles: number;
  doneBytes: number;
  missingBytes: number;
  alreadyPresent: number;
  note: string | null;
};

/** Playing, or only fetching the files ahead of time ("Download only"). */
export type Goal = "prepare" | "play";

export type Phase =
  | { at: "checking" }
  /** ash could not say what the instance needs: nothing was launched, so nothing failed. */
  | { at: "unchecked"; error: UiError }
  | { at: "idle"; plan: Plan }
  | { at: "working"; progress: Progress; goal: Goal }
  /**
   * The game's process is up. `boot` is how far it has got towards its
   * title screen; "ready" is PLAYING.
   */
  | { at: "running"; boot: GameBoot }
  | {
      at: "crashed";
      code: number | null;
      log: string[];
      /**
       * The player's own mods, when the session ran with them on: the likely
       * cause, named. Empty when they were on but none can be named; null
       * when they were off.
       */
      mods: string[] | null;
    }
  | { at: "failed"; error: UiError; goal: Goal };

const START: Progress = {
  stage: "checking",
  fetching: "game",
  totalFiles: 0,
  doneFiles: 0,
  doneBytes: 0,
  missingBytes: 0,
  alreadyPresent: 0,
  note: null,
};

/** How often a running game is asked whether it is still running. */
const POLL_MS = 1500;

/** How often a loading game's output is read for the step it is on. */
const BOOT_POLL_MS = 300;

/**
 * A game that never says its title screen is up (a log ash can't read, a
 * client too old to say) is still up: after this long, PLAYING rather than
 * loading for ever.
 */
const BOOT_GIVE_UP_MS = 180_000;

const READY: GameBoot = { stage: "ready", mods: null };

/** The argument after `--flag`, for reading a value back off a command line. */
function argumentAfter(args: string[], flag: string): string | null {
  const at = args.indexOf(flag);
  return at >= 0 ? (args[at + 1] ?? null) : null;
}

export type Launch = {
  phase: Phase;
  /** What the client said about the last session, while it is worth saying. */
  notice: DegradationNotice | null;
  /**
   * Who is actually playing, read off the command line ash ran rather than
   * from whoever is selected now: switching accounts mid-session must not
   * relabel a game that is already up as someone else.
   */
  runningAs: string | null;
  start: (what: Goal) => void;
  /** Play with the player's own mods left out, this once: what a crash with them on offers. */
  playWithoutMods: () => void;
  /** Play, straight into one of the instance's servers. */
  join: (address: string) => void;
  /** Do again what just failed: the same goal, into the same server if there was one. */
  retry: () => void;
  cancel: () => void;
  stop: () => void;
  /** Check the instance again: after a failure or a crash has been read, or a check that failed. */
  dismiss: () => void;
};

/**
 * Playing one instance: preparing what it needs, starting it, and what
 * happened when it stopped.
 *
 * Held once, by the app, for the selected instance, so the LAUNCH area and
 * the instance's own page show the same launch rather than two copies of it.
 */
/** A phase a finished check may replace: nothing is under way in it. */
function settled(phase: Phase): boolean {
  return phase.at !== "working" && phase.at !== "running";
}

/**
 * Whether LAUNCH GAME takes a click: not only once the files are checked,
 * but while they are being checked and after a check that failed. A launch
 * checks them itself, so there is nothing to wait for - and a button that
 * ignores the first click is the slowest thing a launcher can do.
 */
export function canStart(phase: Phase): boolean {
  return phase.at === "idle" || phase.at === "checking" || phase.at === "unchecked";
}

export function useLaunch(id: InstanceId | null): Launch {
  const [phase, setPhase] = useState<Phase>({ at: "checking" });
  const [command, setCommand] = useState<InvocationView | null>(null);
  const [notice, setNotice] = useState<DegradationNotice | null>(null);
  const goal = useRef<Goal>("play");

  const replan = useCallback(async () => {
    if (id === null) return;
    try {
      // A game already running for this instance outranks anything the file
      // plan has to say: ash may have been reopened while it played.
      const status = await api.gameStatus(id);
      if (status?.state === "running") {
        const boot = (await api.gameBoot(id).catch(() => null)) ?? READY;
        setPhase({ at: "running", boot });
        return;
      }
      const plan = await api.planInstance(id);
      // Only if nothing has started meanwhile: a click during the check began
      // a launch, and the check finishing must not put LAUNCH GAME back.
      setPhase((current) => (settled(current) ? { at: "idle", plan } : current));
    } catch (e) {
      setPhase((current) => (settled(current) ? { at: "unchecked", error: e as UiError } : current));
    }
  }, [id]);

  // What was under way has ended - the game closed, a download finished or
  // was cancelled - so the check that follows may put LAUNCH GAME back.
  // Straight to "checking", which already takes a click: no wait for it.
  const settle = useCallback(() => {
    setPhase({ at: "checking" });
    void replan();
  }, [replan]);

  useEffect(() => {
    setPhase({ at: "checking" });
    setCommand(null);
    // Another instance's notice must not stand on this one's screen while
    // this one's is fetched.
    setNotice(null);
    void replan();
  }, [id, replan]);

  // What the client said about the last session. Asked again whenever the
  // player is about to play - including straight after a game closes, which
  // is when the client has just written a fresh report. Never in the way:
  // a notice that cannot be read is no notice, and Play still works.
  const beforePlay =
    phase.at === "idle" || phase.at === "unchecked" || phase.at === "crashed" || phase.at === "failed";
  useEffect(() => {
    if (!beforePlay || id === null) return;
    let live = true;
    void api
      .degradationNotice(id)
      .catch(() => null)
      .then((found) => {
        if (live) setNotice(found);
      });
    return () => {
      live = false;
    };
  }, [beforePlay, id]);

  // Poll only while something is actually running. A clean exit goes
  // straight back to LAUNCH GAME; only a crash has anything to say.
  useEffect(() => {
    if (phase.at !== "running" || id === null) return;
    let live = true;

    const tick = async () => {
      const status = await api.gameStatus(id).catch(() => null);
      if (!live || !status || status.state === "running") return;
      if (status.clean) {
        settle();
        return;
      }
      const log = await api.gameLog(id).catch(() => []);
      const mods = withoutMods.current ? null : await playersMods(id);
      if (live) setPhase({ at: "crashed", code: status.code, log, mods });
    };

    const timer = setInterval(() => void tick(), POLL_MS);
    return () => {
      live = false;
      clearInterval(timer);
    };
  }, [phase.at, id, settle]);

  // While the game loads, its output says which step it is on, for the
  // LAUNCH bar's last stretch.
  const booting = phase.at === "running" && phase.boot.stage !== "ready";
  useEffect(() => {
    if (!booting || id === null) return;
    let live = true;
    const since = Date.now();

    const tick = async () => {
      const boot = await api.gameBoot(id).catch(() => null);
      if (!live) return;
      const gaveUp = Date.now() - since > BOOT_GIVE_UP_MS;
      setPhase((current) => {
        if (current.at !== "running") return current;
        if (gaveUp) return { at: "running", boot: { ...current.boot, stage: "ready" } };
        if (!boot || (boot.stage === current.boot.stage && boot.mods === current.boot.mods)) return current;
        return { at: "running", boot };
      });
    };

    const timer = setInterval(() => void tick(), BOOT_POLL_MS);
    return () => {
      live = false;
      clearInterval(timer);
    };
  }, [booting, id]);

  useEffect(() => {
    const progress = onPrepareProgress((event) => {
      setPhase((current) => {
        if (current.at !== "working") return current;
        const p = current.progress;
        const keep = (next: Partial<Progress>): Phase => ({
          at: "working",
          goal: current.goal,
          progress: { ...p, ...next },
        });

        switch (event.event) {
          case "resolving":
            return keep({ stage: "checking", note: "Reading version metadata…" });
          case "planned":
            return keep({
              stage: event.missing_files > 0 ? "downloading" : p.stage,
              totalFiles: event.missing_files,
              missingBytes: event.missing_bytes,
              alreadyPresent: event.already_present,
              note: null,
            });
          case "downloaded":
            return keep({
              stage: "downloading",
              doneFiles: event.done_files,
              doneBytes: event.done_bytes,
              note: null,
            });
          case "runtime":
            // A second `planned` follows with the runtime's own counts, so
            // reset rather than carrying the game-file totals forward.
            return {
              at: "working",
              goal: current.goal,
              progress: {
                ...START,
                stage: "downloading",
                fetching: "java",
                note: `Fetching the Java runtime (${event.component})…`,
              },
            };
          case "resuming":
            return keep({ note: "Resuming an interrupted file…" });
          case "reverifying":
            return keep({ note: "A file arrived corrupt; retrying…" });
          case "done":
            return keep({ stage: "starting", note: null });
          default:
            return current;
        }
      });
    });

    // Preparing on its own finishes here; launching carries on past it, so
    // this only settles the phase when nothing is going to be started.
    const prepared = onPrepareFinished((outcome) => {
      if (goal.current === "play") return;
      if (outcome.ok || outcome.error?.kind === "cancelled") settle();
      else if (outcome.error) setPhase({ at: "failed", error: outcome.error, goal: "prepare" });
    });

    const launched = onLaunchFinished((outcome) => {
      if (outcome.ok) {
        setCommand(outcome.invocation);
        setPhase({ at: "running", boot: { stage: "starting", mods: null } });
      } else if (outcome.error) {
        if (outcome.error.kind === "cancelled") settle();
        else setPhase({ at: "failed", error: outcome.error, goal: "play" });
      }
    });

    return () => {
      void progress.then((un) => un());
      void prepared.then((un) => un());
      void launched.then((un) => un());
    };
  }, [settle]);

  // The server the last play was going into, so trying again goes there too.
  const server = useRef<string | null>(null);
  // Whether the last play left the player's own mods out, which a crash
  // then cannot blame on them, and which trying again keeps doing.
  const withoutMods = useRef(false);

  const begin = useCallback(
    (what: Goal, into: string | null, leaveModsOut = false) => {
      if (id === null) return;
      goal.current = what;
      server.current = into;
      withoutMods.current = leaveModsOut;
      setCommand(null);
      setPhase({ at: "working", goal: what, progress: START });
      const call =
        what === "prepare"
          ? api.prepareInstance(id)
          : leaveModsOut
            ? api.launchWithoutThirdPartyMods(id)
            : into
              ? api.join(id, into)
              : api.launch(id);
      call.catch((e) => setPhase({ at: "failed", error: e as UiError, goal: what }));
    },
    [id],
  );

  return {
    phase,
    notice,
    runningAs: command ? argumentAfter(command.args, "--username") : null,
    start: (what) => begin(what, null),
    join: (address) => begin("play", address),
    playWithoutMods: () => begin("play", null, true),
    retry: () => begin(phase.at === "failed" ? phase.goal : "play", server.current, withoutMods.current),
    cancel: () => id !== null && void api.cancelPreparation(id),
    stop: () => id !== null && void api.stopGame(id),
    dismiss: () => void replan(),
  };
}

/**
 * The player's own mods, if the instance has them on: named, for a crash to
 * point at. Null when they are off, or when ash cannot tell.
 */
async function playersMods(id: InstanceId): Promise<string[] | null> {
  try {
    const overrides = await api.overrides(id);
    if (!overrides.third_party_mods) return null;
    return (await api.instanceGlance(id)).mods;
  } catch {
    return null;
  }
}
