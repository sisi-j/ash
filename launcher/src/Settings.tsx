import { useCallback, useEffect, useState } from "react";
import { api, type InstanceId, type MachineOverrides, type UiError } from "./api";

/** The form, where every field is text until it is saved. */
type Draft = {
  memory: string;
  width: string;
  height: string;
  java: string;
};

const EMPTY: Draft = { memory: "", width: "", height: "", java: "" };

function toDraft(settings: MachineOverrides): Draft {
  return {
    memory: settings.memory_mb?.toString() ?? "",
    width: settings.resolution?.width.toString() ?? "",
    height: settings.resolution?.height.toString() ?? "",
    java: settings.java_executable ?? "",
  };
}

/**
 * An empty field means "whatever ash would do", which is not the same as a
 * number that happens to match today's default - a player who never opened
 * this screen should follow the default as it changes rather than being
 * pinned to whatever it was the day they made the instance.
 */
function number(text: string): number | null {
  const trimmed = text.trim();
  if (trimmed === "") return null;
  const value = Number(trimmed);
  return Number.isFinite(value) ? Math.trunc(value) : null;
}

function toOverrides(draft: Draft): MachineOverrides {
  const width = number(draft.width);
  const height = number(draft.height);
  return {
    memory_mb: number(draft.memory),
    java_executable: draft.java.trim() === "" ? null : draft.java.trim(),
    // Half a window size is not a window size.
    resolution: width !== null && height !== null ? { width, height } : null,
  };
}

/**
 * Machine-local settings for one instance.
 *
 * These never travel. ash stores them outside the instance directory
 * entirely, so a future settings sync has nowhere to pick them up from.
 */
export function Settings(props: { id: InstanceId }) {
  const [saved, setSaved] = useState<Draft>(EMPTY);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [fallback, setFallback] = useState<number | null>(null);
  const [error, setError] = useState<UiError | null>(null);
  const [busy, setBusy] = useState(false);
  const [justSaved, setJustSaved] = useState(false);

  useEffect(() => {
    let live = true;
    setError(null);
    setJustSaved(false);
    api
      .overrides(props.id)
      .then((settings) => {
        if (!live) return;
        const next = toDraft(settings);
        setSaved(next);
        setDraft(next);
      })
      .catch((e) => live && setError(e as UiError));
    return () => {
      live = false;
    };
  }, [props.id]);

  useEffect(() => {
    api.defaultMemoryMb().then(setFallback).catch(() => setFallback(null));
  }, []);

  const set = useCallback(
    (field: keyof Draft) => (event: React.ChangeEvent<HTMLInputElement>) => {
      const value = event.target.value;
      setJustSaved(false);
      setDraft((current) => ({ ...current, [field]: value }));
    },
    [],
  );

  const save = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const stored = await api.setOverrides(props.id, toOverrides(draft));
      setSaved(toDraft(stored));
      setDraft(toDraft(stored));
      setJustSaved(true);
    } catch (e) {
      setError(e as UiError);
    } finally {
      setBusy(false);
    }
  }, [draft, props.id]);

  const dirty = (Object.keys(EMPTY) as (keyof Draft)[]).some(
    (field) => draft[field].trim() !== saved[field].trim(),
  );

  return (
    <>
      <div className="field">
        <label className="field-label" htmlFor="memory">
          Memory
        </label>
        <span className="field-input">
          <input
            id="memory"
            className="input numeric"
            inputMode="numeric"
            value={draft.memory}
            onChange={set("memory")}
            placeholder={fallback !== null ? `${fallback}` : ""}
          />
          <span className="unit">MB</span>
        </span>
        <span className="field-note muted">
          {fallback !== null && `Leave empty for ash's default, ${fallback} MB.`}
        </span>
      </div>

      <div className="field">
        <label className="field-label" htmlFor="width">
          Window
        </label>
        <span className="field-input">
          <input
            id="width"
            className="input numeric"
            inputMode="numeric"
            value={draft.width}
            onChange={set("width")}
            placeholder="auto"
            aria-label="Window width"
          />
          <span className="unit">×</span>
          <input
            className="input numeric"
            inputMode="numeric"
            value={draft.height}
            onChange={set("height")}
            placeholder="auto"
            aria-label="Window height"
          />
        </span>
        <span className="field-note muted">Empty on either side lets the game choose.</span>
      </div>

      <div className="field">
        <label className="field-label" htmlFor="java">
          Java
        </label>
        <span className="field-input">
          <input
            id="java"
            className="input"
            value={draft.java}
            onChange={set("java")}
            placeholder="the runtime ash downloaded"
            spellCheck={false}
          />
        </span>
        <span className="field-note muted">
          Only if the runtime ash provisions can't run on this machine.
        </span>
      </div>

      {error && (
        <p className="error" role="alert">
          {error.message}
        </p>
      )}

      <div className="actions">
        <button className="button" disabled={!dirty || busy} onClick={save}>
          {busy ? "Saving…" : "Save settings"}
        </button>
        {dirty && (
          <button className="button" disabled={busy} onClick={() => setDraft(saved)}>
            Discard
          </button>
        )}
        {justSaved && !dirty && <span className="muted saved-note">Saved</span>}
      </div>

      <p className="note muted">
        These stay on this machine. ash keeps them outside the instance, so
        settings that sync between your machines can never carry a memory
        figure from one to another.
      </p>
    </>
  );
}
