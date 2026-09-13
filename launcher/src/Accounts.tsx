import { useState } from "react";
import type { Account, Accounts as AccountList } from "./api";

/**
 * Who ash knows, and who plays next.
 *
 * The active account is marked rather than merely sorted first: a player
 * switching between a main and an alt needs to see which one is armed
 * without reading the order, because joining a server as the wrong person is
 * the whole failure this screen exists to prevent.
 */
export function Accounts(props: {
  accounts: AccountList;
  busy: boolean;
  onSelect: (profileId: string) => void;
  onRemove: (profileId: string) => void;
  onAdd: () => void;
  onClose: () => void;
}) {
  const { accounts, active } = props.accounts;

  return (
    <section className="detail">
      <h2 className="heading">Accounts</h2>

      {accounts.length === 0 ? (
        <p className="muted">No accounts yet.</p>
      ) : (
        <ul className="account-list">
          {accounts.map((account) => (
            <AccountRow
              key={account.profile_id}
              account={account}
              isActive={account.profile_id === active}
              busy={props.busy}
              onSelect={() => props.onSelect(account.profile_id)}
              onRemove={() => props.onRemove(account.profile_id)}
            />
          ))}
        </ul>
      )}

      <div className="actions">
        <button className="button" onClick={props.onAdd}>
          Add another account
        </button>
        {accounts.length > 0 && (
          <button className="button" onClick={props.onClose}>
            Done
          </button>
        )}
      </div>

      <p className="note muted">
        Signing out erases that account's saved sign-in from this machine. It
        does not touch the Microsoft account itself.
      </p>
    </section>
  );
}

function AccountRow(props: {
  account: Account;
  isActive: boolean;
  busy: boolean;
  onSelect: () => void;
  onRemove: () => void;
}) {
  const { account } = props;
  // Two steps, because the row puts "sign out" a few pixels from "play as
  // this person" and only one of those is worth a misclick.
  const [confirming, setConfirming] = useState(false);

  return (
    <li className={`account-row${props.isActive ? " is-active" : ""}`}>
      {account.skin_url ? (
        <span
          className="face face-lg"
          style={{ backgroundImage: `url(${account.skin_url})` }}
          aria-hidden="true"
        />
      ) : (
        <span className="face face-lg face-empty" aria-hidden="true" />
      )}

      <span className="account-who">
        <span className="account-name">{account.username}</span>
        {props.isActive && <span className="badge">Playing</span>}
      </span>

      <span className="account-actions">
        {!props.isActive && (
          <button className="button" disabled={props.busy} onClick={props.onSelect}>
            Use
          </button>
        )}
        {confirming ? (
          <>
            <button
              className="button button-danger"
              disabled={props.busy}
              onClick={props.onRemove}
            >
              Confirm
            </button>
            <button className="button" onClick={() => setConfirming(false)}>
              Keep
            </button>
          </>
        ) : (
          <button className="button" disabled={props.busy} onClick={() => setConfirming(true)}>
            Sign out
          </button>
        )}
      </span>
    </li>
  );
}
