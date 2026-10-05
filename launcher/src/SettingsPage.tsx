import type { LauncherPreferences } from "./api";
import { PAGES } from "./Sidebar";

/**
 * The launcher's own settings. Launch sounds is the first; default memory,
 * what ash does when the game starts, and language follow in #77.
 */
export function SettingsPage(props: {
  preferences: LauncherPreferences | null;
  onChange: (next: LauncherPreferences) => void;
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
            aria-checked={preferences?.launch_sounds ?? true}
            disabled={!preferences}
            onClick={() => preferences && props.onChange({ ...preferences, launch_sounds: !preferences.launch_sounds })}
          />
        </div>
      </div>
      <p className="hint">Default memory, what ash does when the game starts, and language are coming soon.</p>
    </section>
  );
}
