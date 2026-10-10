import { useState } from "react";
import { api, type Account, type AshAccountStatus } from "./api";

/**
 * Said once, the first time ash's servers make an ash account for a player
 * (spec 0004): what it is, what it holds, and that it can be deleted. A
 * quiet line, not a dialog - nothing here needs an answer before playing.
 */
export function AshAccountNotice(props: { account: Account; onDismiss: () => void }) {
  return (
    <div className="ash-account-notice" role="status">
      <p>
        <b>ash made an ash account for {props.account.username}.</b> It will carry your settings and cosmetics between
        computers. It holds your Minecraft name and ID, never your password or sign-in. You can delete it any time in
        Settings.
      </p>
      <div className="ash-account-notice-actions">
        <button className="button" onClick={() => void api.openPrivacyStatement()}>
          Privacy statement
        </button>
        <button className="button" onClick={props.onDismiss}>
          OK
        </button>
      </div>
    </div>
  );
}

/** One line in Settings: where the active player stands with ash's servers. */
export function describeAshAccount(username: string, status: AshAccountStatus): string {
  switch (status.state) {
    case "signed_in":
      return `Signed in to ash as ${username}.`;
    case "not_signed_in":
      return "Not signed in to ash yet. ash signs in on its own; playing doesn't need it.";
    case "deleted":
      return "You deleted your ash account. Signing in to this Microsoft account again makes a new one.";
    case "unreachable":
      return "ash's servers can't be reached right now. ash will try again; playing is unaffected.";
    case "refused":
      return status.message;
  }
}

/**
 * Deleting the ash account (spec 0004, user story 3), asked on the page
 * itself as deleting an instance is. Nothing changes here unless ash's
 * servers confirm the deletion.
 */
export function DeleteAshAccount(props: { username: string; onDelete: () => Promise<boolean> }) {
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);

  if (!asking) {
    return (
      <button className="button button-danger" onClick={() => setAsking(true)}>
        Delete ash account
      </button>
    );
  }
  return (
    <div className="ash-account-delete" role="group" aria-label="Delete ash account">
      <p>
        This deletes {props.username}&rsquo;s ash account from ash&rsquo;s servers, with its synced settings and
        cosmetics. You stay signed in to play, and your instances and worlds on this computer are kept.
      </p>
      {failed && (
        <p className="ash-account-delete-failed" role="alert">
          ash&rsquo;s servers can&rsquo;t be reached, so nothing was deleted. Try again later.
        </p>
      )}
      <div className="ash-account-notice-actions">
        <button
          className="button button-danger"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setFailed(false);
            const deleted = await props.onDelete();
            setBusy(false);
            if (deleted) setAsking(false);
            else setFailed(true);
          }}
        >
          {busy ? "Deleting..." : "Delete"}
        </button>
        <button className="button" disabled={busy} onClick={() => setAsking(false)}>
          Keep
        </button>
      </div>
    </div>
  );
}
