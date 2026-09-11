import { useCallback, useEffect, useMemo, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import {
  describeAge,
  type Catalogue,
  type CatalogueEntry,
  type UiError,
} from "./catalogue";

type State =
  | { status: "loading" }
  | { status: "ready"; catalogue: Catalogue }
  | { status: "failed"; error: UiError };

export default function App() {
  const [state, setState] = useState<State>({ status: "loading" });
  const [showSnapshots, setShowSnapshots] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async (command: "catalogue" | "refresh_catalogue") => {
    try {
      const catalogue = await invoke<Catalogue>(command);
      setState({ status: "ready", catalogue });
    } catch (error) {
      setState({ status: "failed", error: error as UiError });
    }
  }, []);

  useEffect(() => {
    void load("catalogue");
  }, [load]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await load("refresh_catalogue");
    setRefreshing(false);
  }, [load]);

  const catalogue = state.status === "ready" ? state.catalogue : null;

  // Snapshots are hidden by default; the old_alpha/old_beta eras are not
  // version targets at all, so they never appear.
  const listed = useMemo<CatalogueEntry[]>(() => {
    if (!catalogue) return [];
    return catalogue.versions.filter(
      (v) => v.kind === "release" || (showSnapshots && v.kind === "snapshot"),
    );
  }, [catalogue, showSnapshots]);

  const pinned = useMemo<CatalogueEntry[]>(
    () => catalogue?.versions.filter((v) => v.first_class) ?? [],
    [catalogue],
  );

  return (
    <main className="wrap">
      <header className="masthead">
        <h1 className="wordmark">ash</h1>
        <p className="tagline">minimal. fast. yours.</p>
      </header>

      {state.status === "loading" && <p className="muted">Loading versions…</p>}

      {state.status === "failed" && (
        <p className="error" role="alert">
          {state.error.message}
        </p>
      )}

      {catalogue && (
        <>
          {pinned.length > 0 && (
          <section className="panel">
            <h2 className="panel-title">Built for</h2>
            <ul className="versions">
              {pinned.map((v) => (
                <li key={v.id} className="version version-pinned">
                  <span className="numeric">{v.id}</span>
                  <span className="muted">{v.released_at.slice(0, 10)}</span>
                </li>
              ))}
            </ul>
          </section>
          )}

          <section className="panel">
            <div className="panel-head">
              <h2 className="panel-title">
                All versions <span className="numeric">{listed.length}</span>
              </h2>
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={showSnapshots}
                  onChange={(e) => setShowSnapshots(e.target.checked)}
                />
                Snapshots
              </label>
            </div>

            <ul className="versions versions-scroll">
              {listed.map((v) => (
                <li key={v.id} className="version">
                  <span className="numeric">{v.id}</span>
                  <span className="muted">
                    {v.kind === "snapshot" ? "snapshot" : v.released_at.slice(0, 10)}
                  </span>
                </li>
              ))}
            </ul>
          </section>

          <footer className="statusbar">
            <span className="muted">
              {catalogue.source === "cache" ? "Cached · " : "Updated "}
              {describeAge(catalogue.fetched_at_unix)}
            </span>
            <button className="button" onClick={refresh} disabled={refreshing}>
              {refreshing ? "Refreshing…" : "Refresh"}
            </button>
          </footer>
        </>
      )}
    </main>
  );
}
