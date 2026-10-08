# Sodium is not bundled

ash ships no Sodium. ~~Lithium stays.~~ Lithium is not bundled either (amended 2026-10-08, below). A player who wants Sodium installs it themselves as a third-party mod.

This supersedes the Sodium half of ADR-0004, which was built on a licence that is not Sodium's licence.

## Context

ADR-0004 said Sodium and Lithium are LGPL-3.0, and that shipping them unmodified, at arm's length, as user-replaceable jars keeps ash's own code proprietary. That is sound reasoning about the LGPL. It is the wrong instrument for Sodium.

Sodium is **PolyForm Shield 1.0.0** — the licence file on `dev`, on `mc1.21.11-0.8.14` and on `mc26.2-0.9.2`, and the shipped jar's own `fabric.mod.json` (`"license": "Polyform-Shield-1.0.0"`). Its Noncompete clause reads: *"Any purpose is a permitted purpose, except for providing any product that competes with the software or any product the licensor or any of its affiliates provides using the software"*, and *"Goods and services compete even when provided free of charge"*.

The LGPL constrains how you **link**. PolyForm Shield constrains what you **build and sell**. Keeping Sodium in a separate, unmodified, user-replaceable jar answers the first question completely and the second not at all. ash is a client that does its own render work and intends to sell cosmetics; whether that competes with Sodium is a question about the product, and no amount of architectural distance changes the answer.

Lithium is genuinely LGPL-3.0-only, so ADR-0004's reasoning still holds for it.

There is a second, independent problem. ADR-0004 assigned ash "entity rendering, HUD and text rendering, particles, and client-side tick — the hotspots neither covers". Sodium's own shipped mixin manifest covers glyph rendering, GUI graphics, entity culling, model parts, entity shadows, particles, and the immediate-mode buffer path that every HUD and entity draw passes through. The territory is contested, not disjoint. And Sodium's configs are `"required": true` with `injectors.defaultRequire: 1`, so an ash mixin that disturbs a method Sodium overwrites is a crash on launch, not a lost frame.

## Decision

Do not bundle Sodium. Revisit before Phase 4, with legal advice, when cosmetics revenue makes the question real.

## Consequences

- The licence question is deferred, not answered. This ADR does not conclude that bundling Sodium would breach PolyForm Shield — it concludes that ash does not need to find out yet, and that finding out is a lawyer's job rather than an architect's.
- ash's own render work no longer has to be designed around territory Sodium already occupies, which removes a constraint that was shaping the client's architecture on a false premise.
- Players lose Sodium's frame rate unless they install it themselves. On a competitive client that is a real cost, and it is the reason to revisit rather than close this.
- The bundled-mod machinery still earns its keep for Lithium, so nothing built for it is wasted.
- On 1.8.9 nothing changes: no Sodium equivalent exists there, and that pipeline was always ash's own.

## Amended 2026-10-08: Lithium is not bundled either

"Lithium stays" was conditional from Phase 3 on: the spec bundles it only if it makes the game measurably faster (user story 39), because Lithium's own documentation credits its gains to singleplayer's built-in server, not to play on a server (`docs/research/0006`). #44 measured it on 1.21.11 with the frame-time measurement. Lithium `0.21.4+mc1.21.11`, off and on in alternating runs, in singleplayer and on a vanilla dedicated server:

| Where | Off | On | Change, pair by pair |
| --- | --- | --- | --- |
| Singleplayer | 626.1, 656.5 FPS | 627.1, 671.5 FPS | +0.2%, +2.3% |
| On a server | 637.1, 622.2 FPS | 627.3, 644.4 FPS | -1.5%, +3.6% |

Averaged, +1.2% and +1.0%: inside the ±3% the spec counts as no change. The runs and how they were taken are in `client/benchmark-results/README.md`, under *Lithium on 1.21.11*.

So ash bundles **neither Sodium nor Lithium**. A player who wants either adds it as a third-party mod.

Consequences:
- **No bundled-mod work for Lithium.** The depot pin, the LGPL and GPL texts and the source directions it would have needed are not built. The bundled-mod machinery stays, for Fabric API and Legacy Fabric.
- **Frame time is what was asked, and what was measured.** Lithium's claims are about the built-in server's tick time. On a weaker machine, a slow singleplayer tick may matter in a way this laptop's frame rate doesn't show. If that is ever the question, it needs its own measurement of tick time, not a reading of this one.
- **Revisited the way it was decided:** a new Lithium release, or a new target, is measured with the same runs before anything changes.
