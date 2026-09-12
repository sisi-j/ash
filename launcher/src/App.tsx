import { useCallback, useEffect, useMemo, useState } from "react";
import {
  api,
  describeAge,
  describeBytes,
  isUiError,
  type Accounts,
  type Catalogue,
  type DeletionPreview,
  type Instance,
  type InstanceId,
  type UiError,
} from "./api";
import { Play } from "./Play";
import { SignIn } from "./SignIn";

export default function App() {
  const [instances, setInstances] = useState<Instance[]>([]);
  const [catalogue, setCatalogue] = useState<Catalogue | null>(null);
  const [selectedId, setSelectedId] = useState<InstanceId | null>(null);
  const [error, setError] = useState<UiError | null>(null);
  const [creating, setCreating] = useState(false);
  const [pendingDeletion, setPendingDeletion] = useState<DeletionPreview | null>(null);
  const [accounts, setAccounts] = useState<Accounts | null>(null);
  const [signingIn, setSigningIn] = useState(false);

  const fail = useCallback((e: unknown) => {
    setError(
      isUiError(e)
        ? e
        : { kind: "unknown", message: "Something went wrong.", retryable: true },
    );
  }, []);

  const reloadInstances = useCallback(
    async (select?: InstanceId) => {
      try {
        const loaded = await api.instances();
        setInstances(loaded);
        setSelectedId((current) => select ?? (loaded.some((i) => i.id === current) ? current : loaded[0]?.id ?? null));
      } catch (e) {
        fail(e);
      }
    },
    [fail],
  );

  useEffect(() => {
    void reloadInstances();
    api.catalogue().then(setCatalogue).catch(fail);
    api.accounts().then(setAccounts).catch(fail);
  }, [reloadInstances, fail]);

  const signedIn = (accounts?.accounts.length ?? 0) > 0;
  const active = accounts?.accounts.find((a) => a.profile_id === accounts.active) ?? null;

  const selected = useMemo(
    () => instances.find((i) => i.id === selectedId) ?? null,
    [instances, selectedId],
  );

  const create = useCallback(
    async (name: string, versionId: string) => {
      try {
        const made = await api.createInstance(name, versionId);
        setCreating(false);
        await reloadInstances(made.id);
      } catch (e) {
        fail(e);
      }
    },
    [reloadInstances, fail],
  );

  const rename = useCallback(
    async (id: InstanceId, name: string) => {
      try {
        await api.renameInstance(id, name);
        await reloadInstances(id);
      } catch (e) {
        fail(e);
      }
    },
    [reloadInstances, fail],
  );

  const askToDelete = useCallback(
    async (id: InstanceId) => {
      try {
        setPendingDeletion(await api.previewDeletion(id));
      } catch (e) {
        fail(e);
      }
    },
    [fail],
  );

  const confirmDelete = useCallback(async () => {
    if (!pendingDeletion) return;
    try {
      await api.deleteInstance(pendingDeletion.instance.id);
      setPendingDeletion(null);
      await reloadInstances();
    } catch (e) {
      fail(e);
    }
  }, [pendingDeletion, reloadInstances, fail]);

  return (
    <div className="shell">
      <nav className="rail">
        <h1 className="wordmark">ash</h1>

        <button
          className="account"
          onClick={() => setSigningIn(true)}
          title={active ? "Manage accounts" : "Sign in"}
        >
          {active?.skin_url ? (
            <span
              className="face"
              style={{ backgroundImage: `url(${active.skin_url})` }}
              aria-hidden="true"
            />
          ) : (
            <span className="face face-empty" aria-hidden="true" />
          )}
          <span className="account-name">{active ? active.username : "Sign in"}</span>
        </button>

        <ul className="rail-list">
          {instances.map((instance) => (
            <li key={instance.id}>
              <button
                className={`rail-item${instance.id === selectedId ? " is-selected" : ""}`}
                onClick={() => setSelectedId(instance.id)}
              >
                <span className="rail-name">{instance.name}</span>
                <span className="numeric rail-version">{instance.version_id}</span>
              </button>
            </li>
          ))}
        </ul>

        <button className="button rail-new" onClick={() => setCreating(true)}>
          New instance
        </button>
      </nav>

      <main className="main">
        {error && (
          <p className="error" role="alert" onClick={() => setError(null)}>
            {error.message}
          </p>
        )}

        {signingIn ? (
          <SignIn
            onSignedIn={() => {
              setSigningIn(false);
              api.accounts().then(setAccounts).catch(fail);
            }}
            onCancel={signedIn ? () => setSigningIn(false) : undefined}
          />
        ) : !signedIn && accounts !== null ? (
          <SignIn
            onSignedIn={() => {
              api.accounts().then(setAccounts).catch(fail);
            }}
          />
        ) : creating ? (
          <NewInstance
            catalogue={catalogue}
            onCancel={() => setCreating(false)}
            onCreate={create}
          />
        ) : selected ? (
          <InstanceDetail
            instance={selected}
            onRename={rename}
            onReveal={() => api.revealGameDirectory(selected.id).catch(fail)}
            onDelete={() => askToDelete(selected.id)}
          />
        ) : (
          <div className="empty">
            <p className="muted">No instances yet.</p>
            <button className="button" onClick={() => setCreating(true)}>
              Create your first
            </button>
          </div>
        )}
      </main>

      {pendingDeletion && (
        <DeleteDialog
          preview={pendingDeletion}
          onCancel={() => setPendingDeletion(null)}
          onConfirm={confirmDelete}
        />
      )}
    </div>
  );
}

