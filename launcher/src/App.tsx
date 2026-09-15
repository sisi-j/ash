import { useCallback, useEffect, useMemo, useState } from "react";
import {
  api,
  describeAge,
  describeBytes,
  isUiError,
  type Account,
  type Accounts as AccountList,
  type Catalogue,
  type DeletionPreview,
  type Instance,
  type InstanceId,
  type Loader,
  type UiError,
  LOADERS,
  LOADER_LABELS,
} from "./api";
import { Accounts } from "./Accounts";
import { Play } from "./Play";
import { Settings } from "./Settings";
import { SignIn } from "./SignIn";

/**
 * What the main area is showing instead of an instance.
 *
 * One value rather than two booleans: "managing accounts" and "signing in"
 * are steps in one flow, and two flags would allow a fourth state that means
 * nothing.
 */
type Overlay = null | "accounts" | "sign-in";

export default function App() {
  const [instances, setInstances] = useState<Instance[]>([]);
  const [catalogue, setCatalogue] = useState<Catalogue | null>(null);
  const [selectedId, setSelectedId] = useState<InstanceId | null>(null);
  const [error, setError] = useState<UiError | null>(null);
  const [creating, setCreating] = useState(false);
  const [pendingDeletion, setPendingDeletion] = useState<DeletionPreview | null>(null);
  const [accounts, setAccounts] = useState<AccountList | null>(null);
  const [overlay, setOverlay] = useState<Overlay>(null);
  const [switching, setSwitching] = useState(false);

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

  const selectAccount = useCallback(
    async (profileId: string) => {
      setSwitching(true);
      try {
        setAccounts(await api.selectAccount(profileId));
      } catch (e) {
        fail(e);
      } finally {
        setSwitching(false);
      }
    },
    [fail],
  );

  const removeAccount = useCallback(
    async (profileId: string) => {
      setSwitching(true);
      try {
        const left = await api.removeAccount(profileId);
        setAccounts(left);
        // Nobody left to manage, so the only useful screen is signing in.
        if (left.accounts.length === 0) setOverlay("sign-in");
      } catch (e) {
        fail(e);
      } finally {
        setSwitching(false);
      }
    },
    [fail],
  );

  const selected = useMemo(
    () => instances.find((i) => i.id === selectedId) ?? null,
    [instances, selectedId],
  );

  const create = useCallback(
    async (name: string, versionId: string, loader: Loader) => {
      try {
        const made = await api.createInstance(name, versionId, loader);
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
          onClick={() =>
            setOverlay((current) =>
              current === "accounts" ? null : signedIn ? "accounts" : "sign-in",
            )
          }
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

        {overlay === "sign-in" || (!signedIn && accounts !== null && overlay === null) ? (
          <SignIn
            onSignedIn={() => {
              // Back to the list, so adding a third account is one click and
              // it is obvious who is now armed to play.
              api.accounts()
                .then((loaded) => {
                  setAccounts(loaded);
                  setOverlay(loaded.accounts.length > 1 ? "accounts" : null);
                })
                .catch(fail);
            }}
            onCancel={signedIn ? () => setOverlay("accounts") : undefined}
          />
        ) : overlay === "accounts" && accounts ? (
          <Accounts
            accounts={accounts}
            busy={switching}
            onSelect={selectAccount}
            onRemove={removeAccount}
            onAdd={() => setOverlay("sign-in")}
            onClose={() => setOverlay(null)}
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
            playingAs={active}
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
  playingAs: Account | null;
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
          <dt>Loader</dt>
          {/* Shown, never edited: it was chosen when the instance was
              created and there is no operation that changes it. */}
          <dd>{LOADER_LABELS[instance.loader]}</dd>
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
        <button className="button" onClick={() => void api.revealLog()}>
          Show log
        </button>
        <button className="button button-danger" onClick={props.onDelete}>
          Delete
        </button>
      </div>

      <h3 className="panel-title">Play</h3>
      <Play id={instance.id} playingAs={props.playingAs} />

      <h3 className="panel-title">This machine</h3>
      <Settings key={instance.id} id={instance.id} />
    </section>
  );
}

// ---- create ----------------------------------------------------------------

function NewInstance(props: {
  catalogue: Catalogue | null;
  onCancel: () => void;
  onCreate: (name: string, versionId: string, loader: Loader) => void;
}) {
  const { catalogue } = props;
  const [name, setName] = useState("");
  const [versionId, setVersionId] = useState<string>("");
  const [loader, setLoader] = useState<Loader>(LOADERS[0]);
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

      <h3 className="panel-title">Loader</h3>
      <div className="chips">
        {LOADERS.map((option) => (
          <button
            key={option}
            className={`chip${option === loader ? " is-selected" : ""}`}
            onClick={() => setLoader(option)}
          >
            {LOADER_LABELS[option]}
          </button>
        ))}
      </div>
      {/* Said before the choice rather than discovered after it: an
          instance's loader and version are what its game directory is built
          around, and neither can be changed later. */}
      <p className="note muted">
        The loader and the version are fixed once an instance is created.
      </p>

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
        <button
          className="button"
          disabled={!ready}
          onClick={() => props.onCreate(name, versionId, loader)}
        >
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
