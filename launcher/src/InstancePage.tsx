import { useCallback, useEffect, useRef, useState } from "react";
import {
  api,
  describeAge,
  describeBytes,
  LOADER_LABELS,
  type DeletionPreview,
  type Instance,
  type MachineOverrides,
  type UiError,
} from "./api";
import { Icon } from "./icons";
import type { Launch } from "./launch";

/** The memory slider's range and step, in GB. */
const MEMORY_MIN_GB = 2;
const MEMORY_MAX_GB = 16;
const MEMORY_STEP_GB = 0.5;

/** How long after the last keystroke or drag a change is saved. */
const SAVE_AFTER_MS = 500;

/** The page's rows, for saying which one a save or a refusal belongs to. */
type Row = "name" | "memory" | "window" | "java";

type Status = { row: Row; saved: true } | { row: Row; saved: false; message: string };

function gb(mb: number): number {
  return Math.round((mb / 1024) * 2) / 2;
}

function whole(text: string): number | null {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  return Number(trimmed);
}

/**
 * An instance's own page, from its cog on Play: its name, and the settings
 * that belong to this machine - memory, window size, Java - then its game
 * folder, and deleting it.
 *
 * Each change saves on its own, shortly after the player stops typing or
 * dragging, as the in-game settings do; there is no Save button to forget.
 * A value ash refuses says why on its own row and is not saved.
 */
