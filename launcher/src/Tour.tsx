import { useEffect, useLayoutEffect, useRef, useState } from "react";

type Stop = { target: string; title: string; text: string };

/** The tour's stops, in order: each a part of the launcher marked `data-tour`. */
const STOPS: Stop[] = [
  {
    target: "launch",
    title: "Launch",
    text: "One click checks, downloads and starts the selected instance. The button names each step until the game's title screen.",
  },
  {
    target: "instances",
    title: "Instances",
    text: "Each instance is a version of Minecraft with its own mods and settings. New makes another, and the cog changes one.",
  },
  {
    target: "mods",
    title: "Mods",
    text: "Mods from Modrinth for the selected instance, starting with ash's suggestions. Install brings what each one needs.",
  },
  { target: "wardrobe", title: "Wardrobe", text: "Choose a cape. Other ash players see it in game." },
  { target: "news", title: "News", text: "What's new in ash. A dot means there's something you haven't read." },
  {
    target: "feedback",
    title: "Send feedback",
    text: "Something broken or confusing? Tell us on Discord. It shapes what comes next.",
  },
  {
    target: "settings",
    title: "Settings",
    text: "Launch sounds, game memory, and what ash does when the game starts. This tour is here too, whenever you want it again.",
  },
];

/** Room around the part a stop points at, and between it and the card. */
const PAD = 6;
const GAP = 14;
/** The card stays this far inside the window. */
const EDGE = 12;

type Box = { left: number; top: number; width: number; height: number };

function find(stop: Stop): Element | null {
  return document.querySelector(`[data-tour="${stop.target}"]`);
}

/**
 * Where the card goes beside the highlighted part: to its right if it fits,
 * else below, to its left, or above - and always inside the window.
 */
function place(spot: Box, card: { width: number; height: number }): { left: number; top: number } {
  const w = window.innerWidth;
  const h = window.innerHeight;
  const clampX = (x: number) => Math.min(Math.max(x, EDGE), w - card.width - EDGE);
  const clampY = (y: number) => Math.min(Math.max(y, EDGE), h - card.height - EDGE);
  const right = spot.left + spot.width + GAP;
  const below = spot.top + spot.height + GAP;
  if (right + card.width <= w - EDGE) return { left: right, top: clampY(spot.top) };
  if (below + card.height <= h - EDGE) return { left: clampX(spot.left), top: below };
  const left = spot.left - GAP - card.width;
  if (left >= EDGE) return { left, top: clampY(spot.top) };
  return { left: clampX(spot.left), top: clampY(spot.top - GAP - card.height) };
}

/**
 * A short spotlight tour of the launcher: the first time it opens, and
 * again from Settings. Each stop lights up one part of the window and says
 * what it's for; Next moves on, and Skip or Escape ends it.
 */
export function Tour(props: { onDone: () => void }) {
  // Only the stops whose part is on screen: no feedback link, no stop.
  // Found once the window has drawn, which opening the tour from Settings
  // does in the same moment.
  const [stops, setStops] = useState<Stop[] | null>(null);
  useLayoutEffect(() => setStops(STOPS.filter((s) => find(s) !== null)), []);
  const [at, setAt] = useState(0);
  const [spot, setSpot] = useState<Box | null>(null);
  const [card, setCard] = useState<{ left: number; top: number } | null>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const stop = stops?.[at];
  const last = stops !== null && at === stops.length - 1;
  const { onDone } = props;

  useEffect(() => {
    if (stops?.length === 0) onDone();
  }, [stops, onDone]);

  // Measured on each stop and whenever the window changes size.
  useLayoutEffect(() => {
    if (!stop) return;
    const measure = () => {
      const r = find(stop)?.getBoundingClientRect();
      if (!r || !cardRef.current) return;
      const box = { left: r.left - PAD, top: r.top - PAD, width: r.width + 2 * PAD, height: r.height + 2 * PAD };
      setSpot(box);
      setCard(place(box, cardRef.current.getBoundingClientRect()));
    };
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [stop]);

  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") onDone();
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, [onDone]);

  if (!stop) return null;

  return (
    <div className="tour" role="dialog" aria-modal="true" aria-labelledby="tour-title" aria-describedby="tour-text">
      {spot && <div className="tour-spot" style={spot} aria-hidden="true" />}
      <div
        ref={cardRef}
        className="tour-card"
        style={card ?? { visibility: "hidden" }}
      >
        <small className="tour-count numeric">
          {at + 1} of {stops?.length}
        </small>
        <b id="tour-title">{stop.title}</b>
        <p id="tour-text">{stop.text}</p>
        <div className="tour-actions">
          {!last && (
            <button className="tour-skip" onClick={onDone}>
              Skip tour
            </button>
          )}
          <button
            key={at}
            className="button button-go"
            onClick={() => (last ? onDone() : setAt(at + 1))}
            autoFocus
          >
            {last ? "Done" : "Next"}
          </button>
        </div>
      </div>
    </div>
  );
}
