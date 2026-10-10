import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  api,
  appWindow,
  describeAge,
  isUiError,
  onAshAccount,
  type Accounts as AccountList,
  type AshAccountStatus,
  type Catalogue,
  type Instance,
  type InstanceId,
  type LauncherPreferences,
  type Loader,
  type UiError,
  LOADER_LABELS,
} from "./api";
import { AshAccountNotice } from "./AshAccount";
import { Icon } from "./icons";
import { InstancePage } from "./InstancePage";
import { useLaunch } from "./launch";
import { LaunchFailure } from "./LaunchFailure";
import { PlayPage } from "./PlayPage";
import { SettingsPage } from "./SettingsPage";
import { EmptyPage, Sidebar, type Page } from "./Sidebar";
import { SignIn } from "./SignIn";
import { TitleBar } from "./TitleBar";

/**
 * What the Play page is showing: home, with LAUNCH GAME; the selected
 * instance's own page, from its cog; or the form for a new one.
 */
type PlayView = "home" | "instance" | "new";

export default function App() {
  const [instances, setInstances] = useState<Instance[]>([]);
  const [catalogue, setCatalogue] = useState<Catalogue | null>(null);
  const [selectedId, setSelectedId] = useState<InstanceId | null>(null);
  const [error, setError] = useState<UiError | null>(null);
  const [playView, setPlayView] = useState<PlayView>("home");
  const [preferences, setPreferences] = useState<LauncherPreferences | null>(null);
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
    api.launcherPreferences().then(setPreferences).catch(fail);
  }, [reloadInstances, fail]);

  const signedIn = (accounts?.accounts.length ?? 0) > 0;
  const active = accounts?.accounts.find((a) => a.profile_id === accounts.active) ?? null;

  // The active player's ash account (spec 0004): signed in by the backend
  // side in the background, never on the way to playing. When a sign-in
  // finishes, the account list may carry a new notice and the status moves.
  const [ashStatus, setAshStatus] = useState<AshAccountStatus | null>(null);
  const activeId = active?.profile_id ?? null;
  useEffect(() => {
    if (activeId === null) return setAshStatus(null);
    api.ashAccountStatus(activeId).then(setAshStatus).catch(() => setAshStatus(null));
  }, [activeId]);
  useEffect(() => {
    const stop = onAshAccount((event) => {
      api.accounts().then(setAccounts).catch(fail);
      if (event.profile_id === activeId) setAshStatus(event.status);
    });
    return () => void stop.then((unlisten) => unlisten());
  }, [activeId, fail]);
  const deleteAshAccount = useCallback(async () => {
    if (activeId === null) return false;
    try {
      setAccounts(await api.deleteAshAccount(activeId));
      setAshStatus({ state: "deleted" });
      return true;
    } catch {
      return false;
    }
  }, [activeId]);
  const dismissAshNotice = useCallback(
    async (profileId: string) => {
      try {
        setAccounts(await api.dismissAshAccountNotice(profileId));
      } catch (e) {
        fail(e);
      }
    },
    [fail],
  );

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
  const launch = useLaunch(selected?.id ?? null);

  // What the player chose for once the game is up. Only on a launch this
  // window watched succeed: never on a failed one, which leaves ash open on
  // the reason, and never on reopening ash onto a game already running.
  const beforePhase = useRef(launch.phase.at);
  const onGameStart = preferences?.on_game_start ?? "keep_open";
  useEffect(() => {
    const started = beforePhase.current === "working" && launch.phase.at === "running";
    beforePhase.current = launch.phase.at;
    if (!started) return;
    if (onGameStart === "minimise") void appWindow.minimise();
    if (onGameStart === "close") void appWindow.close();
  }, [launch.phase.at, onGameStart]);

  const savePreferences = useCallback(
    async (next: LauncherPreferences) => {
      setPreferences(next);
      try {
        setPreferences(await api.setLauncherPreferences(next));
      } catch (e) {
        fail(e);
      }
    },
    [fail],
  );

  const create = useCallback(
    async (name: string, versionId: string, loader: Loader) => {
      try {
        const made = await api.createInstance(name, versionId, loader);
        setPlayView("home");
        await reloadInstances(made.id);
      } catch (e) {
        fail(e);
      }
    },
    [reloadInstances, fail],
  );

  /** Renames an instance; why not, if ash refused, for the page to say on its row. */
  const rename = useCallback(
    async (id: InstanceId, name: string) => {
      try {
        await api.renameInstance(id, name);
        await reloadInstances(id);
        return null;
      } catch (e) {
        return isUiError(e) ? e.message : "ash could not rename it.";
      }
    },
    [reloadInstances],
  );

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
          if (next === "play") setPlayView("home");
        }}
      />

      <main className="main">
        {error && (
          <p className="error" role="alert" onClick={() => setError(null)}>
            {error.message}
          </p>
        )}

        {active?.ash_account_notice && !signingIn && (
          <AshAccountNotice account={active} onDismiss={() => void dismissAshNotice(active.profile_id)} />
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
          // machine are on its own page, from its cog.
          <SettingsPage
            preferences={preferences}
            onChange={savePreferences}
            ashAccount={active && ashStatus ? { username: active.username, status: ashStatus } : null}
            onDeleteAshAccount={deleteAshAccount}
          />
        ) : playView === "new" ? (
          <section className="page">
            <BackTo onBack={() => setPlayView("home")} />
            <NewInstance catalogue={catalogue} onCancel={() => setPlayView("home")} onCreate={create} />
          </section>
        ) : playView === "instance" && selected ? (
          <InstancePage
            key={selected.id}
            instance={selected}
            launch={launch}
            onRename={(name) => rename(selected.id, name)}
            onDeleted={() => {
              setPlayView("home");
              void reloadInstances();
            }}
            onBack={() => setPlayView("home")}
          />
        ) : (
          <PlayPage
            player={active}
            instances={instances}
            selected={selected}
            launch={launch}
            // Silent until ash-core has said: the default is its to give.
            sounds={preferences?.launch_sounds ?? false}
            onSelect={setSelectedId}
            onOpen={(id) => {
              setSelectedId(id);
              setPlayView("instance");
            }}
            onNew={() => setPlayView("new")}
          />
        )}
      </main>

      {(launch.phase.at === "failed" || launch.phase.at === "crashed") && (
        <LaunchFailure
          phase={launch.phase}
          onRetry={launch.retry}
          onPlayWithoutMods={launch.playWithoutMods}
          onClose={launch.dismiss}
        />
      )}
    </div>
  );
}

/** Back to the Play page, from a page reached from it. */
function BackTo(props: { onBack: () => void }) {
  return (
    <button className="back" onClick={props.onBack}>
      <span className="back-arrow">
        <Icon name="back" />
      </span>
      Play
    </button>
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
