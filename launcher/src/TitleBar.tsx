import { useEffect, useRef, useState } from "react";
import { appWindow, type Account, type Accounts } from "./api";
import { Icon } from "./icons";

/**
 * The bar across the top that replaces the system's: the wordmark, who is
 * signed in, and the window's own buttons.
 *
 * The whole bar drags the window, and a double click on it maximises - both
 * handled by Tauri for anything marked as a drag region. "deep" makes the
 * bar's text drag too; its buttons do not, because Tauri never starts a drag
 * from something clickable.
 */
export function TitleBar(props: {
  accounts: Accounts | null;
  busy: boolean;
  onSelect: (profileId: string) => void;
  onSignOut: (profileId: string) => void;
  onAdd: () => void;
}) {
  return (
    <header className="titlebar" data-tauri-drag-region="deep">
      <span className="wordmark">ash</span>

      {props.accounts && (
        <AccountMenu
          accounts={props.accounts}
          busy={props.busy}
          onSelect={props.onSelect}
          onSignOut={props.onSignOut}
          onAdd={props.onAdd}
        />
      )}

      <div className="window-controls">
        <button aria-label="Minimise" onClick={() => void appWindow.minimise()}>
          <Icon name="minimise" />
        </button>
        <button aria-label="Maximise" onClick={() => void appWindow.toggleMaximise()}>
          <Icon name="maximise" />
        </button>
        <button className="close" aria-label="Close" onClick={() => void appWindow.close()}>
          <Icon name="close" />
        </button>
      </div>
    </header>
  );
}

/**
 * Who plays next, and the way to change it.
 *
 * The active account is ticked, not merely listed first: a player switching
 * between a main and an alt needs to see which one is armed, because joining
 * a server as the wrong person is the failure this menu exists to prevent.
 */
function AccountMenu(props: {
  accounts: Accounts;
  busy: boolean;
  onSelect: (profileId: string) => void;
  onSignOut: (profileId: string) => void;
  onAdd: () => void;
}) {
  const { accounts, active } = props.accounts;
  const current = accounts.find((a) => a.profile_id === active) ?? null;
  const [open, setOpen] = useState(false);
  // Two steps, because "sign out" sits a few pixels from "play as this
  // person" and only one of those is worth a misclick.
  const [confirming, setConfirming] = useState(false);
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      setConfirming(false);
      return;
    }
    const away = (e: MouseEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false);
    };
    const escape = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", away);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", away);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);

  const choose = (then: () => void) => () => {
    setOpen(false);
    then();
  };

  if (!current) {
    return (
      <div className="account-menu">
        <button className="account-button" onClick={props.onAdd}>
          <span className="face face-empty" aria-hidden="true" />
          Sign in
        </button>
      </div>
    );
  }

  return (
    <div className="account-menu" ref={root}>
      <button
        className="account-button"
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen(!open)}
      >
        <Face account={current} />
        <span className="account-name">{current.username}</span>
        <Icon name="chevron" />
      </button>

      {open && (
        // Not part of the drag region, so its words can be read and clicked
        // around without the window moving.
        <div className="menu" role="menu" data-tauri-drag-region="false">
          {confirming ? (
            <div className="menu-confirm">
              <p>
                Sign out <strong>{current.username}</strong>?
              </p>
              <p className="muted">
                This erases its saved sign-in from this machine. It does not touch the Microsoft
                account itself.
              </p>
              <div className="actions">
                <button
                  className="button button-danger"
                  disabled={props.busy}
                  onClick={choose(() => props.onSignOut(current.profile_id))}
                >
                  Sign out
                </button>
                <button className="button" onClick={() => setConfirming(false)}>
                  Keep
                </button>
              </div>
            </div>
          ) : (
            <>
              {accounts.map((account) => (
                <button
                  key={account.profile_id}
                  role="menuitemradio"
                  aria-checked={account.profile_id === active}
                  disabled={props.busy}
                  onClick={choose(() => {
                    if (account.profile_id !== active) props.onSelect(account.profile_id);
                  })}
                >
                  <Face account={account} />
                  <span className="account-name">{account.username}</span>
                  {account.profile_id === active && (
                    <span className="menu-check">
                      <Icon name="check" />
                    </span>
                  )}
                </button>
              ))}
              <hr />
              <button role="menuitem" onClick={choose(props.onAdd)}>
                <span className="menu-glyph">
                  <Icon name="plus" />
                </span>
                Add account
              </button>
              <button role="menuitem" className="danger" onClick={() => setConfirming(true)}>
                <span className="menu-glyph">
                  <Icon name="signOut" />
                </span>
                Sign out
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}

/** A Mojang skin is the whole 64 by 64 texture; the stylesheet crops it to the face. */
export function Face(props: { account: Account }) {
  return props.account.skin_url ? (
    <span
      className="face"
      style={{ backgroundImage: `url(${props.account.skin_url})` }}
      aria-hidden="true"
    />
  ) : (
    <span className="face face-empty" aria-hidden="true" />
  );
}
