# Sodium and Lithium ship unmodified; our own optimisations target what they don't

> **Superseded in part by [ADR-0013](0013-sodium-is-not-bundled.md).** Sodium is PolyForm Shield 1.0.0, not LGPL-3.0, so the reasoning below applies only to Lithium.

Sodium and Lithium are LGPL-3.0. They ship as separate, unmodified, user-replaceable jars inside an instance so that ash's own code stays proprietary — shading their classes into our jar, patching their bytecode, or forking them would each pull us into the licence. ash's own render and tick work therefore targets territory they do not cover.

## Consequences

- **No ash mixin may target a Sodium or Lithium class.** This is a licence boundary and a practical one: Sodium replaces the terrain renderer outright, so any mixin of ours into `WorldRenderer` or chunk building would conflict, be silently overridden, or fail at load.
- On modern targets, Sodium owns terrain rendering and Lithium owns game-logic ticking. ash's own work takes entity rendering, HUD and text rendering, particles, and client-side tick — the hotspots neither covers, and the ones that actually bite on a busy PvP screen.
- On 1.8.9 no Sodium equivalent exists, so the entire pipeline is ours. Modern-target and 1.8.9 optimisation are two separate bodies of work, not one shared pipeline.
- Attribution and a written offer for their source must ship with the client.
- This reading of LGPL-3.0 is engineering judgement, not legal advice. Get counsel before taking money for this.
