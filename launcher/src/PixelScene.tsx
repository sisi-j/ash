import { useEffect, useRef } from "react";

/**
 * The pixel landscape behind LAUNCH GAME, drawn by the launcher itself so
 * there is no artwork to licence: sky, drifting clouds, far hills and a
 * near hillside of grass, dirt and stone with a few trees.
 *
 * It follows the local time of day - day, sunset or night - and drifts
 * slowly. With reduced motion it is one still frame.
 */

type TimeOfDay = "day" | "sunset" | "night";

const LOOKS: Record<TimeOfDay, { sky: string[]; far: string; tint: string[]; cloud: string }> = {
  day: { sky: ["#5f93ee", "#b7d0fb"], far: "#8eadcf", tint: [], cloud: "rgba(255,255,255,.92)" },
  sunset: {
    sky: ["#2f3470", "#d9715a", "#f6c27a"],
    far: "#7a5f86",
    tint: ["rgba(255,120,60,.22)", "rgba(40,10,40,.18)"],
    cloud: "rgba(255,190,170,.85)",
  },
  night: { sky: ["#03061a", "#141d48"], far: "#1c2440", tint: ["rgba(8,12,40,.66)"], cloud: "rgba(150,160,200,.22)" },
};

/** The canvas's own pixels; the stylesheet scales it up without smoothing. */
const WIDTH = 324;
const HEIGHT = 62;
/** The landscape is twice the canvas wide and wraps, so drifting never runs out. */
const LOOP = 648;

export function timeOfDay(hour: number): TimeOfDay {
  if (hour >= 7 && hour < 17) return "day";
  if ((hour >= 17 && hour < 20) || (hour >= 5 && hour < 7)) return "sunset";
  return "night";
}

type Layers = {
  clouds: HTMLCanvasElement;
  far: HTMLCanvasElement;
  near: HTMLCanvasElement;
  stars: [number, number, number][];
};

/** The scene's layers, the same every time for a time of day: the seed is fixed. */
function buildLayers(look: (typeof LOOKS)[TimeOfDay]): Layers {
  let seed = 11;
  const random = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const layer = () => {
    const canvas = document.createElement("canvas");
    canvas.width = LOOP;
    canvas.height = HEIGHT;
    return canvas;
  };
  const pen = (canvas: HTMLCanvasElement) => canvas.getContext("2d")!;
  const wave = (x: number, parts: [number, number][]) =>
    parts.reduce((sum, [k, a]) => sum + a * Math.sin((2 * Math.PI * k * x) / LOOP), 0);

  const clouds = layer();
  const cloudPen = pen(clouds);
  cloudPen.fillStyle = look.cloud;
  for (let n = 0; n < 9; n++) {
    const x = Math.floor(random() * LOOP);
    const y = 4 + Math.floor(random() * 14);
    const w = 18 + Math.floor(random() * 26);
    cloudPen.fillRect(x, y, w, 4);
    cloudPen.fillRect(x + 5, y - 2, w - 12, 2);
    if (x + w > LOOP) cloudPen.fillRect(x - LOOP, y, w, 4);
  }

  const far = layer();
  const farPen = pen(far);
  farPen.fillStyle = look.far;
  for (let x = 0; x < LOOP; x++) {
    const y = Math.round(30 + wave(x, [[2, 4], [7, 2]]));
    farPen.fillRect(x, y, 1, HEIGHT - y);
  }

  const near = layer();
  const nearPen = pen(near);
  const ground: number[] = [];
  for (let x = 0; x < LOOP; x++) ground.push(Math.round((42 + wave(x, [[3, 4], [9, 2]])) / 2) * 2);
  for (let x = 0; x < LOOP; x++) {
    const top = ground[x]!;
    for (let y = top; y < HEIGHT; y++) {
      const depth = y - top;
      const base = depth < 2 ? [92, 156, 58] : depth < 14 ? [134, 96, 67] : [118, 118, 118];
      const grain = (random() - 0.5) * 22;
      nearPen.fillStyle = `rgb(${(base[0]! + grain) | 0},${(base[1]! + grain) | 0},${(base[2]! + grain) | 0})`;
      nearPen.fillRect(x, y, 1, 1);
    }
  }
  for (const tx of [40, 150, 230, 380, 470, 590]) {
    const base = ground[tx]!;
    const trunk = 10 + Math.floor(random() * 6);
    nearPen.fillStyle = "#6b4f2c";
    nearPen.fillRect(tx, base - trunk, 3, trunk);
    for (let i = 0; i < 60; i++) {
      nearPen.fillStyle = random() > 0.5 ? "#3e7a2c" : "#356a25";
      nearPen.fillRect(tx - 7 + Math.floor(random() * 17), base - trunk - 7 + Math.floor(random() * 10), 2, 2);
    }
  }

  for (const canvas of [far, near]) {
    const tinted = pen(canvas);
    tinted.globalCompositeOperation = "source-atop";
    for (const tint of look.tint) {
      tinted.fillStyle = tint;
      tinted.fillRect(0, 0, LOOP, HEIGHT);
    }
  }

  const stars: [number, number, number][] = [];
  for (let n = 0; n < 70; n++) stars.push([Math.floor(random() * WIDTH), Math.floor(random() * 34), random() * 6.28]);
  return { clouds, far, near, stars };
}

