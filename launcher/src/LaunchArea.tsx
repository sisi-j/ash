import { useEffect, useRef, useState, type MouseEvent } from "react";
import { describeBytes, describeKind, type Instance } from "./api";
import { Icon } from "./icons";
import { canStart, type Launch, type Progress } from "./launch";
import { stepOf, useLaunchFill } from "./launchBar";
import { PixelScene } from "./PixelScene";
import { playLaunchSound } from "./sound";

/**
 * How much of the download is done, 0 to 1.
 *
 * The button says it as one percentage, as the approved design does. The
 * file counts and notes the events carry (resuming, re-verifying a corrupt
 * file) are still in the progress, and in ash's log, for when one number is
 * not enough.
 */
function downloadedFraction(progress: Progress): number {
  return progress.missingBytes > 0 ? Math.min(1, progress.doneBytes / progress.missingBytes) : 0;
}

type Ripple = { id: number; x: number; y: number };

/**
 * The top of the Play page: the selected instance's LAUNCH GAME over the
 * pixel scene.
 *
 * A click has to be unmistakable, so it answers four ways at once: the
 * button presses in and a ripple spreads from the click, the scene behind
 * brightens and zooms with a light sweeping across, the button fills
 * through the steps the launch is really on (launchBar.ts), up to the
 * game's title screen, and - unless launch sounds are off - a short sound
 * plays, with a quieter one when the title screen is up.
 */
export function LaunchArea(props: { instance: Instance; launch: Launch; sounds: boolean }) {
  const { instance, launch, sounds } = props;
  const { phase } = launch;
  const [ripples, setRipples] = useState<Ripple[]>([]);
  const nextRipple = useRef(0);
  const fill = useLaunchFill(instance.id, phase, instance.loader === "vanilla");
  const step = stepOf(phase);
  const before = useRef(step);

  // The quieter sound, when a launch this screen watched reaches the title
  // screen - not when ash is reopened onto a game that was already running.
  useEffect(() => {
    if (before.current !== null && before.current !== "done" && step === "done" && sounds) playLaunchSound("start");
    before.current = step;
  }, [step, sounds]);

  const press = (e: MouseEvent<HTMLButtonElement>) => {
    if (!canStart(phase)) return;
    const box = e.currentTarget.getBoundingClientRect();
    const ripple = { id: nextRipple.current++, x: e.clientX - box.left, y: e.clientY - box.top };
    setRipples((all) => [...all, ripple]);
    window.setTimeout(() => setRipples((all) => all.filter((r) => r.id !== ripple.id)), 700);
    if (sounds) playLaunchSound("click");
    launch.start("play");
  };

  let label = "LAUNCH GAME";
  let detail = `${instance.name} · ${describeKind(instance)}`;
  if (phase.at === "idle" && phase.plan.missing_files > 0) {
    detail += ` · ${describeBytes(phase.plan.missing_bytes)} to download`;
  } else if (phase.at === "working") {
    const { progress } = phase;
    if (progress.stage === "checking") {
      label = "CHECKING FILES";
      detail = "Checking files";
    } else if (progress.stage === "downloading") {
      label = "DOWNLOADING";
      detail = `${progress.fetching === "java" ? "Java runtime" : "Game files"} · ${Math.round(100 * downloadedFraction(progress))}%`;
    } else if (phase.goal === "prepare") {
      // Download only: the files are in, and no game is about to start.
      label = "DOWNLOADING";
      detail = "Finishing…";
    } else {
      label = "STARTING";
      detail = "Starting Java";
    }
  } else if (phase.at === "running" && phase.boot.stage !== "ready") {
    label = "STARTING";
    const { boot } = phase;
    detail =
      boot.stage === "starting"
        ? "Starting Java"
        : boot.stage === "loading_mods"
          ? boot.mods !== null
            ? `Loading ${boot.mods} mods`
            : "Loading mods"
          : "Loading resources";
  } else if (phase.at === "running") {
    detail = launch.runningAs ? `${instance.name} is running as ${launch.runningAs}` : `${instance.name} is running`;
  }

  // Loading counts as working until the title screen: Stop, not PLAYING.
  const loading = phase.at === "running" && phase.boot.stage !== "ready";
  const working = phase.at === "working";
  const running = phase.at === "running" && !loading;
  // The scene answers a launch, not a download fetched ahead of time.
  const launching = (working && phase.goal === "play") || phase.at === "running";

  return (
    <div className={`launch-area${launching ? " is-launching" : ""}`}>
      <PixelScene />
      <div className="sweep" aria-hidden="true" />

      <div className="launch-stack">
        <button
          className={`launch-button${running ? " is-playing" : ""}${phase.at === "unchecked" ? " is-unavailable" : ""}`}
          onClick={press}
          aria-disabled={!canStart(phase)}
          aria-live="polite"
        >
          <span className="launch-fill" style={{ width: `${fill}%` }} aria-hidden="true" />
          <span className="launch-label">
            {running && (
              <span className="launch-tick">
                <Icon name="check" />
              </span>
            )}
            {running ? "PLAYING" : label}
          </span>
          <small className="launch-detail numeric">{detail}</small>
          {ripples.map((r) => (
            <span key={r.id} className="ripple" style={{ left: r.x, top: r.y }} aria-hidden="true" />
          ))}
        </button>

        {working && (
          <button className="launch-link" onClick={launch.cancel}>
            Cancel
          </button>
        )}
        {(running || loading) && (
          <button className="launch-link" onClick={launch.stop}>
            Stop
          </button>
        )}
        {phase.at === "unchecked" && (
          <p className="launch-note" role="status">
            {phase.error.message}
            <button className="launch-link" onClick={launch.dismiss}>
              Try again
            </button>
          </p>
        )}
      </div>
    </div>
  );
}
