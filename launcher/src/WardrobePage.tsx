import { useEffect, useRef, useState } from "react";
import { SkinViewer } from "skinview3d";
import { api, isUiError, type Cosmetic, type Wardrobe } from "./api";
import { PAGES } from "./Sidebar";

/**
 * The Wardrobe (spec 0004, step 4): your capes on a 3D preview of your own
 * skin, and choosing one. Other ash players see the change within a minute or
 * two; nobody else sees ash cosmetics at all.
 */
export function WardrobePage() {
  const [wardrobe, setWardrobe] = useState<Wardrobe | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api
      .wardrobe()
      .then(setWardrobe)
      .catch((e) => setProblem(isUiError(e) ? e.message : "ash couldn't load the Wardrobe."));
  }, []);

  const wear = async (cosmetic: Cosmetic | null) => {
    setBusy(true);
    try {
      setWardrobe(await api.equip("cape", cosmetic?.id ?? null));
      setProblem(null);
    } catch {
      setProblem("ash's servers can't be reached, so your cape hasn't changed. Try again later.");
    } finally {
      setBusy(false);
    }
  };

  const capes = wardrobe?.cosmetics.filter((c) => c.slot === "cape") ?? [];
  const worn = capes.find((c) => c.id === wardrobe?.equipped.cape) ?? null;

  return (
    <section className="page wardrobe-page">
      <h2 className="page-title">{PAGES.wardrobe.label}</h2>
      {problem && (
        <p className="notice" role="alert">
          {problem}
        </p>
      )}
      {wardrobe && !wardrobe.signed_in && (
        <p className="notice" role="status">
          Sign in to ash to wear a cape. ash signs in on its own once its servers can be reached.
        </p>
      )}
      <div className="wardrobe">
        <Preview skin={wardrobe?.skin ?? null} cape={worn?.texture ?? null} />
        <div className="wardrobe-choices">
          <h3>Cape</h3>
          <p className="wardrobe-hint">Only ash players see it. It replaces your Minecraft cape while you wear it.</p>
          <div className="wardrobe-grid" role="radiogroup" aria-label="Cape">
            <CapeChoice
              name="None"
              texture={null}
              selected={worn === null}
              disabled={busy || !wardrobe?.signed_in}
              onPick={() => void wear(null)}
            />
            {capes.map((cape) => (
              <CapeChoice
                key={cape.id}
                name={cape.name}
                texture={cape.texture}
                selected={worn?.id === cape.id}
                disabled={busy || !wardrobe?.signed_in}
                onPick={() => void wear(cape)}
              />
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

/**
 * The cape's front face, cut from its 64 by 32 texture at the place the game
 * reads it from (10 by 16 pixels at 1,1), and scaled up without smoothing.
 */
function CapeChoice(props: {
  name: string;
  texture: string | null;
  selected: boolean;
  disabled: boolean;
  onPick: () => void;
}) {
  const scale = 4;
  return (
    <button
      className={`wardrobe-choice${props.selected ? " is-selected" : ""}`}
      role="radio"
      aria-checked={props.selected}
      aria-label={props.name}
      disabled={props.disabled}
      onClick={props.onPick}
    >
      <span
        className="wardrobe-swatch"
        style={
          props.texture
            ? {
                backgroundImage: `url("${props.texture}")`,
                backgroundSize: `${64 * scale}px ${32 * scale}px`,
                backgroundPosition: `${-12 * scale}px ${-1 * scale}px`,
                width: 10 * scale,
                height: 16 * scale,
              }
            : { width: 10 * scale, height: 16 * scale }
        }
      />
      <span>{props.name}</span>
    </button>
  );
}

/**
 * The player's own skin in 3D, wearing the cape, turned to show its back.
 * WebGL can be missing (a remote desktop, a broken driver): the page then
 * says so instead of failing.
 */
function Preview(props: { skin: string | null; cape: string | null }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const viewer = useRef<SkinViewer | null>(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    if (!canvas.current) return;
    try {
      const v = new SkinViewer({ canvas: canvas.current, width: 260, height: 360 });
      v.autoRotate = true;
      v.autoRotateSpeed = 0.6;
      v.playerObject.rotation.y = Math.PI;
      v.zoom = 0.85;
      viewer.current = v;
    } catch {
      setUnavailable(true);
    }
    return () => {
      viewer.current?.dispose();
      viewer.current = null;
    };
  }, []);

  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    if (props.skin) void v.loadSkin(props.skin).catch(() => undefined);
    else v.loadSkin(null);
  }, [props.skin]);

  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    if (props.cape) void v.loadCape(props.cape).catch(() => undefined);
    else v.loadCape(null);
  }, [props.cape]);

  return (
    <div className="wardrobe-preview">
      {unavailable ? (
        <p className="notice">The 3D preview needs graphics this computer isn't giving ash right now.</p>
      ) : (
        <canvas ref={canvas} aria-label="You, wearing your cape" />
      )}
    </div>
  );
}
