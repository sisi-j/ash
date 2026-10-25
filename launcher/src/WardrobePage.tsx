import { useEffect, useRef, useState } from "react";
import { SkinViewer } from "skinview3d";
import { api, isUiError, type AshAccountStatus, type Cosmetic, type Wardrobe } from "./api";
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

  // Opening the Wardrobe signs in to ash if it has to, so this is also what
  // "Try again" does.
  const load = async () => {
    setBusy(true);
    try {
      setWardrobe(await api.wardrobe());
      setProblem(null);
    } catch (e) {
      setProblem(isUiError(e) ? e.message : "ash couldn't load the Wardrobe.");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    void load();
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
        <div className="notice" role="status">
          <p>{whyNotSignedIn(wardrobe.ash_account)}</p>
          {wardrobe.ash_account.state !== "deleted" && wardrobe.ash_account.state !== "refused" && (
            <button className="button" disabled={busy} onClick={() => void load()}>
              {busy ? "Signing in..." : "Try again"}
            </button>
          )}
        </div>
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

/** Why a cape can't be chosen yet, and what happens next: never a dead end. */
export function whyNotSignedIn(status: AshAccountStatus): string {
  switch (status.state) {
    case "deleted":
      return "You deleted your ash account, so there's nothing to wear a cape on. Signing in to this Microsoft account again makes a new one.";
    case "refused":
      return `${status.message} Capes need an ash account.`;
    case "not_signed_in":
      return "ash couldn't sign you in to ash, because your Microsoft sign-in has ended. Sign in again from the account menu, then come back to wear a cape.";
    case "unreachable":
    case "signed_in":
      return "ash couldn't sign you in to ash just now, so a cape can't be chosen yet. ash keeps trying in the background, or you can try again now.";
  }
}

/**
 * The cape's outside face, the one seen on the player's back, cut from its 64 by 32 texture at the place the game
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
                backgroundPosition: `${-1 * scale}px ${-1 * scale}px`,
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
