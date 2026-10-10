import { useEffect, useRef, useState } from "react";
import { api, isUiError, type AshAccountStatus, type LauncherPreferences, type MachineDefaults, type OnGameStart } from "./api";
import { DeleteAshAccount, describeAshAccount } from "./AshAccount";
import { Icon } from "./icons";
import { filledPercent, formatGb, MEMORY_MAX_GB, MEMORY_MIN_GB, MEMORY_STEP_GB, onSlider, toGb } from "./memory";
import { PAGES } from "./Sidebar";

/** How long after the last drag a memory change is saved, as on an instance's page. */
const SAVE_AFTER_MS = 500;

const ON_GAME_START: { value: OnGameStart; label: string }[] = [
  { value: "keep_open", label: "Keep ash open" },
  { value: "minimise", label: "Minimise ash" },
  { value: "close", label: "Close ash" },
];

/**
 * The launcher's own settings, not any one instance's: launch sounds, what
 * ash does when the game starts, the memory an instance gets when it has
 * none of its own, and language.
 *
 * Default memory is this machine's alone and is kept apart from the rest,
 * which will follow the player to other machines.
 */
export function SettingsPage(props: {
  preferences: LauncherPreferences | null;
  onChange: (next: LauncherPreferences) => void;
  /** The active player and where they stand with ash's servers. */
  ashAccount: { username: string; status: AshAccountStatus } | null;
  /** Delete the active player's ash account; whether ash's servers confirmed it. */
  onDeleteAshAccount: () => Promise<boolean>;
}) {
  const { preferences } = props;

  return (
    <section className="page">
      <h2 className="page-title">{PAGES.settings.label}</h2>
      <div className="options">
        <div className="option">
          <span className="option-text">
            <b id="launch-sounds">Launch sounds</b>
            <small>A short sound when you click LAUNCH GAME, and a quieter one when the game starts.</small>
          </span>
          <button
            className="switch"
            role="switch"
            aria-labelledby="launch-sounds"
            aria-checked={preferences?.launch_sounds ?? false}
            disabled={!preferences}
            onClick={() => preferences && props.onChange({ ...preferences, launch_sounds: !preferences.launch_sounds })}
          />
        </div>
      </div>

      <div className="settings launcher-settings">
        <div className="setting">
          <span className="setting-label" id="on-game-start">
            When the game starts
          </span>
          <div className="setting-control">
            <div className="chips" role="radiogroup" aria-labelledby="on-game-start">
              {ON_GAME_START.map((choice) => (
                <button
                  key={choice.value}
                  className={`chip${preferences?.on_game_start === choice.value ? " is-selected" : ""}`}
                  role="radio"
                  aria-checked={preferences?.on_game_start === choice.value}
                  disabled={!preferences}
                  onClick={() => preferences && props.onChange({ ...preferences, on_game_start: choice.value })}
                >
                  {choice.label}
                </button>
              ))}
            </div>
            <span className="setting-hint">Only after the game has started. Closing ash leaves the game running.</span>
          </div>
        </div>

        <DefaultMemory />

        {props.ashAccount && (
          <div className="setting">
            <span className="setting-label">ash account</span>
            <div className="setting-control">
              <span className="ash-account-status" role="status">
                {describeAshAccount(props.ashAccount.username, props.ashAccount.status)}
              </span>
              {props.ashAccount.status.state !== "deleted" && (
                <DeleteAshAccount username={props.ashAccount.username} onDelete={props.onDeleteAshAccount} />
              )}
            </div>
          </div>
        )}

        <div className="setting">
          <span className="setting-label" id="language">
            Language
          </span>
          <div className="setting-control">
            <div className="chips" role="radiogroup" aria-labelledby="language">
              <button className="chip is-selected" role="radio" aria-checked>
                English
              </button>
            </div>
            <span className="setting-hint">More languages are coming.</span>
          </div>
        </div>
      </div>
    </section>
  );
}

/** What the last save came to: saved, or refused and why. */
type Status = { saved: true } | { saved: false; message: string };

/**
 * The memory every instance without its own is given, on this machine. Like
 * an instance's: Automatic follows ash's own default, Custom is a slider.
 */
function DefaultMemory() {
  const [defaults, setDefaults] = useState<MachineDefaults | null>(null);
  const [status, setStatus] = useState<Status | null>(null);
  const [ashsMb, setAshsMb] = useState<number | null>(null);
  const pending = useRef<number | null>(null);

  useEffect(() => {
    let live = true;
    api
      .machineDefaults()
      .then((d) => live && setDefaults(d))
      .catch(() => live && setDefaults({ memory_mb: null }));
    api
      .ashDefaultMemoryMb()
      .then((mb) => live && setAshsMb(mb))
      .catch(() => undefined);
    return () => {
      live = false;
      if (pending.current !== null) window.clearTimeout(pending.current);
    };
  }, []);

  const save = (next: MachineDefaults, delay = SAVE_AFTER_MS) => {
    setDefaults(next);
    setStatus(null);
    if (pending.current !== null) window.clearTimeout(pending.current);
    pending.current = window.setTimeout(() => {
      pending.current = null;
      api
        .setMachineDefaults(next)
        .then(() => setStatus({ saved: true }))
        .catch((e) => setStatus({ saved: false, message: isUiError(e) ? e.message : "Not saved." }));
    }, delay);
  };

  const memoryMb = defaults?.memory_mb ?? null;
  const knobGb = onSlider(toGb(memoryMb ?? MEMORY_MIN_GB * 1024));

  return (
    <div className="setting">
      <span className="setting-label" id="default-memory">
        Default memory
      </span>
      <div className="setting-control">
        <div className="chips" role="radiogroup" aria-labelledby="default-memory">
          <button
            className={`chip${defaults && memoryMb === null ? " is-selected" : ""}`}
            role="radio"
            aria-checked={memoryMb === null}
            disabled={!defaults}
            onClick={() => save({ memory_mb: null }, 0)}
          >
            Automatic{ashsMb !== null && ` (${formatGb(ashsMb)} GB)`}
          </button>
          <button
            className={`chip${memoryMb !== null ? " is-selected" : ""}`}
            role="radio"
            aria-checked={memoryMb !== null}
            disabled={!defaults}
            onClick={() => memoryMb === null && save({ memory_mb: 4 * 1024 }, 0)}
          >
            Custom
          </button>
        </div>
        {memoryMb !== null && (
          <div className="slider">
            <input
              type="range"
              min={MEMORY_MIN_GB}
              max={MEMORY_MAX_GB}
              step={MEMORY_STEP_GB}
              value={knobGb}
              aria-labelledby="default-memory"
              style={{ ["--filled" as string]: filledPercent(knobGb) }}
              onChange={(e) => save({ memory_mb: Math.round(Number(e.target.value) * 1024) })}
            />
            <output className="numeric">{formatGb(memoryMb)} GB</output>
          </div>
        )}
        <span className="setting-hint">For every instance without memory of its own. An instance's own setting still wins.</span>
        {status &&
          (status.saved ? (
            <span className="setting-saved">
              <Icon name="check" />
              Saved
            </span>
          ) : (
            <span className="setting-refused" role="alert">
              {status.message}
            </span>
          ))}
      </div>
    </div>
  );
}
