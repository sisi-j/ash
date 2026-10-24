import { useEffect, useState } from "react";
import { api, isUiError, onUpdateProgress, type AvailableUpdate } from "./api";
import { MarkdownBody } from "./NewsPage";

type Step = { at: "offered" } | { at: "installing"; fraction: number | null } | { at: "failed"; message: string };

/**
 * A newer release, offered when ash starts (spec 0005, ADR-0025): its
 * version, what's new in it, and Update. Never in the way: a check that
 * fails shows nothing, Later hides it until the next start, and Update waits
 * for a game ash started to close, since installing restarts ash.
 */
export function UpdateBar(props: { gameRunning: boolean }) {
  const [update, setUpdate] = useState<AvailableUpdate | null>(null);
  const [later, setLater] = useState(false);
  const [showNotes, setShowNotes] = useState(false);
  const [step, setStep] = useState<Step>({ at: "offered" });

  useEffect(() => {
    let live = true;
    void api
      .checkForUpdate()
      .then((found) => {
        if (live) setUpdate(found);
      })
      .catch(() => undefined);
    return () => {
      live = false;
    };
  }, []);

  useEffect(() => {
    const unlisten = onUpdateProgress((progress) =>
      setStep({
        at: "installing",
        fraction: progress.total ? Math.min(1, progress.downloaded / progress.total) : null,
      }),
    );
    return () => {
      void unlisten.then((un) => un());
    };
  }, []);

  if (update === null || later) return null;

  const install = async () => {
    setStep({ at: "installing", fraction: null });
    try {
      await api.installUpdate();
    } catch (e) {
      setStep({
        at: "failed",
        message: isUiError(e)
          ? e.message
          : "The update couldn't be installed. This version keeps working; try again later.",
      });
    }
  };

  const installing = step.at === "installing";
  let detail = "Updating restarts ash.";
  if (step.at === "installing") {
    detail = step.fraction === null ? "Downloading…" : `Downloading… ${Math.round(step.fraction * 100)}%`;
  } else if (props.gameRunning) {
    detail = "Close the game to update: updating restarts ash.";
  }

  return (
    <div className="update-bar" role="status">
      <div className="update-bar-row">
        <p>
          <b>ash {update.version} is ready.</b> {detail}
        </p>
        <div className="ash-account-notice-actions">
          {update.notes !== "" && (
            <button className="button" onClick={() => setShowNotes(!showNotes)}>
              {showNotes ? "Hide what's new" : "What's new"}
            </button>
          )}
          <button className="button" disabled={installing} onClick={() => setLater(true)}>
            Later
          </button>
          <button className="button" disabled={installing || props.gameRunning} onClick={() => void install()}>
            {installing ? "Updating…" : "Update"}
          </button>
        </div>
      </div>
      {step.at === "failed" && (
        <p className="update-bar-failed" role="alert">
          {step.message}
        </p>
      )}
      {showNotes && (
        <div className="update-bar-notes news-body">
          <MarkdownBody markdown={update.notes} />
        </div>
      )}
    </div>
  );
}
