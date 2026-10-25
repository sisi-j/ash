import { useEffect, useRef, useState } from "react";
import {
  api,
  isUiError,
  LOADER_LABELS,
  type Instance,
  type InstanceId,
  type ModEntry,
  type ModHit,
} from "./api";
import { PAGES } from "./Sidebar";

/** How long typing pauses before a search goes out: Modrinth allows 300 a minute. */
const DEBOUNCE_MS = 300;

type Done = { names: string[]; turnedOn: boolean; optional: string[] };

/**
 * Mods from Modrinth (spec 0005, ADR-0026): searched for one instance's
 * version and loader, so nothing that can't load is ever offered, and
 * installed with what each requires.
 */
export function ModsPage(props: {
  instances: Instance[];
  selected: InstanceId | null;
  onSelect: (id: InstanceId) => void;
  onNewInstance: () => void;
}) {
  const instance = props.instances.find((i) => i.id === props.selected) ?? null;
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<ModHit[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [installing, setInstalling] = useState<string | null>(null);
  const [failed, setFailed] = useState<{
    project: string;
    message: string;
  } | null>(null);
  const [done, setDone] = useState<Done | null>(null);
  const asked = useRef(0);
  const [view, setView] = useState<"installed" | "browse">("installed");
  // Bumped by each install, so the installed list reads the folder again.
  const [installs, setInstalls] = useState(0);

  const modded = instance !== null && instance.loader !== "vanilla";

  // A new search for each instance and each pause in typing; only the
  // latest answer is shown, however the answers arrive.
  useEffect(() => {
    if (!instance || !modded) return;
    const ask = ++asked.current;
    setLoading(true);
    const timer = window.setTimeout(() => {
      api
        .searchMods(instance.id, query, 0)
        .then((found) => {
          if (ask !== asked.current) return;
          setHits(found.hits);
          setTotal(found.total);
          setProblem(null);
        })
        .catch(
          (e) =>
            ask === asked.current &&
            setProblem(
              isUiError(e) ? e.message : "Modrinth couldn't be searched.",
            ),
        )
        .finally(() => ask === asked.current && setLoading(false));
    }, DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [instance?.id, modded, query]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    setDone(null);
    setFailed(null);
  }, [instance?.id]);

  const more = async () => {
    if (!instance) return;
    try {
      const found = await api.searchMods(instance.id, query, hits.length);
      setHits((current) => [...current, ...found.hits]);
    } catch (e) {
      setProblem(isUiError(e) ? e.message : "Modrinth couldn't be searched.");
    }
  };

  const install = async (hit: ModHit) => {
    if (!instance) return;
    setInstalling(hit.project_id);
    setFailed(null);
    setDone(null);
    try {
      const outcome = await api.installMod(instance.id, hit.project_id);
      setDone({
        names: outcome.installed.map((m) => m.title),
        turnedOn: outcome.mods_turned_on,
        optional: outcome.optional,
      });
      setInstalls((n) => n + 1);
      // The mod and everything it brought with it.
      const now = new Set(outcome.installed.map((m) => m.project_id));
      setHits((current) =>
        current.map((h) =>
          now.has(h.project_id) ? { ...h, installed: true } : h,
        ),
      );
    } catch (e) {
      setFailed({
        project: hit.project_id,
        message: isUiError(e)
          ? e.message
          : `${hit.title} couldn't be installed.`,
      });
    } finally {
      setInstalling(null);
    }
  };

  return (
    <section className="page mods-page">
      <h2 className="page-title">{PAGES.mods.label}</h2>

      {props.instances.length > 0 && (
        <div
          className="chips mods-instances"
          role="radiogroup"
          aria-label="Instance"
        >
          {props.instances.map((i) => (
            <button
              key={i.id}
              className={`chip${i.id === props.selected ? " is-selected" : ""}`}
              role="radio"
              aria-checked={i.id === props.selected}
              onClick={() => props.onSelect(i.id)}
            >
              {i.name}
              {i.name !== i.version_id && (
                <span className="chip-detail numeric">{i.version_id}</span>
              )}
            </button>
          ))}
        </div>
      )}

      {!instance ? (
        <p className="notice">
          Make an instance first, then add mods to it here.
        </p>
      ) : !modded ? (
        <div className="notice mods-vanilla" role="status">
          <p>
            Mods need a Fabric instance. {instance.name} is vanilla Minecraft.
          </p>
          <button className="button" onClick={props.onNewInstance}>
            New instance
          </button>
        </div>
      ) : (
        <>
          <div className="chips mods-views" role="tablist" aria-label="Mods">
            <button
              className={`chip${view === "installed" ? " is-selected" : ""}`}
              role="tab"
              aria-selected={view === "installed"}
              onClick={() => setView("installed")}
            >
              Installed
            </button>
            <button
              className={`chip${view === "browse" ? " is-selected" : ""}`}
              role="tab"
              aria-selected={view === "browse"}
              onClick={() => setView("browse")}
            >
              Browse Modrinth
            </button>
          </div>
          {view === "installed" ? (
            <InstalledList
              instance={instance}
              installs={installs}
              onBrowse={() => setView("browse")}
            />
          ) : (
            <>
              <input
                className="mods-search"
                type="search"
                placeholder={`Search Modrinth for ${LOADER_LABELS[instance.loader]} ${instance.version_id} mods`}
                aria-label="Search mods"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />

              {done && (
                <p className="notice mods-done" role="status">
                  Installed {listed(done.names)}.
                  {done.turnedOn
                    ? ` Your mods are now on for ${instance.name}.`
                    : ""}
                  {done.optional.length > 0
                    ? ` Optional extras: ${listed(done.optional)}.`
                    : ""}
                </p>
              )}
              {problem && (
                <p className="error" role="alert">
                  {problem}
                </p>
              )}

              <ul className="mods-results" aria-busy={loading}>
                {hits.map((hit) => (
                  <li key={hit.project_id} className="mod-row">
                    {hit.icon_url ? (
                      <img className="mod-icon" src={hit.icon_url} alt="" />
                    ) : (
                      <span className="mod-icon" />
                    )}
                    <div className="mod-text">
                      <b>{hit.title}</b>
                      <span>{hit.description}</span>
                      <small className="numeric">
                        {downloads(hit.downloads)} downloads
                      </small>
                      {failed?.project === hit.project_id && (
                        <span className="mod-failed" role="alert">
                          {failed.message}
                        </span>
                      )}
                    </div>
                    <button
                      className="button"
                      disabled={hit.installed || installing !== null}
                      onClick={() => void install(hit)}
                      aria-label={
                        hit.installed
                          ? `${hit.title} installed`
                          : `Install ${hit.title}`
                      }
                    >
                      {hit.installed
                        ? "Installed"
                        : installing === hit.project_id
                          ? "Installing…"
                          : "Install"}
                    </button>
                  </li>
                ))}
              </ul>
              {!loading && hits.length === 0 && !problem && (
                <p className="notice">
                  Nothing on Modrinth matches that for {instance.version_id}.
                </p>
              )}
              {hits.length < total && (
                <button
                  className="button mods-more"
                  onClick={() => void more()}
                >
                  Show more
                </button>
              )}
            </>
          )}
        </>
      )}
    </section>
  );
}

/**
 * Every jar in the instance's `mods` folder, however it got there, each with
 * a switch and Remove. ash's record names what it installed; a jar's own
 * metadata names the rest.
 */
function InstalledList(props: {
  instance: Instance;
  installs: number;
  onBrowse: () => void;
}) {
  const { instance } = props;
  const [mods, setMods] = useState<ModEntry[] | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [removing, setRemoving] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    api
      .listMods(instance.id)
      .then((found) => live && setMods(found))
      .catch(
        (e) =>
          live &&
          setProblem(
            isUiError(e) ? e.message : "ash couldn't read the mods folder.",
          ),
      );
    return () => {
      live = false;
    };
  }, [instance.id, props.installs]);

  const act = async (work: () => Promise<ModEntry[]>) => {
    try {
      setMods(await work());
      setProblem(null);
    } catch (e) {
      setProblem(isUiError(e) ? e.message : "ash couldn't change that mod.");
    } finally {
      setRemoving(null);
    }
  };

  if (mods === null) return problem ? <p className="error">{problem}</p> : null;

  return (
    <>
      {problem && (
        <p className="error" role="alert">
          {problem}
        </p>
      )}
      {mods.length === 0 ? (
        <div className="notice mods-vanilla" role="status">
          <p>No mods in {instance.name} yet.</p>
          <button className="button" onClick={props.onBrowse}>
            Browse Modrinth
          </button>
        </div>
      ) : (
        <ul className="mods-results">
          {mods.map((mod) => (
            <li
              key={mod.file_name}
              className={`mod-row${mod.enabled ? "" : " is-off"}`}
            >
              {mod.icon_url ? (
                <img className="mod-icon" src={mod.icon_url} alt="" />
              ) : (
                <span className="mod-icon" />
              )}
              <div className="mod-text">
                <b>{mod.title}</b>
                <small className="numeric">
                  {mod.version ?? "Unknown version"}
                  {mod.from_modrinth ? "" : " · added by hand"}
                </small>
                {mod.overlaps && (
                  <span className="mod-overlap">
                    Does what ash&rsquo;s {mod.overlaps} does. Switch one of
                    them off.
                  </span>
                )}
              </div>
              {removing === mod.file_name ? (
                <div className="mod-actions">
                  <button
                    className="button button-danger"
                    onClick={() =>
                      void act(() => api.removeMod(instance.id, mod.file_name))
                    }
                  >
                    Remove
                  </button>
                  <button className="button" onClick={() => setRemoving(null)}>
                    Keep
                  </button>
                </div>
              ) : (
                <div className="mod-actions">
                  <button
                    className="switch"
                    role="switch"
                    aria-label={`${mod.title} on`}
                    aria-checked={mod.enabled}
                    onClick={() =>
                      void act(() =>
                        api.setModEnabled(
                          instance.id,
                          mod.file_name,
                          !mod.enabled,
                        ),
                      )
                    }
                  />
                  <button
                    className="button"
                    aria-label={`Remove ${mod.title}`}
                    onClick={() => setRemoving(mod.file_name)}
                  >
                    Remove
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      <button
        className="button mods-more"
        onClick={() => void api.revealModsFolder(instance.id)}
      >
        Open mods folder
      </button>
    </>
  );
}

/** "A", "A and B", "A, B and C". */
function listed(names: string[]): string {
  if (names.length <= 1) return names.join("");
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

function downloads(n: number): string {
  if (n >= 1_000_000)
    return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1)}M`;
  if (n >= 1_000) return `${Math.round(n / 1_000)}k`;
  return `${n}`;
}
