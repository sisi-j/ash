import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";

/** Mirrors `ash_core::ManifestProbe`. */
type ManifestProbe = {
  total_versions: number;
  latest_release: string;
  latest_snapshot: string;
};

/** Mirrors the adapter's `UiError`. `kind` is the stable thing to branch on. */
type UiError = { kind: string; message: string };

type State =
  | { status: "loading" }
  | { status: "ready"; probe: ManifestProbe }
  | { status: "failed"; error: UiError };

export default function App() {
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let live = true;
    invoke<ManifestProbe>("probe_manifest")
      .then((probe) => live && setState({ status: "ready", probe }))
      .catch((error: UiError) => live && setState({ status: "failed", error }));
    return () => {
      live = false;
    };
  }, []);

  return (
    <main className="wrap">
      <header className="masthead">
        <h1 className="wordmark">ash</h1>
        <p className="tagline">minimal. fast. yours.</p>
      </header>

      <section className="panel">
        <h2 className="panel-title">Mojang version manifest</h2>
        {state.status === "loading" && <p className="muted">Reading…</p>}

        {state.status === "failed" && (
          <p className="error" role="alert">
            {state.error.message}
          </p>
        )}

        {state.status === "ready" && (
          <dl className="readout">
            <div>
              <dt>Versions published</dt>
              <dd className="numeric">{state.probe.total_versions}</dd>
            </div>
            <div>
              <dt>Latest release</dt>
              <dd className="numeric">{state.probe.latest_release}</dd>
            </div>
            <div>
              <dt>Latest snapshot</dt>
              <dd className="numeric">{state.probe.latest_snapshot}</dd>
            </div>
          </dl>
        )}
      </section>

      <footer className="note">
        This is the walking skeleton. Everything above travelled the full seam:
        HTTP port → ash-core → Tauri command → here.
      </footer>
    </main>
  );
}
