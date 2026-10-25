import { useCallback, useEffect, useRef, useState } from "react";
import { api, isUiError, type Account, type PendingSignIn, type UiError } from "./api";

type Method = "browser" | "code";

type Phase =
  | { at: "idle" }
  | { at: "starting" }
  /** Microsoft's sign-in is open in the browser, and ash waits for it to come back. */
  | { at: "browser" }
  | { at: "waiting"; pending: PendingSignIn }
  | { at: "failed"; error: UiError; method: Method };

const failure = (e: unknown, message: string): UiError =>
  isUiError(e) ? e : { kind: "unknown", message, retryable: true };

/**
 * Signing in, in one click (ADR-0007, amended): Microsoft's sign-in opens
 * in the browser, the player picks their account and approves, and the
 * browser comes back to ash.
 *
 * "Use a code instead" is the device code, for when the browser can't come
 * back: Microsoft's code page opens with the code copied, to paste. Not
 * with the code filled in by the link (`?otc=`), which Microsoft refuses
 * (see `open_sign_in_page`).
 */
export function SignIn(props: {
  onSignedIn: (account: Account) => void;
  onCancel?: () => void;
}) {
  const [phase, setPhase] = useState<Phase>({ at: "idle" });
  const [copied, setCopied] = useState(false);
  const timer = useRef<number | null>(null);

  const stopPolling = useCallback(() => {
    if (timer.current !== null) {
      window.clearTimeout(timer.current);
      timer.current = null;
    }
  }, []);

  useEffect(() => stopPolling, [stopPolling]);

  const poll = useCallback(
    async (intervalSecs: number) => {
      timer.current = window.setTimeout(async () => {
        try {
          const status = await api.pollSignIn();
          if (status.status === "complete") {
            stopPolling();
            props.onSignedIn(status.account);
          } else {
            // The service can raise the interval via slow_down; honour
            // whatever it just told us rather than the original value.
            void poll(status.interval_secs);
          }
        } catch (e) {
          stopPolling();
          setPhase({ at: "failed", error: failure(e, "Sign-in failed."), method: "code" });
        }
      }, intervalSecs * 1000);
    },
    [props, stopPolling],
  );

  const copy = useCallback(async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard access can be refused; the code is on screen regardless.
      setCopied(false);
    }
  }, []);

  const start = useCallback(async () => {
    // A browser sign-in still waiting gives way to the code.
    void api.cancelSignIn();
    setPhase({ at: "starting" });
    setCopied(false);
    try {
      const pending = await api.beginSignIn();
      setPhase({ at: "waiting", pending });
      void poll(pending.interval_secs);
      // Straight to Microsoft's code page, with the code on the clipboard.
      void copy(pending.user_code);
      void api.openSignInPage().catch(() => undefined);
    } catch (e) {
      setPhase({ at: "failed", error: failure(e, "Could not start sign-in."), method: "code" });
    }
  }, [poll, copy]);

  const browser = useCallback(async () => {
    setPhase({ at: "browser" });
    try {
      props.onSignedIn(await api.signInWithBrowser());
    } catch (e) {
      // Cancelled is the player choosing something else, which has already
      // moved the screen on.
      if (isUiError(e) && e.kind === "cancelled") return;
      setPhase({ at: "failed", error: failure(e, "Sign-in failed."), method: "browser" });
    }
  }, [props]);

  const cancel = useCallback(() => {
    stopPolling();
    void api.cancelSignIn();
    setPhase({ at: "idle" });
    props.onCancel?.();
  }, [props, stopPolling]);

  const useCode = (
    <button className="link-button" onClick={() => void start()}>
      Use a code instead
    </button>
  );

  if (phase.at === "idle" || phase.at === "starting") {
    return (
      <section className="detail">
        <h2 className="heading">Sign in</h2>
        <p className="muted">
          ash signs you in with your Microsoft account and never stores your
          password. You need a Microsoft account that owns Minecraft: Java
          Edition.
        </p>
        <div className="actions">
          <button className="button" disabled={phase.at === "starting"} onClick={() => void browser()}>
            {phase.at === "starting" ? "Starting…" : "Sign in with Microsoft"}
          </button>
          {props.onCancel && (
            <button className="button" onClick={cancel}>
              Cancel
            </button>
          )}
        </div>
        <p className="muted sign-in-other">{useCode}</p>
      </section>
    );
  }

  if (phase.at === "browser") {
    return (
      <section className="detail">
        <h2 className="heading">Sign in</h2>
        <p className="muted">
          Microsoft&rsquo;s sign-in is open in your browser. Choose your account and approve it, and you&rsquo;ll come
          straight back here.
        </p>
        <p className="muted waiting">
          <span className="pulse" aria-hidden="true" />
          Waiting for you to approve it…
        </p>
        <div className="actions">
          <button className="button" onClick={cancel}>
            Cancel
          </button>
        </div>
        <p className="muted sign-in-other">Browser didn&rsquo;t open, or stuck? {useCode}</p>
      </section>
    );
  }

  if (phase.at === "failed") {
    return (
      <section className="detail">
        <h2 className="heading">Sign in</h2>
        <p className="error" role="alert">
          {phase.error.message}
        </p>
        <div className="actions">
          {/* No retry button for outcomes a retry cannot fix. */}
          {phase.error.retryable && (
            <button className="button" onClick={() => void (phase.method === "browser" ? browser() : start())}>
              Try again
            </button>
          )}
          {props.onCancel && (
            <button className="button" onClick={cancel}>
              Back
            </button>
          )}
        </div>
        {phase.method === "browser" && <p className="muted sign-in-other">{useCode}</p>}
      </section>
    );
  }

  const { pending } = phase;

  return (
    <section className="detail">
      <h2 className="heading">Sign in</h2>

      <p className="muted">
        Your browser has opened Microsoft&rsquo;s sign-in. Paste this code there with Ctrl+V &ndash; it&rsquo;s
        already copied &ndash; then choose your account and approve it.
      </p>

      <div className="actions">
        <button className="button" onClick={() => void api.openSignInPage().catch(() => undefined)}>
          Open the sign-in page again
        </button>
      </div>

      <button
        className="code"
        onClick={() => copy(pending.user_code)}
        title="Copy to clipboard"
      >
        {pending.user_code}
      </button>
      <p className="muted copy-hint">
        {copied ? "Copied. " : "Click the code to copy it. "}The page is{" "}
        <span className="numeric">{pending.verification_uri}</span>.
      </p>

      <p className="muted waiting">
        <span className="pulse" aria-hidden="true" />
        Waiting for you to approve it…
      </p>

      <div className="actions">
        <button className="button" onClick={cancel}>
          Cancel
        </button>
      </div>
    </section>
  );
}
