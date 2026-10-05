import { describeAge, describeKind, type Account, type Instance, type InstanceId } from "./api";
import { Icon } from "./icons";
import type { Launch } from "./launch";
import { LaunchArea } from "./LaunchArea";
import { Face } from "./TitleBar";

/**
 * The launcher's home: who is playing, LAUNCH GAME for the selected
 * instance, and below it the instances to choose from.
 *
 * The two middle cards hold their places until recent servers (#74, #75)
 * and the instance at a glance (#73) fill them.
 */
export function PlayPage(props: {
  player: Account | null;
  instances: Instance[];
  selected: Instance | null;
  launch: Launch;
  sounds: boolean;
  onSelect: (id: InstanceId) => void;
  onOpen: (id: InstanceId) => void;
  onNew: () => void;
}) {
  const { selected, launch } = props;

  const greeting = (
    <h2 className="welcome">
      Welcome back,
      {props.player && (
        <>
          <Face account={props.player} />
          {props.player.username}!
        </>
      )}
    </h2>
  );

  if (!selected) {
    return (
      <section className="page">
        {greeting}
        <div className="empty-state">
          <Icon name="mods" />
          <b>No instances yet</b>
          <span>Create one to choose a version and play.</span>
          <button className="button" onClick={props.onNew}>
            <Icon name="plus" />
            New instance
          </button>
        </div>
      </section>
    );
  }

  return (
    <section className="page play-page">
      {greeting}
      <LaunchArea instance={selected} launch={launch} sounds={props.sounds} />

      {launch.notice && (
        <p className="notice" role="status">
          {launch.notice.message}
        </p>
      )}

      <div className="cards">
        <div className="card">
          <h3>
            Recent servers <small>{selected.name}</small>
          </h3>
          <p className="hint">Your servers, with Join, are coming soon.</p>
        </div>
        <div className="card">
          <h3>
            This instance <small>{selected.name}</small>
          </h3>
          <p className="hint">Its ash features, play time and mods are coming soon.</p>
        </div>
        <InstancesCard
          instances={props.instances}
          selected={selected.id}
          // Choosing another instance mid-launch or mid-game would show its
          // LAUNCH GAME over a game still going for this one - and stop
          // watching that game, so a crash would go unseen.
          locked={launch.phase.at === "working" || launch.phase.at === "running"}
          onSelect={props.onSelect}
          onOpen={props.onOpen}
          onNew={props.onNew}
        />
      </div>
    </section>
  );
}

/** "1.21" from "1.21.11": the version a player names, short enough for a badge. */
function badge(versionId: string): string {
  return versionId.split(".").slice(0, 2).join(".");
}

/**
 * The instances, set apart from the cards beside it - a brighter surface
 * and a soft white glow - because choosing what to play is the page's job.
 */
function InstancesCard(props: {
  instances: Instance[];
  selected: InstanceId;
  locked: boolean;
  onSelect: (id: InstanceId) => void;
  onOpen: (id: InstanceId) => void;
  onNew: () => void;
}) {
  return (
    <div className="card card-primary">
      <h3>
        Instances
        <button className="mini" onClick={props.onNew}>
          <Icon name="plus" />
          New
        </button>
      </h3>
      <div className="instance-rows" role="listbox" aria-label="Instances">
        {props.instances.map((instance) => (
          <div
            key={instance.id}
            className="instance-row"
            role="option"
            aria-selected={instance.id === props.selected}
            aria-disabled={props.locked && instance.id !== props.selected}
            tabIndex={0}
            onClick={() => !props.locked && props.onSelect(instance.id)}
            onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && !props.locked && props.onSelect(instance.id)}
          >
            <span className="version-badge numeric">{badge(instance.version_id)}</span>
            <span className="instance-text">
              <b>{instance.name}</b>
              <small>
                {describeKind(instance)} ·{" "}
                {instance.last_played_ms ? describeAge(instance.last_played_ms) : "never played"}
              </small>
            </span>
            <button
              className="instance-cog"
              aria-label={`${instance.name} settings`}
              onClick={(e) => {
                e.stopPropagation();
                props.onOpen(instance.id);
              }}
            >
              <Icon name="settings" />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