function drawFrame(pen: CanvasRenderingContext2D, when: TimeOfDay, layers: Layers, seconds: number) {
  const look = LOOKS[when];
  const sky = pen.createLinearGradient(0, 0, 0, HEIGHT * 0.7);
  look.sky.forEach((colour, k) => sky.addColorStop(k / (look.sky.length - 1), colour));
  pen.fillStyle = sky;
  pen.fillRect(0, 0, WIDTH, HEIGHT);

  if (when === "night") {
    for (const [x, y, phase] of layers.stars) {
      pen.fillStyle = `rgba(255,255,255,${0.35 + 0.35 * Math.sin(seconds * 1.3 + phase)})`;
      pen.fillRect(x, y, 1, 1);
    }
    pen.fillStyle = "#e8ecf5";
    pen.fillRect(64, 8, 8, 8);
    pen.fillStyle = "#c6ccd9";
    pen.fillRect(66, 10, 2, 2);
    pen.fillRect(69, 13, 1, 1);
  } else if (when === "sunset") {
    pen.fillStyle = "#ffb36b";
    pen.fillRect(232, 24, 14, 14);
    pen.fillStyle = "rgba(255,170,100,.35)";
    pen.fillRect(228, 20, 22, 22);
  } else {
    pen.fillStyle = "#fff6c8";
    pen.fillRect(262, 7, 10, 10);
  }

  const drift = (image: HTMLCanvasElement, speed: number) => {
    const offset = Math.floor((seconds * speed) % LOOP);
    pen.drawImage(image, -offset, 0);
    pen.drawImage(image, LOOP - offset, 0);
  };
  drift(layers.clouds, 3);
  drift(layers.far, 1.2);
  drift(layers.near, 4);
}

export function PixelScene() {
  const canvas = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const pen = canvas.current?.getContext("2d");
    if (!pen) return;
    const still = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let when = timeOfDay(new Date().getHours());
    let layers = buildLayers(LOOKS[when]);
    const started = performance.now();
    // Asked again as it draws, so a launcher left open from afternoon into
    // the evening turns to sunset on its own.
    const followTheClock = () => {
      const now = timeOfDay(new Date().getHours());
      if (now !== when) {
        when = now;
        layers = buildLayers(LOOKS[when]);
      }
    };

    if (still) {
      drawFrame(pen, when, layers, 0);
      const minute = window.setInterval(() => {
        followTheClock();
        drawFrame(pen, when, layers, 0);
      }, 60_000);
      return () => window.clearInterval(minute);
    }

    let frame = 0;
    const draw = (now: number) => {
      followTheClock();
      drawFrame(pen, when, layers, (now - started) / 1000);
      frame = requestAnimationFrame(draw);
    };
    frame = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(frame);
  }, []);

  return <canvas ref={canvas} className="scene" width={WIDTH} height={HEIGHT} aria-hidden="true" />;
}
