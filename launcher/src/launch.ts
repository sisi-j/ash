import { useCallback, useEffect, useRef, useState } from "react";
import {
  api,
  onLaunchFinished,
  onPrepareFinished,
  onPrepareProgress,
  type DegradationNotice,
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
  | { at: "running" }
  | { at: "crashed"; code: number | null; log: string[] }
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
        setPhase({ at: "running" });
        return;
      }
      setPhase({ at: "idle", plan: await api.planInstance(id) });
    } catch (e) {
      setPhase({ at: "unchecked", error: e as UiError });
    }
  }, [id]);

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
        void replan();
        return;
      }
      const log = await api.gameLog(id).catch(() => []);
      if (live) setPhase({ at: "crashed", code: status.code, log });
    };

    const timer = setInterval(() => void tick(), POLL_MS);
    return () => {
      live = false;
      clearInterval(timer);
    };
  }, [phase.at, id, replan]);

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
      if (outcome.ok || outcome.error?.kind === "cancelled") void replan();
      else if (outcome.error) setPhase({ at: "failed", error: outcome.error, goal: "prepare" });
    });

    const launched = onLaunchFinished((outcome) => {
      if (outcome.ok) {
        setCommand(outcome.invocation);
        setPhase({ at: "running" });
      } else if (outcome.error) {
        if (outcome.error.kind === "cancelled") void replan();
        else setPhase({ at: "failed", error: outcome.error, goal: "play" });
      }
    });

    return () => {
      void progress.then((un) => un());
      void prepared.then((un) => un());
      void launched.then((un) => un());
    };
  }, [replan]);

  const start = useCallback(
    (what: Goal) => {
      if (id === null) return;
      goal.current = what;
      setCommand(null);
      setPhase({ at: "working", goal: what, progress: START });
      const call = what === "play" ? api.launch(id) : api.prepareInstance(id);
      call.catch((e) => setPhase({ at: "failed", error: e as UiError, goal: what }));
    },
    [id],
  );

  return {
    phase,
    notice,
    runningAs: command ? argumentAfter(command.args, "--username") : null,
    start,
    cancel: () => id !== null && void api.cancelPreparation(id),
    stop: () => id !== null && void api.stopGame(id),
    dismiss: () => void replan(),
  };
}
