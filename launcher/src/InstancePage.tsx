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

/** What the last save on a row came to: saved, or refused and why. */
type Status = { saved: true } | { saved: false; message: string };

/** One of the machine-local overrides, by its field. */
type Field = keyof MachineOverrides;

/** Megabytes in gigabytes as a label says them: exact, to one decimal. */
function formatGb(mb: number): string {
  const gb = mb / 1024;
  return Number.isInteger(gb * 2) ? `${gb}` : gb.toFixed(1);
}

/** Megabytes as the slider's gigabytes, to its half-gigabyte step. */
function toGb(mb: number): number {
  return Math.round((mb / 1024) * 2) / 2;
}

/**
 * Where the knob sits. ash-core allows more than the slider shows (512 MB
 * to 64 GB), so a figure set before this page existed keeps its own label
 * while the knob rests at the nearer end, until the player moves it.
 */
function onSlider(gb: number): number {
  return Math.min(MEMORY_MAX_GB, Math.max(MEMORY_MIN_GB, gb));
}

function parseWhole(text: string): number | null {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  return Number(trimmed);
}

/** The window size as typed, both halves together: half a size is not a size. */
type WindowText = { width: string; height: string };

/**
 * One waiting save per row, so a change to one row never cancels another's,
 * and a newer change to a row replaces its older one still waiting. Leaving
 * the page saves whatever was still waiting, rather than losing it.
 */
function useRowSaves() {
  const pending = useRef(new Map<Row, { timer: number; run: () => void }>());

  const cancelSave = useCallback((row: Row) => {
    const earlier = pending.current.get(row);
    if (earlier) window.clearTimeout(earlier.timer);
    pending.current.delete(row);
  }, []);

  const scheduleSave = useCallback(
    (row: Row, save: () => void, delay = SAVE_AFTER_MS) => {
      cancelSave(row);
      const timer = window.setTimeout(() => {
        pending.current.delete(row);
        save();
      }, delay);
      pending.current.set(row, { timer, run: save });
    },
    [cancelSave],
  );

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

  return { scheduleSave, cancelSave };
}

/** Why ash refused something, on the row it belongs to. */
function Refused(props: { message: string }) {
  return (
    <span className="setting-refused" role="alert">
      {props.message}
    </span>
  );
}

/**
 * An instance's own page, from its cog on Play: its name, and its
 * machine-local overrides - memory, window size, Java - then its game
 * folder, and deleting it.
 *
 * Each change saves on its own, shortly after the player stops typing or
 * dragging, as the in-game settings do; there is no Save button to forget.
 * A value ash refuses says why on its own row and is not saved.
 */