// ---- instance detail -------------------------------------------------------

function InstanceDetail(props: {
  instance: Instance;
  onRename: (id: InstanceId, name: string) => void;
  onReveal: () => void;
  onDelete: () => void;
}) {
  const { instance } = props;
  const [draft, setDraft] = useState(instance.name);

  useEffect(() => setDraft(instance.name), [instance.id, instance.name]);

  const dirty = draft.trim() !== instance.name && draft.trim().length > 0;

  return (
    <section className="detail">
      <input
        className="detail-name"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && dirty && props.onRename(instance.id, draft)}
        aria-label="Instance name"
      />

      <dl className="facts">
        <div>
          <dt>Version</dt>
          <dd className="numeric">{instance.version_id}</dd>
        </div>
        <div>
          <dt>Last played</dt>
          <dd>{instance.last_played_ms ? describeAge(instance.last_played_ms) : "never"}</dd>
        </div>
        <div>
          <dt>Created</dt>
          <dd>{describeAge(instance.created_at_ms)}</dd>
        </div>
      </dl>

      <div className="actions">
        <button className="button" disabled={!dirty} onClick={() => props.onRename(instance.id, draft)}>
          Save name
        </button>
        <button className="button" onClick={props.onReveal}>
          Open folder
        </button>
        <button className="button button-danger" onClick={props.onDelete}>
          Delete
        </button>
      </div>

      <h3 className="panel-title">Play</h3>
      <Play id={instance.id} />
    </section>
  );
}

// ---- create ----------------------------------------------------------------

function NewInstance(props: {
  catalogue: Catalogue | null;
  onCancel: () => void;
  onCreate: (name: string, versionId: string) => void;
}) {
  const { catalogue } = props;
  const [name, setName] = useState("");
  const [versionId, setVersionId] = useState<string>("");
  const [showSnapshots, setShowSnapshots] = useState(false);

  const pinned = useMemo(
    () => catalogue?.versions.filter((v) => v.first_class) ?? [],
    [catalogue],
  );

  const listed = useMemo(() => {
    if (!catalogue) return [];
    return catalogue.versions.filter(
      (v) => v.kind === "release" || (showSnapshots && v.kind === "snapshot"),
    );
  }, [catalogue, showSnapshots]);

  useEffect(() => {
    if (!versionId && pinned[0]) setVersionId(pinned[0].id);
  }, [pinned, versionId]);

  const ready = name.trim().length > 0 && versionId.length > 0;

  return (
    <section className="detail">
      <h2 className="heading">New instance</h2>

      <input
        className="detail-name"
        placeholder="Name it"
        value={name}
        onChange={(e) => setName(e.target.value)}
        aria-label="Instance name"
        autoFocus
      />

      {pinned.length > 0 && (
        <>
          <h3 className="panel-title">Built for</h3>
          <div className="chips">
            {pinned.map((v) => (
              <button
                key={v.id}
                className={`chip numeric${v.id === versionId ? " is-selected" : ""}`}
                onClick={() => setVersionId(v.id)}
              >
                {v.id}
              </button>
            ))}
          </div>
        </>
      )}

      <div className="panel-head">
        <h3 className="panel-title">All versions</h3>
        <label className="toggle">
          <input
            type="checkbox"
            checked={showSnapshots}
            onChange={(e) => setShowSnapshots(e.target.checked)}
          />
          Snapshots
        </label>
      </div>

      <select
        className="select numeric"
        value={versionId}
        onChange={(e) => setVersionId(e.target.value)}
        size={8}
        aria-label="Version"
      >
        {listed.map((v) => (
          <option key={v.id} value={v.id}>
            {v.id}
          </option>
        ))}
      </select>

      <div className="actions">
        <button className="button" disabled={!ready} onClick={() => props.onCreate(name, versionId)}>
          Create
        </button>
        <button className="button" onClick={props.onCancel}>
          Cancel
        </button>
      </div>

      {catalogue && (
        <p className="note muted">
          {catalogue.source === "cache" ? "Cached · " : "Updated "}
          {describeAge(catalogue.fetched_at_ms)}
        </p>
      )}
    </section>
  );
}

// ---- delete ----------------------------------------------------------------

function DeleteDialog(props: {
  preview: DeletionPreview;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { preview } = props;

  return (
    <div className="scrim" role="dialog" aria-modal="true">
      <div className="dialog">
        <h2 className="heading">Delete “{preview.instance.name}”?</h2>

        <p className="muted">
          This cannot be undone. {describeBytes(preview.total_bytes)} will be removed.
        </p>

        {preview.worlds.length > 0 ? (
          <>
            <h3 className="panel-title">
              {preview.worlds.length} world{preview.worlds.length === 1 ? "" : "s"} will be lost
            </h3>
            <ul className="worlds">
              {preview.worlds.map((world) => (
                <li key={world}>{world}</li>
              ))}
            </ul>
          </>
        ) : (
          <p className="muted">No worlds in this instance.</p>
        )}

        <p className="muted">
          {preview.resource_packs} resource pack{preview.resource_packs === 1 ? "" : "s"},{" "}
          {preview.screenshots} screenshot{preview.screenshots === 1 ? "" : "s"}.
          Shared game files in the depot are not touched.
        </p>

        <div className="actions">
          <button className="button button-danger" onClick={props.onConfirm}>
            Delete permanently
          </button>
          <button className="button" onClick={props.onCancel} autoFocus>
            Keep it
          </button>
        </div>
      </div>
    </div>
  );
}
