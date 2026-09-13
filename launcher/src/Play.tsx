import { useCallback, useEffect, useRef, useState } from "react";
import {
  api,
  describeBytes,
  onLaunchFinished,
  onPrepareFinished,
  onPrepareProgress,
  type Account,
  type InstanceId,
  type InvocationView,
  type Plan,
  type UiError,
} from "./api";

type Progress = {
  totalFiles: number;
  doneFiles: number;
  doneBytes: number;
  missingBytes: number;
  alreadyPresent: number;
  note: string | null;
};

type Phase =
  | { at: "checking" }
  | { at: "idle"; plan: Plan }
  | { at: "working"; progress: Progress; goal: "prepare" | "play" }
  | { at: "running" }
  | { at: "exited"; clean: boolean; code: number | null; log: string[] }
  | { at: "failed"; error: UiError };

const START: Progress = {
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
function valueOf(args: string[], flag: string): string | null {
  const at = args.indexOf(flag);
  return at >= 0 ? (args[at + 1] ?? null) : null;
}

/**
 * Playing one instance: preparing what it needs, starting it, and saying
 * what happened when it stops.
 *
 * Progress is rendered from the event stream rather than a percentage,
 * because "4,300 of 4,900 files" and "re-verifying a corrupt file" are both
 * things a player wants to see and a single number cannot say.
 */
export function Play(props: { id: InstanceId; playingAs: Account | null }) {
  const [phase, setPhase] = useState<Phase>({ at: "checking" });
  const [command, setCommand] = useState<InvocationView | null>(null);
  const goal = useRef<"prepare" | "play">("play");

  const replan = useCallback(async () => {
    try {
      // A game already running for this instance outranks anything the file
      // plan has to say: ash may have been reopened while it played.
      const status = await api.gameStatus(props.id);
      if (status?.state === "running") {
        setPhase({ at: "running" });
        return;
      }
      setPhase({ at: "idle", plan: await api.planInstance(props.id) });
    } catch (e) {
      setPhase({ at: "failed", error: e as UiError });
    }
  }, [props.id]);

  useEffect(() => {
    setPhase({ at: "checking" });
    setCommand(null);
    void replan();
  }, [props.id, replan]);

  // Poll only while something is actually running.
  useEffect(() => {
    if (phase.at !== "running") return;
    let live = true;

    const tick = async () => {
      const status = await api.gameStatus(props.id).catch(() => null);
      if (!live || !status || status.state === "running") return;
      const log = await api.gameLog(props.id).catch(() => []);
      if (!live) return;
      setPhase({ at: "exited", clean: status.clean, code: status.code, log });
    };

    const timer = setInterval(() => void tick(), POLL_MS);
    return () => {
      live = false;
      clearInterval(timer);
    };
  }, [phase.at, props.id]);

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
            return keep({ note: "Reading version metadata…" });
          case "planned":
            return keep({
              totalFiles: event.missing_files,
              missingBytes: event.missing_bytes,
              alreadyPresent: event.already_present,
              note: null,
            });
          case "downloaded":
            return keep({
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
              progress: { ...START, note: `Fetching the Java runtime (${event.component})…` },
            };
          case "resuming":
            return keep({ note: "Resuming an interrupted file…" });
          case "reverifying":
            return keep({ note: "A file arrived corrupt; retrying…" });
          default:
            return current;
        }
      });
    });

    // Preparing on its own finishes here; launching carries on past it, so
    // this only settles the phase when nothing is going to be started.
    const prepared = onPrepareFinished((outcome) => {
      if (goal.current === "play") return;
      if (outcome.ok) void replan();
      else if (outcome.error) {
        setPhase(
          outcome.error.kind === "cancelled"
            ? { at: "checking" }
            : { at: "failed", error: outcome.error },
        );
        if (outcome.error.kind === "cancelled") void replan();
      }
    });

    const launched = onLaunchFinished((outcome) => {
      if (outcome.ok) {
        setCommand(outcome.invocation);
        setPhase({ at: "running" });
      } else if (outcome.error) {
        if (outcome.error.kind === "cancelled") void replan();
        else setPhase({ at: "failed", error: outcome.error });
      }
    });

    return () => {
      void progress.then((un) => un());
      void prepared.then((un) => un());
      void launched.then((un) => un());
    };
  }, [replan]);

  const start = useCallback(
    (what: "prepare" | "play") => {
      goal.current = what;
      setCommand(null);
      setPhase({ at: "working", goal: what, progress: START });
      const call = what === "play" ? api.launch(props.id) : api.prepareInstance(props.id);
      call.catch((e) => setPhase({ at: "failed", error: e as UiError }));
    },
    [props.id],
  );

  // Who is *actually* playing, read off the command line ash ran rather than
  // from whoever is selected now. Switching accounts mid-session must not
  // relabel a game that is already up as someone else.
  const running = command ? valueOf(command.args, "--username") : null;

  if (phase.at === "checking") {
    return <p className="muted">Checking what this instance needs…</p>;
  }

  if (phase.at === "failed") {
    return (
      <>
        <p className="error" role="alert">
          {phase.error.message}
        </p>
        <div className="actions">
          {phase.error.retryable && (
            <button className="button" onClick={() => start("play")}>
              Try again
            </button>
          )}
          <button className="button" onClick={() => void replan()}>
            Back
          </button>
        </div>
      </>
    );
  }

  if (phase.at === "running") {
    return (
      <>
        <p className="muted">
          Running{running ? <> as <strong>{running}</strong></> : null}.
        </p>
        <div className="actions">
          <button className="button" onClick={() => void api.stopGame(props.id)}>
            Stop
          </button>
        </div>
        <Command invocation={command} />
      </>
    );
  }

  if (phase.at === "exited") {
    return (
      <>
        {phase.clean ? (
          <p className="muted">The game closed.</p>
        ) : (
          <p className="error" role="alert">
            The game crashed{phase.code !== null ? ` (exit code ${phase.code})` : ""}.
          </p>
        )}

        {!phase.clean && phase.log.length > 0 && (
          <>
            <h3 className="panel-title">Last output</h3>
            <pre className="log">{phase.log.slice(-40).join("\n")}</pre>
          </>
        )}

        <div className="actions">
          <button className="button" onClick={() => start("play")}>
            Play again
          </button>
        </div>
      </>
    );
  }

  if (phase.at === "idle") {
    const missing = phase.plan.missing_files;
    return (
      <>
        {props.playingAs && (
          <p className="muted playing-as">
            Playing as <strong>{props.playingAs.username}</strong>.
          </p>
        )}
        <p className="muted">
          {missing === 0 ? (
            "Everything this instance needs is in the depot."
          ) : (
            <>
              <span className="numeric">{missing}</span> files to download,{" "}
              <span className="numeric">{describeBytes(phase.plan.missing_bytes)}</span>.
            </>
          )}
        </p>
        <div className="actions">
          <button className="button" onClick={() => start("play")}>
            Play
          </button>
          {missing > 0 && (
            <button className="button" onClick={() => start("prepare")}>
              Download only
            </button>
          )}
        </div>
      </>
    );
  }

  const { progress } = phase;
  const fraction = progress.totalFiles > 0 ? progress.doneFiles / progress.totalFiles : 0;

  return (
    <>
      <div className="bar" role="progressbar" aria-valuenow={Math.round(fraction * 100)}>
        <div className="bar-fill" style={{ width: `${fraction * 100}%` }} />
      </div>

      <p className="muted">
        <span className="numeric">{progress.doneFiles}</span> of{" "}
        <span className="numeric">{progress.totalFiles}</span> files ·{" "}
        <span className="numeric">{describeBytes(progress.doneBytes)}</span> of{" "}
        <span className="numeric">{describeBytes(progress.missingBytes)}</span>
        {progress.alreadyPresent > 0 && (
          <>
            {" · "}
            <span className="numeric">{progress.alreadyPresent}</span> already in the depot
          </>
        )}
      </p>

      {progress.note && <p className="muted">{progress.note}</p>}

      <div className="actions">
        <button className="button" onClick={() => void api.cancelPreparation(props.id)}>
          Cancel
        </button>
      </div>
    </>
  );
}

/**
 * What ash actually ran.
 *
 * Worth showing: when a launch goes wrong, the command line is the first
 * thing anyone asks for. The access token is already redacted before it
 * reaches here.
 */
function Command(props: { invocation: InvocationView | null }) {
  const [open, setOpen] = useState(false);
  if (!props.invocation) return null;

  return (
    <>
      <button className="link" onClick={() => setOpen(!open)}>
        {open ? "Hide the command" : "Show the command"}
      </button>
      {open && (
        <pre className="log">
          {props.invocation.program}
          {"\n"}
          {props.invocation.args.join("\n")}
        </pre>
      )}
    </>
  );
}