export function InstancePage(props: {
  instance: Instance;
  launch: Launch;
  /** Renames the instance; why not, if ash refused. */
  onRename: (name: string) => Promise<string | null>;
  onDeleted: () => void;
  onBack: () => void;
}) {
  const { instance } = props;
  const [name, setName] = useState(instance.name);
  const [overrides, setOverrides] = useState<MachineOverrides | null>(null);
  const [fallbackMb, setFallbackMb] = useState<number | null>(null);
  const [windowText, setWindowText] = useState<WindowText>({ width: "", height: "" });
  // Per row, so one row's "Saved" never hides another row's refusal.
  const [statuses, setStatuses] = useState<Partial<Record<Row, Status>>>({});
  const setStatus = (row: Row, status: Status) => setStatuses((all) => ({ ...all, [row]: status }));
  const [loadError, setLoadError] = useState<UiError | null>(null);
  const { scheduleSave, cancelSave } = useRowSaves();
  // What ash-core last confirmed, apart from what the page shows while a
  // change waits to be saved; and the saves, one at a time.
  const saved = useRef<MachineOverrides | null>(null);
  const saving = useRef<Promise<void>>(Promise.resolve());

  useEffect(() => {
    setName(instance.name);
  }, [instance.id, instance.name]);

  useEffect(() => {
    let live = true;
    setStatuses({});
    api
      .overrides(instance.id)
      .then((loaded) => {
        if (!live) return;
        saved.current = loaded;
        setOverrides(loaded);
        setWindowText({
          width: loaded.resolution?.width.toString() ?? "",
          height: loaded.resolution?.height.toString() ?? "",
        });
      })
      .catch((e) => live && setLoadError(e as UiError));
    api.defaultMemoryMb().then((mb) => live && setFallbackMb(mb)).catch(() => undefined);
    return () => {
      live = false;
    };
  }, [instance.id]);

  /**
   * Shows a change at once and saves it once the player pauses.
   *
   * Saves run one at a time, each sending only its own field on top of what
   * ash-core last confirmed, and each reply updates only that field on
   * screen. So a value ash refused never rides along with another row's
   * save, and an older reply never undoes a newer change still waiting.
   * A refusal puts the field back to what was saved.
   */
  const saveField = useCallback(
    <K extends Field>(field: K, value: MachineOverrides[K], row: Row, delay?: number) => {
      setOverrides((shown) => shown && { ...shown, [field]: value });
      scheduleSave(
        row,
        () => {
          saving.current = saving.current.then(async () => {
            const base = saved.current;
            if (!base) return;
            try {
              const stored = await api.setOverrides(instance.id, { ...base, [field]: value });
              saved.current = stored;
              setOverrides((shown) => shown && { ...shown, [field]: stored[field] });
              setStatus(row, { saved: true });
            } catch (e) {
              setOverrides((shown) => shown && { ...shown, [field]: base[field] });
              setStatus(row, { saved: false, message: (e as UiError).message });
            }
          });
        },
        delay,
      );
    },
    [instance.id, scheduleSave],
  );

  const changeName = (text: string) => {
    setName(text);
    const trimmed = text.trim();
    if (trimmed === "" || trimmed === instance.name) {
      cancelSave("name");
      return;
    }
    scheduleSave("name", async () => {
      const refused = await props.onRename(trimmed);
      setStatus("name", refused ? { saved: false, message: refused } : { saved: true });
    });
  };

  const changeWindow = (next: WindowText) => {
    setWindowText(next);
    if (!overrides) return;
    const w = parseWhole(next.width);
    const h = parseWhole(next.height);
    if (next.width.trim() === "" && next.height.trim() === "") {
      // Empty saves "none", never today's size: the game keeps choosing,
      // rather than being pinned to whatever it chose the day this was set.
      saveField("resolution", null, "window");
    } else if (w !== null && h !== null) {
      saveField("resolution", { width: w, height: h }, "window");
    } else {
      // Half a window size is not a window size: wait for the other half.
      cancelSave("window");
      setStatus("window", { saved: false, message: "Enter both a width and a height, or leave both empty." });
    }
  };

  const chooseJava = async () => {
    if (!overrides) return;
    const picked = await api.chooseJava().catch(() => null);
    if (picked) saveField("java_executable", picked, "java", 0);
  };

  const rowStatus = (row: Row) => {
    const status = statuses[row];
    if (!status) return null;
    return status.saved ? (
      <span className="setting-saved">
        <Icon name="check" />
        Saved
      </span>
    ) : (
      <Refused message={status.message} />
    );
  };

  const memoryMb = overrides?.memory_mb ?? null;
  // Before ash's default has arrived, a custom figure starts at the slider's
  // low end rather than at a copy of a default that is ash-core's to give.
  const shownGb = toGb(memoryMb ?? fallbackMb ?? MEMORY_MIN_GB * 1024);
  const knobGb = onSlider(shownGb);
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
            {rowStatus("name")}
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
                // "None", never the default's figure: an instance on Automatic
                // follows ash's default as it changes, rather than being pinned
                // to whatever it was the day the player chose Automatic.
                onClick={() => overrides && saveField("memory_mb", null, "memory", 0)}
              >
                Automatic{fallbackMb !== null && ` (${toGb(fallbackMb)} GB)`}
              </button>
              <button
                className={`chip${memoryMb !== null ? " is-selected" : ""}`}
                role="radio"
                aria-checked={memoryMb !== null}
                disabled={!overrides}
                onClick={() =>
                  overrides &&
                  memoryMb === null &&
                  saveField("memory_mb", Math.round(shownGb * 1024), "memory", 0)
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
                  value={knobGb}
                  aria-labelledby="memory-label"
                  style={{ ["--filled" as string]: `${((knobGb - MEMORY_MIN_GB) / (MEMORY_MAX_GB - MEMORY_MIN_GB)) * 100}%` }}
                  onChange={(e) =>
                    saveField("memory_mb", Math.round(Number(e.target.value) * 1024), "memory")
                  }
                />
                <output className="numeric">{formatGb(memoryMb)} GB</output>
              </div>
            )}
            {rowStatus("memory")}
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
                value={windowText.width}
                placeholder="auto"
                aria-label="Window width"
                disabled={!overrides}
                onChange={(e) => changeWindow({ ...windowText, width: e.target.value })}
              />
              <span className="unit">×</span>
              <input
                className="input numeric"
                inputMode="numeric"
                value={windowText.height}
                placeholder="auto"
                aria-label="Window height"
                disabled={!overrides}
                onChange={(e) => changeWindow({ ...windowText, height: e.target.value })}
              />
            </div>
            {rowStatus("window") || <span className="setting-hint">Leave both empty to let the game choose.</span>}
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
                onClick={() => overrides && saveField("java_executable", null, "java", 0)}
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
            {rowStatus("java") || (
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
          {/* The loader is shown, never edited: it was chosen when the
              instance was created and no operation changes it. */}
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
        {error && <Refused message={error.message} />}
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
      {error && <Refused message={error.message} />}
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
