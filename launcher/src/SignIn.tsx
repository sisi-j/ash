import { useCallback, useEffect, useRef, useState } from "react";
import { api, isUiError, type Account, type PendingSignIn, type UiError } from "./api";

type Phase =
  | { at: "idle" }
  | { at: "starting" }
  | { at: "waiting"; pending: PendingSignIn }
  | { at: "failed"; error: UiError };

/**
 * Device-code sign-in.
 *
 * Microsoft does not support `verification_uri_complete`, so there is no
 * one-click link with the code pre-filled - the player genuinely has to read
 * the code and type it. That is why the code is the largest thing on screen
 * and why copying it is one click.
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
          setPhase({
            at: "failed",
            error: isUiError(e)
              ? e
              : { kind: "unknown", message: "Sign-in failed.", retryable: true },
          });
        }
      }, intervalSecs * 1000);
    },
    [props, stopPolling],
  );

  const start = useCallback(async () => {
    setPhase({ at: "starting" });
    setCopied(false);
    try {
      const pending = await api.beginSignIn();
      setPhase({ at: "waiting", pending });
      void poll(pending.interval_secs);
    } catch (e) {
      setPhase({
        at: "failed",
        error: isUiError(e)
          ? e
          : { kind: "unknown", message: "Could not start sign-in.", retryable: true },
      });
    }
  }, [poll]);

  const cancel = useCallback(() => {
    stopPolling();
    void api.cancelSignIn();
    setPhase({ at: "idle" });
    props.onCancel?.();
  }, [props, stopPolling]);

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
          <button className="button" disabled={phase.at === "starting"} onClick={start}>
            {phase.at === "starting" ? "Starting…" : "Sign in with Microsoft"}
          </button>
          {props.onCancel && (
            <button className="button" onClick={cancel}>
              Cancel
            </button>
          )}
        </div>
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
            <button className="button" onClick={start}>
              Try again
            </button>
          )}
          {props.onCancel && (
            <button className="button" onClick={cancel}>
              Back
            </button>
          )}
        </div>
      </section>
    );
  }

  const { pending } = phase;

  return (
    <section className="detail">
      <h2 className="heading">Sign in</h2>

      <p className="muted">
        Go to <span className="numeric">{pending.verification_uri}</span> in
        your browser and enter this code.
      </p>

      <button
        className="code"
        onClick={() => copy(pending.user_code)}
        title="Copy to clipboard"
      >
        {pending.user_code}
      </button>
      <p className="muted copy-hint">{copied ? "Copied" : "Click the code to copy it"}</p>

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
