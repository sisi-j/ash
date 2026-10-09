# Cosmetic lookups and the emote relay keep no record of who was near whom

To show other players' cosmetics and emotes, every ash client tells the backend which players it can see. That's a stream of "this player was near these players" that, kept, would be a record of who plays with whom, and roughly where. ash keeps none of it.

## Decision

- **Lookups never reach ash's code.** *(Amended 2026-10-09 by research 0010.)* Each account's equipped cosmetics are published as a static file, `profiles/<uuid>.json` in R2. It's written when the player equips something and deleted with the account. Clients read it through an edge-cached public domain, so a lookup doesn't invoke the Worker at all. ash has nothing to log, and doesn't depend on remembering not to. A missing file means no ash cosmetics.
- Nothing about who asked for whom is written anywhere by ash: no table, no log line, no analytics event.
- **The emote relay subscribes by player, not by server.** A client subscribes to the UUIDs it can currently see. A started emote goes only to that player's subscribers. The backend never learns which Minecraft server anyone is on. Subscriptions live in memory for the socket's life and are never stored.
- Rooms keyed by server address were rejected: they're simpler, but the backend would learn where everyone plays.

## Consequences

- Backend tests assert that the relay writes nothing, and that equipping writes exactly the player's own profile file, and that deleting the account removes it.
- The lookups also stay within Cloudflare's free allowance: a busy server would otherwise have cost a Worker request per player seen.
- Abuse is limited by rate limits, not by identifying the caller.
- ash-site's privacy policy describes both before either goes live.
