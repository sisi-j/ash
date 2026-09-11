import { useCallback, useEffect, useState } from "react";
import {
  api,
  describeBytes,
  onPrepareFinished,
  onPrepareProgress,
  type InstanceId,
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
  | { at: "unknown" }
  | { at: "planned"; plan: Plan }
  | { at: "running"; progress: Progress }
  | { at: "ready" }
  | { at: "failed"; error: UiError };

const START: Progress = {
  totalFiles: 0,
  doneFiles: 0,
  doneBytes: 0,
  missingBytes: 0,
  alreadyPresent: 0,
  note: null,
};

/**
 * Preparation for one instance.
 *
 * Progress is rendered from the event stream rather than a percentage,
 * because "4,300 of 4,900 files" and "re-verifying a corrupt file" are both
 * things a player wants to see and a single number cannot say.
 */
export function Prepare(props: { id: InstanceId }) {
  const [phase, setPhase] = useState<Phase>({ at: "unknown" });

  // Re-plan whenever the selected instance changes.
  useEffect(() => {
    let live = true;
    setPhase({ at: "unknown" });
    api
      .planInstance(props.id)
      .then((plan) => {
        if (!live) return;
        setPhase(plan.missing_files === 0 ? { at: "ready" } : { at: "planned", plan });
      })
      .catch((e) => live && setPhase({ at: "failed", error: e as UiError }));
    return () => {
      live = false;
    };
  }, [props.id]);

  useEffect(() => {
    const progress = onPrepareProgress((event) => {
      setPhase((current) => {
        const p = current.at === "running" ? current.progress : START;
        switch (event.event) {
          case "resolving":
            return { at: "running", progress: { ...p, note: "Reading version metadata…" } };
          case "planned":
            return {
              at: "running",
              progress: {
                ...p,
                totalFiles: event.missing_files,
                missingBytes: event.missing_bytes,
                alreadyPresent: event.already_present,
                note: null,
              },
            };
          case "downloaded":
            return {
              at: "running",
              progress: {
                ...p,
                doneFiles: event.done_files,
                doneBytes: event.done_bytes,
                note: null,
              },
            };
          case "resuming":
            return { at: "running", progress: { ...p, note: "Resuming an interrupted file…" } };
          case "reverifying":
            return { at: "running", progress: { ...p, note: "A file arrived corrupt; retrying…" } };
          default:
            return current;
        }
      });
    });

    const finished = onPrepareFinished((outcome) => {
      if (outcome.ok) {
        setPhase({ at: "ready" });
      } else if (outcome.error) {
        setPhase(
          outcome.error.kind === "cancelled"
            ? { at: "unknown" }
            : { at: "failed", error: outcome.error },
        );
        if (outcome.error.kind === "cancelled") void replan();
      }
    });

    return () => {
      void progress.then((un) => un());
      void finished.then((un) => un());
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const replan = useCallback(async () => {
    try {
      const plan = await api.planInstance(props.id);
      setPhase(plan.missing_files === 0 ? { at: "ready" } : { at: "planned", plan });
    } catch (e) {
      setPhase({ at: "failed", error: e as UiError });
    }
  }, [props.id]);

  const start = useCallback(async () => {
    setPhase({ at: "running", progress: START });
    try {
      await api.prepareInstance(props.id);
    } catch (e) {
      setPhase({ at: "failed", error: e as UiError });
    }
  }, [props.id]);

  if (phase.at === "unknown") {
    return <p className="muted">Checking what this instance needs…</p>;
  }

  if (phase.at === "ready") {
    return (
      <p className="muted">
        Everything this version needs is in the depot. Launching arrives with #8.
      </p>
    );
  }

  if (phase.at === "failed") {
    return (
      <>
        <p className="error" role="alert">
          {phase.error.message}
        </p>
        {phase.error.retryable && (
          <div className="actions">
            <button className="button" onClick={start}>
              Try again
            </button>
          </div>
        )}
      </>
    );
  }

  if (phase.at === "planned") {
    return (
      <>
        <p className="muted">
          <span className="numeric">{phase.plan.missing_files}</span> files to download,{" "}
          <span className="numeric">{describeBytes(phase.plan.missing_bytes)}</span>.
        </p>
        <div className="actions">
          <button className="button" onClick={start}>
            Prepare
          </button>
        </div>
      </>
    );
  }

  const { progress } = phase;
  const fraction =
    progress.totalFiles > 0 ? progress.doneFiles / progress.totalFiles : 0;

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
        <button className="button" onClick={() => void api.cancelPreparation()}>
          Cancel
        </button>
      </div>
    </>
  );
}