export function InstancePage(props: {
  instance: Instance;
  launch: Launch;
  onRename: (name: string) => Promise<boolean>;
  onDeleted: () => void;
  onBack: () => void;
}) {
  const { instance } = props;
  const [name, setName] = useState(instance.name);
  const [overrides, setOverrides] = useState<MachineOverrides | null>(null);
  const [fallbackMb, setFallbackMb] = useState<number | null>(null);
  const [width, setWidth] = useState("");
  const [height, setHeight] = useState("");
  const [status, setStatus] = useState<Status | null>(null);
  const [loadError, setLoadError] = useState<UiError | null>(null);
  // One waiting save per row, so a change to one row never cancels another's.
  const pending = useRef(new Map<Row, { timer: number; run: () => void }>());

  useEffect(() => {
    setName(instance.name);
  }, [instance.id, instance.name]);

  useEffect(() => {
    let live = true;
    setStatus(null);
    api
      .overrides(instance.id)
      .then((loaded) => {
        if (!live) return;
        setOverrides(loaded);
        setWidth(loaded.resolution?.width.toString() ?? "");
        setHeight(loaded.resolution?.height.toString() ?? "");
      })
      .catch((e) => live && setLoadError(e as UiError));
    api.defaultMemoryMb().then((mb) => live && setFallbackMb(mb)).catch(() => undefined);
    return () => {
      live = false;
    };
  }, [instance.id]);

  /** Runs `save` once the player has paused; a newer change to the row replaces one still waiting. */
  const later = useCallback((row: Row, save: () => void, delay = SAVE_AFTER_MS) => {
    const waiting = pending.current;
    const earlier = waiting.get(row);
    if (earlier) window.clearTimeout(earlier.timer);
    const timer = window.setTimeout(() => {
      waiting.delete(row);
      save();
    }, delay);
    waiting.set(row, { timer, run: save });
  }, []);

  const forget = (row: Row) => {
    const earlier = pending.current.get(row);
    if (earlier) window.clearTimeout(earlier.timer);
    pending.current.delete(row);
  };

  // Leaving the page saves whatever was still waiting, rather than losing it.
  useEffect(() => {
    const waiting = pending.current;
    return () => {
      for (const { timer, run } of waiting.values()) {
        window.clearTimeout(timer);
        run();
      }
      waiting.clear();
    };
  }, []);

  const saveOverrides = useCallback(
    (next: MachineOverrides, row: Row, delay?: number) => {
      setOverrides(next);
      later(row, async () => {
        try {
          setOverrides(await api.setOverrides(instance.id, next));
          setStatus({ row, saved: true });
        } catch (e) {
          setStatus({ row, saved: false, message: (e as UiError).message });
        }
      }, delay);
    },
    [instance.id, later],
  );

  const changeName = (text: string) => {
    setName(text);
    const trimmed = text.trim();
    if (trimmed === "" || trimmed === instance.name) {
      forget("name");
      return;
    }
    later("name", async () => {
      if (await props.onRename(trimmed)) setStatus({ row: "name", saved: true });
    });
  };

  const changeWindow = (nextWidth: string, nextHeight: string) => {
    setWidth(nextWidth);
    setHeight(nextHeight);
    if (!overrides) return;
    const w = whole(nextWidth);
    const h = whole(nextHeight);
    if (nextWidth.trim() === "" && nextHeight.trim() === "") {
      saveOverrides({ ...overrides, resolution: null }, "window");
    } else if (w !== null && h !== null) {
      saveOverrides({ ...overrides, resolution: { width: w, height: h } }, "window");
    } else {
      // Half a window size is not a window size: wait for the other half.
      forget("window");
      setStatus({ row: "window", saved: false, message: "Enter both a width and a height, or leave both empty." });
    }
  };

  const chooseJava = async () => {
    if (!overrides) return;
    const picked = await api.chooseJava().catch(() => null);
    if (picked) saveOverrides({ ...overrides, java_executable: picked }, "java", 0);
  };

  const note = (row: Row) =>
    status?.row === row &&
    (status.saved ? (
      <span className="setting-saved">
        <Icon name="check" />
        Saved
      </span>
    ) : (
      <span className="setting-refused" role="alert">
        {status.message}
      </span>
    ));

  const memoryMb = overrides?.memory_mb ?? null;
  const shownGb = gb(memoryMb ?? fallbackMb ?? 2048);
  const { phase } = props.launch;

  return (
    <section className="page instance-page">
      <div className="page-head">
        <button className="back-round" onClick={props.onBack} aria-label="Back to Play">
          <Icon name="back" />
        </button>
        <h2 className="page-title">{instance.name}</h2>
      </div>

      {loadError && (
        <p className="error" role="alert">
          {loadError.message}
        </p>
      )}

      <div className="settings">
        <div className="setting">
          <label className="setting-label" htmlFor="instance-name">
            Name
          </label>
          <div className="setting-control">
            <input
              id="instance-name"
              className="input"
              value={name}
              onChange={(e) => changeName(e.target.value)}
              onBlur={() => name.trim() === "" && setName(instance.name)}
              spellCheck={false}
            />
            {note("name")}
          </div>
        </div>

        <div className="setting">
          <span className="setting-label" id="memory-label">
            Memory
          </span>
          <div className="setting-control">
            <div className="chips" role="radiogroup" aria-labelledby="memory-label">
              <button
                className={`chip${memoryMb === null ? " is-selected" : ""}`}
                role="radio"
                aria-checked={memoryMb === null}
                disabled={!overrides}
                onClick={() => overrides && saveOverrides({ ...overrides, memory_mb: null }, "memory", 0)}
              >
                Automatic{fallbackMb !== null && ` (${gb(fallbackMb)} GB)`}
              </button>
              <button
                className={`chip${memoryMb !== null ? " is-selected" : ""}`}
                role="radio"
                aria-checked={memoryMb !== null}
                disabled={!overrides}
                onClick={() =>
                  overrides &&
                  memoryMb === null &&
                  saveOverrides({ ...overrides, memory_mb: Math.round(shownGb * 1024) }, "memory", 0)
                }
              >
                Custom
              </button>
            </div>
            {memoryMb !== null && overrides && (
              <div className="slider">
                <input
                  type="range"
                  min={MEMORY_MIN_GB}
                  max={MEMORY_MAX_GB}
                  step={MEMORY_STEP_GB}
                  value={Math.min(MEMORY_MAX_GB, Math.max(MEMORY_MIN_GB, shownGb))}
                  aria-labelledby="memory-label"
                  style={{
                    ["--filled" as string]: `${((Math.min(MEMORY_MAX_GB, Math.max(MEMORY_MIN_GB, shownGb)) - MEMORY_MIN_GB) / (MEMORY_MAX_GB - MEMORY_MIN_GB)) * 100}%`,
                  }}
                  onChange={(e) =>
                    saveOverrides({ ...overrides, memory_mb: Math.round(Number(e.target.value) * 1024) }, "memory")
                  }
                />
                <output className="numeric">{shownGb} GB</output>
              </div>
            )}
            {note("memory")}
          </div>
        </div>

        <div className="setting">
          <span className="setting-label" id="window-label">
            Window size
          </span>
          <div className="setting-control">
            <div className="window-size" aria-labelledby="window-label">
              <input
                className="input numeric"
                inputMode="numeric"
                value={width}
                placeholder="auto"
                aria-label="Window width"
                disabled={!overrides}
                onChange={(e) => changeWindow(e.target.value, height)}
              />
              <span className="unit">×</span>
              <input
                className="input numeric"
                inputMode="numeric"
                value={height}
                placeholder="auto"
                aria-label="Window height"
                disabled={!overrides}
                onChange={(e) => changeWindow(width, e.target.value)}
              />
            </div>
            {note("window") || <span className="setting-hint">Leave both empty to let the game choose.</span>}
          </div>
        </div>

        <div className="setting">
          <span className="setting-label" id="java-label">
            Java
          </span>
          <div className="setting-control">
            <div className="chips" role="radiogroup" aria-labelledby="java-label">
              <button
                className={`chip${!overrides?.java_executable ? " is-selected" : ""}`}
                role="radio"
                aria-checked={!overrides?.java_executable}
                disabled={!overrides}
                onClick={() => overrides && saveOverrides({ ...overrides, java_executable: null }, "java", 0)}
              >
                Automatic
              </button>
              <button
                className={`chip${overrides?.java_executable ? " is-selected" : ""}`}
                role="radio"
                aria-checked={Boolean(overrides?.java_executable)}
                disabled={!overrides}
                onClick={() => void chooseJava()}
              >
                Choose…
              </button>
            </div>
            {overrides?.java_executable && <span className="setting-path">{overrides.java_executable}</span>}
            {note("java") || (
              <span className="setting-hint">Only if the Java ash downloads can't run on this machine.</span>
            )}
          </div>
        </div>

        <div className="setting">
          <span className="setting-label">Game folder</span>
          <div className="setting-control">
            <button className="button" onClick={() => void api.revealGameDirectory(instance.id)}>
              Open folder
            </button>
          </div>
        </div>

        <div className="setting">
          <span className="setting-label">Delete</span>
          <div className="setting-control">
            <DeleteInstance instance={instance} onDeleted={props.onDeleted} />
          </div>
        </div>
      </div>

      <p className="hint">
        Memory, window size and Java stay on this machine. ash keeps them outside the instance, so settings that
        sync between your machines can never carry them from one to another.
      </p>

      <div className="details">
        <h3 className="panel-title">Details</h3>
        <p className="muted">
          <span className="numeric">{instance.version_id}</span> · {LOADER_LABELS[instance.loader]} · created{" "}
          {describeAge(instance.created_at_ms)}
          {/* The default key, said once where a new player looks. It is
              rebindable in the game's Controls, and the launcher never reads
              the game's own options, so a player who moved it knows where. */}
          {instance.loader !== "vanilla" && " · Right Shift opens ash's settings in game"}
        </p>
        <div className="actions">
          <button className="button" onClick={() => void api.revealLog()}>
            Show log
          </button>
          {/* Fetching the files ahead of time, for a slow connection: the
              LAUNCH area on Play shows the progress. */}
          {phase.at === "idle" && phase.plan.missing_files > 0 && (
            <button className="button" onClick={() => props.launch.start("prepare")}>
              Download only ({describeBytes(phase.plan.missing_bytes)})
            </button>
          )}
          {phase.at === "working" && (
            <button className="button" disabled>
              Downloading…
            </button>
          )}
        </div>
      </div>
    </section>
  );
}

