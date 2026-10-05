import { useEffect, useState } from "react";
import { api } from "./api";
import type { Phase } from "./launch";

/** How much of a crashed game's output to show: the end is where the reason is. */
const LAST_LINES = 40;

/**
 * A launch that failed or a game that crashed, over the blurred launcher:
 * a failure should stop the player, not sit in a corner they might miss.
 *
 * It says what went wrong in ash's words, shows a crash's last output, and
 * offers what can help: Try again only when trying again can work, and
 * ash's log, which is the first thing anyone asks for.
 */
export function LaunchFailure(props: {
  phase: Extract<Phase, { at: "failed" } | { at: "crashed" }>;
  onRetry: () => void;
  onClose: () => void;
}) {
  const { phase } = props;
  const [copied, setCopied] = useState(false);
  const output = phase.at === "crashed" ? phase.log.slice(-LAST_LINES).join("\n") : "";
  const canRetry = phase.at === "crashed" || phase.error.retryable;

  useEffect(() => {
    const escape = (e: KeyboardEvent) => e.key === "Escape" && props.onClose();
    document.addEventListener("keydown", escape);
    return () => document.removeEventListener("keydown", escape);
  }, [props]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(output);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard access can be refused; the output is on screen to select.
    }
  };

  return (
    <div className="failure-scrim" role="alertdialog" aria-modal="true" aria-labelledby="failure-title">
      <div className="failure">
        <div className="failure-mark" aria-hidden="true">
          !
        </div>
        <h2 id="failure-title" className="failure-title">
          {phase.at === "crashed" ? "The game crashed" : "Launch failed"}
        </h2>
        <p className="failure-reason">
          {phase.at === "crashed"
            ? `Minecraft stopped unexpectedly${phase.code !== null ? ` (exit code ${phase.code})` : ""}.`
            : phase.error.message}
        </p>

        {output && (
          <div className="failure-output">
            <div className="failure-output-head">
              <span>Last output</span>
              <button className="link" onClick={() => void copy()}>
                {copied ? "Copied" : "Copy"}
              </button>
            </div>
            <pre className="log">{output}</pre>
          </div>
        )}

        <div className="actions">
          {canRetry && (
            <button className="button button-go" onClick={props.onRetry}>
              Try again
            </button>
          )}
          <button className="button" onClick={() => void api.revealLog()}>
            Show log
          </button>
          <button className="button failure-close" onClick={props.onClose} autoFocus>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
