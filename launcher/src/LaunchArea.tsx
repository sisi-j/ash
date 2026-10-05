import { useEffect, useRef, useState, type MouseEvent } from "react";
import { describeBytes, type Instance } from "./api";
import { Icon } from "./icons";
import type { Launch, Progress } from "./launch";
import { PixelScene } from "./PixelScene";
import { playLaunchSound } from "./sound";

/** "ash client" or "vanilla": what a player calls the two kinds of instance. */
export function kindOf(instance: Instance): string {
  return instance.loader === "vanilla" ? "vanilla" : "ash client";
}

/**
 * How far along the fill is, in the three bands the approved mockup gave the
 * steps: checking to 30%, downloading across to 82%, starting to the end.
 */
function filled(progress: Progress): number {
  if (progress.stage === "checking") return 10;
  if (progress.stage === "starting") return 92;
  const fraction = progress.missingBytes > 0 ? progress.doneBytes / progress.missingBytes : 0;
  return 30 + 52 * Math.min(1, fraction);
}

function percent(progress: Progress): number {
  return progress.missingBytes > 0 ? Math.round((100 * progress.doneBytes) / progress.missingBytes) : 0;
}

type Ripple = { id: number; x: number; y: number };

/**
 * The top of the Play page: the selected instance's LAUNCH GAME over the
 * pixel scene.
 *
 * A click has to be unmistakable, so it answers four ways at once: the
 * button presses in and a ripple spreads from the click, the scene behind
 * brightens and zooms with a light sweeping across, the button fills
 * through the steps the launch is really on, and - unless launch sounds are
 * off - a short sound plays, with a quieter one when the game is up.
 */
export function LaunchArea(props: { instance: Instance; launch: Launch; sounds: boolean }) {
  const { instance, launch, sounds } = props;
  const { phase } = launch;
  const [ripples, setRipples] = useState<Ripple[]>([]);
  const nextRipple = useRef(0);
  const before = useRef(phase.at);

  // The quieter sound, when a launch this screen watched gets the game up -
  // not when ash is reopened onto a game that was already running.
  useEffect(() => {
    if (before.current === "working" && phase.at === "running" && sounds) playLaunchSound("start");
    before.current = phase.at;
  }, [phase.at, sounds]);

  const press = (e: MouseEvent<HTMLButtonElement>) => {
    if (phase.at !== "idle") return;
    const box = e.currentTarget.getBoundingClientRect();
    const ripple = { id: nextRipple.current++, x: e.clientX - box.left, y: e.clientY - box.top };
    setRipples((all) => [...all, ripple]);
    window.setTimeout(() => setRipples((all) => all.filter((r) => r.id !== ripple.id)), 700);
    if (sounds) playLaunchSound("click");
    launch.start("play");
  };

  let label = "LAUNCH GAME";
  let detail = `${instance.name} · ${kindOf(instance)}`;
  let fill = 0;
  if (phase.at === "idle" && phase.plan.missing_files > 0) {
    detail += ` · ${describeBytes(phase.plan.missing_bytes)} to download`;
  } else if (phase.at === "working") {
    const { progress } = phase;
    fill = filled(progress);
    if (progress.stage === "checking") {
      label = "CHECKING FILES";
    } else if (progress.stage === "downloading") {
      label = "DOWNLOADING";
      detail = `${progress.fetching === "java" ? "Java runtime" : "Game files"} · ${percent(progress)}%`;
    } else {
      label = "STARTING";
      detail = "Starting Minecraft";
    }
  } else if (phase.at === "running") {
    fill = 100;
    detail = launch.runningAs ? `${instance.name} is running as ${launch.runningAs}` : `${instance.name} is running`;
  }

  const working = phase.at === "working";
  const running = phase.at === "running";

  return (
    <div className={`launch-area${working || running ? " is-launching" : ""}`}>
      <PixelScene />
      <div className="sweep" aria-hidden="true" />

      <div className="launch-stack">
        <button
          className={`launch-button${running ? " is-playing" : ""}`}
          onClick={press}
          aria-disabled={phase.at !== "idle"}
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
        {running && (
          <button className="launch-link" onClick={launch.stop}>
            Stop
          </button>
        )}
      </div>
    </div>
  );
}
