import { describeAge, type DeletedElsewhere, type SyncStatus } from "./api";

/** One line in Settings: whether sync is on, and how the last one went. */
export function describeSync(status: SyncStatus): string {
  switch (status.state.state) {
    case "off":
      return "Off on this computer. Your settings here stay as they are.";
    case "not_signed_in":
      return "Starts once you're signed in to ash.";
    case "waiting":
      return "On. Syncing shortly.";
    case "synced":
      return `On. Synced ${describeAge(status.state.at_ms)}.`;
    case "unreachable":
      return "On, but ash's servers can't be reached right now. ash will try again.";
  }
}

/** The Settings row: the switch, and a line saying how it's going. */
export function SyncSetting(props: { status: SyncStatus | null; onToggle: (enabled: boolean) => void }) {
  const enabled = props.status?.enabled ?? true;
  return (
    <div className="option">
      <span className="option-text">
        <b id="sync-settings">Sync between computers</b>
        <small>
          Your crosshair and other ash settings, launcher settings, instances and server lists follow you to every
          computer you sign in on. Memory, Java, window size and your own mods stay on each computer.
          {props.status && (
            <>
              <br />
              <span role="status">{describeSync(props.status)}</span>
            </>
          )}
        </small>
      </span>
      <button
        className="switch"
        role="switch"
        aria-labelledby="sync-settings"
        aria-checked={enabled}
        disabled={!props.status}
        onClick={() => props.onToggle(!enabled)}
      />
    </div>
  );
}

/**
 * An instance deleted on another computer that is still here. Nothing is
 * deleted until the player says (spec 0004): delete it here too, worlds and
 * all, or keep it as this computer's alone.
 */
export function DeletedElsewhereQuestion(props: {
  instance: DeletedElsewhere;
  onAnswer: (deleteHere: boolean) => void;
}) {
  return (
    <div className="ash-account-notice" role="alertdialog" aria-label={`${props.instance.name} was deleted elsewhere`}>
      <p>
        <b>&ldquo;{props.instance.name}&rdquo; was deleted on another computer.</b> Delete it here too, with its worlds
        on this computer, or keep it here as this computer&rsquo;s alone.
      </p>
      <div className="ash-account-notice-actions">
        <button className="button button-danger" onClick={() => props.onAnswer(true)}>
          Delete here
        </button>
        <button className="button" onClick={() => props.onAnswer(false)}>
          Keep here
        </button>
      </div>
    </div>
  );
}
