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
  LOADER_LABELS,
} from "./api";
import { Icon } from "./icons";
import { Play } from "./Play";
import { Settings } from "./Settings";
import { EmptyPage, Sidebar, type Page } from "./Sidebar";
import { SignIn } from "./SignIn";
import { TitleBar } from "./TitleBar";

export default function App() {
  const [instances, setInstances] = useState<Instance[]>([]);
  const [catalogue, setCatalogue] = useState<Catalogue | null>(null);
  const [selectedId, setSelectedId] = useState<InstanceId | null>(null);
  const [error, setError] = useState<UiError | null>(null);
  const [creating, setCreating] = useState(false);
  const [pendingDeletion, setPendingDeletion] = useState<DeletionPreview | null>(null);
  const [accounts, setAccounts] = useState<AccountList | null>(null);
  const [page, setPage] = useState<Page>("play");
  // Adding an account, from the title bar's menu. Signing in with no
  // account at all needs no flag: there is nothing else ash can do.
  const [signingIn, setSigningIn] = useState(false);
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
        // With nobody left, signing in is all the main area shows.
        setAccounts(await api.removeAccount(profileId));
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
      <TitleBar
        accounts={accounts}
        busy={switching}
        onSelect={selectAccount}
        onSignOut={removeAccount}
        onAdd={() => setSigningIn(true)}
      />
      <Sidebar
        page={page}
        onOpen={(next) => {
          setPage(next);
          setSigningIn(false);
        }}
      />

      <main className="main">
        {error && (
          <p className="error" role="alert" onClick={() => setError(null)}>
            {error.message}
          </p>
        )}

        {signingIn || (!signedIn && accounts !== null) ? (
          <SignIn
            onSignedIn={() => {
              // Back to where the player was, now playing as whoever just
              // signed in: ash makes a new account the active one.
              api.accounts()
                .then((loaded) => {
                  setAccounts(loaded);
                  setSigningIn(false);
                })
                .catch(fail);
            }}
            onCancel={signedIn ? () => setSigningIn(false) : undefined}
          />
        ) : page === "mods" ? (
          // Until third-party mods can be added to an instance (#46).
          <EmptyPage
            page="mods"
            headline="Mods are coming soon"
            detail="Third-party mods you add to an instance will show here."
          />
        ) : page === "news" ? (
          <EmptyPage
            page="news"
            headline="News is coming soon"
            detail="News posts and patch notes will show here."
          />
        ) : page === "settings" ? (
          // The launcher's own settings. Each instance's settings for this
          // machine are on its Play page, as they were.
          <EmptyPage
            page="settings"
            headline="Settings are coming soon"
            detail="Default memory, what ash does when the game starts, launch sounds and language will be set here."
          />
        ) : (
          <div className="play">
            <aside className="instances">
              <h3 className="panel-title">Instances</h3>
              <ul className="instance-list">
                {instances.map((instance) => (
                  <li key={instance.id}>
                    <button
                      className={`instance-row${instance.id === selectedId && !creating ? " is-selected" : ""}`}
                      onClick={() => {
                        setSelectedId(instance.id);
                        setCreating(false);
                      }}
                    >
                      <span className="instance-name">{instance.name}</span>
                      <span className="numeric instance-version">
                        {instance.version_id}
                        {/* Only when there is something to say. Every instance has
                            a loader, but printing "Vanilla" on every row is a word
                            that distinguishes nothing. */}
                        {instance.loader !== "vanilla" && (
                          <span className="instance-loader">{LOADER_LABELS[instance.loader]}</span>
                        )}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
              <button className="button instance-new" onClick={() => setCreating(true)}>
                <Icon name="plus" />
                New instance
              </button>
            </aside>

            <div className="play-main">
              {creating ? (
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
            </div>
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
        {instance.loader !== "vanilla" && (
          <div>
            <dt>ash settings</dt>
            {/* The default key, said once where a new player looks. It is
                rebindable in the game's Controls, and the launcher never
                reads the game's own options, so a player who moved it knows
                where it went. */}
            <dd>Right Shift, in game</dd>
          </div>
        )}
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
  const [loader, setLoader] = useState<Loader>("vanilla");
  const [loaders, setLoaders] = useState<Loader[]>(["vanilla"]);
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

  // Which loaders exist is ash's to answer, not the UI's: it pins the ones
  // it has tested per version target, and offering one it cannot install
  // would be offering an instance that could never launch.
  useEffect(() => {
    if (!versionId) return;
    let live = true;
    api
      .loadersFor(versionId)
      .then((offered) => {
        if (!live) return;
        setLoaders(offered);
        // The version target just changed, and the loader that was chosen
        // may not run on this one.
        setLoader((current) => (offered.includes(current) ? current : "vanilla"));
      })
      .catch(() => {
        // Deliberately nothing. Which loaders exist is ash's answer to give,
        // and inventing one here would be a launcher decision taken where
        // nothing can test it. The picker keeps showing what it last had.
      });
    return () => {
      live = false;
    };
  }, [versionId]);

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
        {loaders.map((option) => (
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
