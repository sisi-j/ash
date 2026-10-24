import { useEffect, useState } from "react";
import { api, type ReportRequest } from "./api";

/**
 * "Copy report" (spec 0005): the report shown in full before it is copied,
 * so a tester knows what they're sharing. ash never sends it anywhere; the
 * tester pastes it into feedback.
 */
export function ReportDialog(props: { request: ReportRequest; onClose: () => void }) {
  const [text, setText] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const { request, onClose } = props;

  useEffect(() => {
    let live = true;
    void api
      .report(request)
      .then((report) => live && setText(report))
      .catch(() => live && setText("ash couldn't put the report together. Show log has the same lines."));
    return () => {
      live = false;
    };
  }, [request]);

  useEffect(() => {
    const escape = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      // This dialog sits over the failure panel; Escape closes only this.
      e.stopImmediatePropagation();
      onClose();
    };
    document.addEventListener("keydown", escape, true);
    return () => document.removeEventListener("keydown", escape, true);
  }, [onClose]);

  const copy = async () => {
    if (text === null) return;
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard access can be refused; the report is on screen to select.
    }
  };

  return (
    <div className="failure-scrim report-scrim" role="dialog" aria-modal="true" aria-labelledby="report-title">
      <div className="failure report">
        <h2 id="report-title" className="failure-title">
          Report
        </h2>
        <p className="report-note">
          What you&rsquo;d share: ash&rsquo;s version and Windows&rsquo;, what went wrong, and the end of ash&rsquo;s log
          {request.crashed ? " and of the game's output" : ""}. Your password and sign-in are never in it.
          {request.crashed ? " The game's output can include server addresses and player names." : ""}
        </p>
        <pre className="log report-text">{text ?? "…"}</pre>
        <div className="actions">
          <button className="button button-go" disabled={text === null} onClick={() => void copy()}>
            {copied ? "Copied" : "Copy"}
          </button>
          <button className="button" onClick={onClose} autoFocus>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
