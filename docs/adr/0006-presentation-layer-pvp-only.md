# PvP features are presentation layer only

ash ships features that change how the game is *displayed* — crosshair, hit indicators, ping and FPS readouts, hit colour, toggle sprint, freelook — and never features that change game mechanics. Reach and hitbox modification, autoclickers, aim assist, X-ray, ESP and tracers are permanently out of scope.

## Consequences

- The line is: does the server's view of the game change, or would a fair player be disadvantaged by not having it? If either, it doesn't ship.
- This is not a v1 scoping decision to relax later. A single banned feature gets the whole client blocked by every major server and there is no recovering that reputation.
- Expect this to be argued repeatedly by contributors and users. The answer does not change.

## Amended 2026-09-30: freelook ships, switched off on servers that ban it

Research for Phase 3 (`docs/research/0006-freelook-lithium-and-launcher-typefaces.md`) applied this ADR's test to freelook and found it split.
- **The server's view does not change.** Freelook turns the camera and leaves the player's own rotation, which is what movement packets carry, untouched. Anything else would be a bug.
- **Whether a player without it is disadvantaged depends on the server.**
  - Hypixel bans freelook in writing as *"a significant unfair advantage"* and makes the player responsible for their client. MCC Island and Hoplite ban it by name, drawing the line at what vanilla's F5 camera can show.
  - CubeCraft, PikaNetwork and PvPHQ list freelook, or an equivalent, as allowed.

So the answer to "would a fair player be disadvantaged by not having it?" belongs to each server. ash ships freelook, and switches it off on servers whose published rules ban it, from a short list inside the client. Each entry cites the rules page that bans freelook. On a listed server the key says that freelook is off and why. The research recommended cutting it. Shipping it with a block was the product owner's decision, recorded here so that the next person to ask does not have to reconstruct it.

Consequences:
- **Missing a server is the accepted risk.** A server that bans freelook but is not on the list is a ban for a player who uses it there, and Hypixel's own rules say that responsibility is the player's. That is why the list is short, sourced and tested, and why an unlisted server's ban is a reason to update it rather than to argue.
- The list changes only with an ash release until a backend can serve it.
- This does not relax the rule for anything else. A feature that fails the test everywhere still does not ship, and a switch the player could leave on where a feature is banned is still not a block.
- **Snaplook passes the test without a block.** It holds vanilla's front-facing third-person view, and shows nothing F5 cannot.

**Toggle sprint and "auto-sprint".** Hypixel's allowed-modifications page lists *"auto-sprint"* as disallowed automation, without defining it. ash's toggle sprint is not that:
- it sprints only after the player presses a key, and stops on the next press;
- it reads to the game exactly as a held key does, and releases wherever a held key would;
- it is the behaviour vanilla 1.21.11 ships as its own option (`Options.toggleSprint`), brought to 1.8.9.

CubeCraft and PikaNetwork list toggle sprint as allowed. If Hypixel ever names it, this paragraph is what changes.