/**
 * Deleting, confirmed on the page itself. The confirmation says what goes
 * with the instance - its worlds above all - because there is no undo.
 */
function DeleteInstance(props: { instance: Instance; onDeleted: () => void }) {
  const [preview, setPreview] = useState<DeletionPreview | null>(null);
  const [error, setError] = useState<UiError | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => setPreview(null), [props.instance.id]);

  const ask = async () => {
    setError(null);
    try {
      setPreview(await api.previewDeletion(props.instance.id));
    } catch (e) {
      setError(e as UiError);
    }
  };

  const confirm = async () => {
    setBusy(true);
    try {
      await api.deleteInstance(props.instance.id);
      props.onDeleted();
    } catch (e) {
      setError(e as UiError);
      setBusy(false);
    }
  };

  if (!preview) {
    return (
      <>
        <button className="button button-danger" onClick={() => void ask()}>
          Delete instance
        </button>
        {error && (
          <span className="setting-refused" role="alert">
            {error.message}
          </span>
        )}
      </>
    );
  }

  const worlds = preview.worlds.length;
  return (
    <div className="delete-confirm" role="alert">
      <p>
        {worlds > 0 ? (
          <>
            Its <strong>{worlds === 1 ? "world" : `${worlds} worlds`}</strong> ({preview.worlds.join(", ")}), screenshots
            and resource packs go with it
          </>
        ) : (
          <>Its screenshots and resource packs go with it</>
        )}{" "}
        - <span className="numeric">{describeBytes(preview.total_bytes)}</span>, and it cannot be undone. Shared game
        files in the depot are not touched.
      </p>
      {error && (
        <span className="setting-refused" role="alert">
          {error.message}
        </span>
      )}
      <div className="actions">
        <button className="button button-danger-solid" disabled={busy} onClick={() => void confirm()}>
          Delete
        </button>
        <button className="button" onClick={() => setPreview(null)} autoFocus>
          Keep it
        </button>
      </div>
    </div>
  );
}
