import { useEffect, useState } from "react";
import {
  api,
  describeAge,
  describeDuration,
  describeKind,
  isUiError,
  type Account,
  type AshFeatures,
  type Instance,
  type InstanceGlance,
  type InstanceId,
  type ServerEntry,
  type ServerStatus,
  type Session,
} from "./api";
import { Icon } from "./icons";
import type { Launch } from "./launch";
import { LaunchArea } from "./LaunchArea";
import { playLaunchSound } from "./sound";
import { Face } from "./TitleBar";

/**
 * The launcher's home: who is playing, LAUNCH GAME for the selected
 * instance, and below it the instances to choose from.
 *
 * The servers card puts the servers the player joined most recently first,
 * from ash's client's own record, then the rest of the game's list.
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
        <ServersCard instance={selected} launch={launch} sounds={props.sounds} />
        <ThisInstanceCard instance={selected} phase={launch.phase.at} onOpen={props.onOpen} />
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

/**
 * How often the servers are asked again while the page shows. ash-core will
 * not ask any one server more often than this whatever the page does.
 */
const SERVER_REFRESH_MS = 60_000;

/**
 * The selected instance's servers, from the game's own list in the player's
 * order, each with how it is and a Join that starts the game straight into it.
 */
function ServersCard(props: { instance: Instance; launch: Launch; sounds: boolean }) {
  const { instance, launch } = props;
  const [servers, setServers] = useState<ServerEntry[] | null>(null);
  const [statuses, setStatuses] = useState<Record<string, ServerStatus>>({});

  // Read again when a game ends too: the player may have added a server in it.
  const playing = launch.phase.at === "running";
  useEffect(() => {
    let live = true;
    setStatuses({});
    api
      .servers(instance.id)
      .then((list) => live && setServers(list))
      .catch(() => live && setServers([]));
    return () => {
      live = false;
    };
  }, [instance.id, playing]);

  useEffect(() => {
    if (!servers) return;
    let live = true;
    const ask = () =>
      servers.forEach((server) =>
        api
          .serverStatus(instance.id, server.address)
          .catch((): ServerStatus => ({ state: "offline" }))
          .then((status) => live && setStatuses((all) => ({ ...all, [server.address]: status }))),
      );
    ask();
    const timer = setInterval(ask, SERVER_REFRESH_MS);
    return () => {
      live = false;
      clearInterval(timer);
    };
  }, [instance.id, servers]);

  const join = (address: string) => {
    if (props.sounds) playLaunchSound("click");
    launch.join(address);
  };

  // Editing works on the game's own order, which the card does not show
  // otherwise: it puts the most recently joined first.
  const [editing, setEditing] = useState(false);
  const [ordered, setOrdered] = useState<ServerEntry[]>([]);
  const [form, setForm] = useState<ServerForm | null>(null);
  const [removing, setRemoving] = useState<number | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const reread = async () => {
    const list = await api.serverList(instance.id).catch(() => null);
    if (list) setOrdered(list);
    const recent = await api.servers(instance.id).catch(() => null);
    if (recent) setServers(recent);
  };

  useEffect(() => {
    setEditing(false);
    setForm(null);
    setRemoving(null);
    setProblem(null);
  }, [instance.id]);

  const startEditing = () => {
    setProblem(null);
    setEditing(true);
    void reread();
  };

  /** One change, then the list as it now is - also after a refusal, which is often a list changed elsewhere. */
  const change = async (work: () => Promise<void>) => {
    setBusy(true);
    setProblem(null);
    try {
      await work();
      setForm(null);
      setRemoving(null);
    } catch (e) {
      setProblem(isUiError(e) ? e.message : "That didn't work. Try again.");
    } finally {
      await reread();
      setBusy(false);
    }
  };

  const save = (f: ServerForm) =>
    change(() =>
      f.position === null
        ? api.addServer(instance.id, f.name, f.address)
        : api.editServer(instance.id, f.position, f.expected, f.name, f.address),
    );

  const locked = playing || busy;

  if (editing) {
    return (
      <div className="card servers-card">
        <h3>
          Edit servers <small>{instance.name}</small>
        </h3>
        <div className="server-edit-actions">
          <button
            className="button"
            disabled={locked || form !== null}
            onClick={() => setForm({ position: null, expected: "", name: "", address: "" })}
          >
            <Icon name="plus" /> Add server
          </button>
          <button className="button button-go" disabled={busy} onClick={() => setEditing(false)}>
            Done
          </button>
        </div>
        {playing && <p className="hint">Close {instance.name} to edit its servers. The game rewrites its list itself.</p>}
        {problem && (
          <p className="setting-refused" role="alert">
            {problem}
          </p>
        )}
        {form && (
          <ServerFormView form={form} busy={busy} onChange={setForm} onCancel={() => setForm(null)} onSave={save} />
        )}
        {ordered.length === 0 && !form && <p className="hint">No servers yet.</p>}
        <div className="server-rows">
          {ordered.map((server, position) => (
            <div key={`${position}-${server.address}`} className="server-row">
              <ServerIcon name={server.name} icon={server.icon} />
              <span className="server-text">
                <b>{server.name || server.address}</b>
                <small>{server.address}</small>
              </span>
              {removing === position ? (
                <>
                  <button
                    className="button button-danger"
                    disabled={locked}
                    onClick={() => change(() => api.removeServer(instance.id, position, server.address))}
                  >
                    Remove
                  </button>
                  <button className="button" disabled={busy} onClick={() => setRemoving(null)}>
                    Keep
                  </button>
                </>
              ) : (
                <span className="server-edit-row">
                  <button
                    className="icon-only"
                    aria-label={`Move ${server.name || server.address} up`}
                    disabled={locked || position === 0}
                    onClick={() => change(() => api.moveServer(instance.id, position, server.address, position - 1))}
                  >
                    <Icon name="up" />
                  </button>
                  <button
                    className="icon-only"
                    aria-label={`Move ${server.name || server.address} down`}
                    disabled={locked || position === ordered.length - 1}
                    onClick={() => change(() => api.moveServer(instance.id, position, server.address, position + 1))}
                  >
                    <Icon name="chevron" />
                  </button>
                  <button
                    className="icon-only"
                    aria-label={`Edit ${server.name || server.address}`}
                    disabled={locked || form !== null}
                    onClick={() =>
                      setForm({ position, expected: server.address, name: server.name, address: server.address })
                    }
                  >
                    <Icon name="edit" />
                  </button>
                  <button
                    className="icon-only"
                    aria-label={`Remove ${server.name || server.address}`}
                    disabled={locked}
                    onClick={() => setRemoving(position)}
                  >
                    <Icon name="close" />
                  </button>
                </span>
              )}
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="card servers-card">
      <h3>
        {servers?.some((s) => s.last_joined_ms !== null) ? "Recent servers" : "Servers"} <small>{instance.name}</small>
        <button
          className="button server-edit-open"
          disabled={servers === null}
          onClick={startEditing}
          aria-label={`Edit ${instance.name}'s servers`}
        >
          Edit
        </button>
      </h3>
      {servers && servers.length === 0 && (
        <p className="hint">No servers yet. Add one with Edit, or in the game's Multiplayer screen.</p>
      )}
      {servers && servers.length > 0 && (
        <>
          <div className="server-rows">
            {servers.map((server) => {
              const status = statuses[server.address];
              const online = status?.state === "online" ? status : null;
              return (
                <div key={server.address} className="server-row">
                  <ServerIcon name={server.name} icon={server.icon ?? online?.icon ?? null} />
                  <span className="server-text">
                    <b>{server.name || server.address}</b>
                    <small title={online?.motd || undefined}>
                      <i className={`server-dot${online ? " is-online" : status ? "" : " is-checking"}`} />
                      <span className="numeric">
                        {online ? `${online.players_online.toLocaleString()} online` : status ? "Offline" : "Checking…"}
                      </span>
                      {" · "}
                      {server.address}
                    </small>
                  </span>
                  <button
                    className="join"
                    disabled={!online || launch.phase.at !== "idle"}
                    aria-label={`Join ${server.name || server.address}`}
                    onClick={() => join(server.address)}
                  >
                    Join
                  </button>
                </div>
              );
            })}
          </div>
          <p className="hint">Join starts {instance.name} straight into the server.</p>
        </>
      )}
    </div>
  );
}

/** A server being added (`position` null) or edited, as typed so far. */
type ServerForm = { position: number | null; expected: string; name: string; address: string };

/** The game's own Add Server fields: a name, which may be left empty, and an address. */
function ServerFormView(props: {
  form: ServerForm;
  busy: boolean;
  onChange: (form: ServerForm) => void;
  onCancel: () => void;
  onSave: (form: ServerForm) => void;
}) {
  const { form } = props;
  const adding = form.position === null;
  return (
    <form
      className="server-form"
      onSubmit={(e) => {
        e.preventDefault();
        props.onSave(form);
      }}
    >
      <label>
        <span>Name</span>
        <input
          className="input"
          value={form.name}
          maxLength={32}
          placeholder="Minecraft Server"
          onChange={(e) => props.onChange({ ...form, name: e.target.value })}
        />
      </label>
      <label>
        <span>Address</span>
        <input
          className="input"
          value={form.address}
          maxLength={128}
          placeholder="play.example.net"
          autoFocus={adding}
          onChange={(e) => props.onChange({ ...form, address: e.target.value })}
        />
      </label>
      <span className="server-form-actions">
        <button type="button" className="button" disabled={props.busy} onClick={props.onCancel}>
          Cancel
        </button>
        <button type="submit" className="button button-go" disabled={props.busy || form.address.trim() === ""}>
          {adding ? "Add" : "Save"}
        </button>
      </span>
    </form>
  );
}

/** The server's own icon, or its initial on a tile when it has none. */
function ServerIcon(props: { name: string; icon: string | null }) {
  if (props.icon) {
    return <img className="server-icon" src={`data:image/png;base64,${props.icon}`} alt="" />;
  }
  return (
    <span className="server-icon server-initial" aria-hidden>
      {(props.name.trim()[0] ?? "?").toUpperCase()}
    </span>
  );
}

/** How often a running session's play time is asked for again. */
const RUNNING_REFRESH_MS = 30_000;

/**
 * The selected instance at a glance: what ash is running in it, how long it
 * has been played, and the player's own mods.
 */
function ThisInstanceCard(props: {
  instance: Instance;
  /** Asked again whenever this changes, so a session that starts or ends shows. */
  phase: Launch["phase"]["at"];
  onOpen: (id: InstanceId) => void;
}) {
  const { instance, phase } = props;
  const [glance, setGlance] = useState<InstanceGlance | null>(null);

  useEffect(() => {
    let live = true;
    const ask = () =>
      api
        .instanceGlance(instance.id)
        .then((g) => live && setGlance(g))
        .catch(() => live && setGlance(null));
    void ask();
    // A running session counts up, so it is asked for again while it runs.
    const timer = phase === "running" ? setInterval(() => void ask(), RUNNING_REFRESH_MS) : undefined;
    return () => {
      live = false;
      clearInterval(timer);
    };
  }, [instance.id, phase]);

  // Until the first answer, the card holds its place without guessing.
  const chips = glance?.features.state === "on" && glance.features.features.length > 0;
  const shown = glance && (
    <div className="glance-rows">
      {/* Chips go under their label: beside it, a narrow card stacks them one per line. */}
      <div className={chips ? "kv kv-stacked" : "kv"}>
        <span>ash features on</span>
        <Features features={glance.features} />
      </div>
      <div className="kv">
        <span>Play time</span>
        <b className="numeric">{describeDuration(glance.played_ms)}</b>
      </div>
      <div className="kv">
        <span>Last session</span>
        <b className="numeric">{describeSession(glance.last_session)}</b>
      </div>
      <div className="kv">
        <span>Mods</span>
        <b>{glance.mods.length ? glance.mods.join(", ") : "None"}</b>
      </div>
      {glance.features.state !== "no_client" && (
        <p className="hint">Right Shift changes these in game.</p>
      )}
    </div>
  );

  return (
    <div className="card glance-card">
      <h3>
        This instance <small>{instance.name}</small>
      </h3>
      {shown}
      <button className="mini glance-open" onClick={() => props.onOpen(instance.id)}>
        <Icon name="settings" />
        Instance settings
      </button>
    </div>
  );
}

function Features(props: { features: AshFeatures }) {
  const { features } = props;
  if (features.state === "no_client") return <b className="quiet">Vanilla, no ash client</b>;
  if (features.state === "not_reported") return <b className="quiet">Shown after you play</b>;
  if (features.features.length === 0) return <b>None</b>;
  return (
    <span className="feature-chips">
      {features.features.map((name) => (
        <span key={name} className="feature-chip">
          {name}
        </span>
      ))}
    </span>
  );
}

/** "2 hours ago · 48 min", or "Playing now · 12 min" while it runs. */
function describeSession(session: Session | null): string {
  if (!session) return "Never played";
  if (session.ended_ms === null) return `Playing now · ${describeDuration(Date.now() - session.started_ms)}`;
  return `${describeAge(session.ended_ms)} · ${describeDuration(session.ended_ms - session.started_ms)}`;
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
  // Which instances load the player's own mods: worth seeing from here,
  // because that is where to look first when one of them breaks.
  const [withMods, setWithMods] = useState<Set<InstanceId>>(new Set());
  useEffect(() => {
    let live = true;
    Promise.all(
      props.instances.map((i) =>
        api
          .overrides(i.id)
          .then((o) => (o.third_party_mods ? i.id : null))
          .catch(() => null),
      ),
    ).then((ids) => live && setWithMods(new Set(ids.filter((id): id is InstanceId => id !== null))));
    return () => {
      live = false;
    };
  }, [props.instances]);

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
                {describeKind(instance)}
                {withMods.has(instance.id) && " · your mods"} ·{" "}
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
