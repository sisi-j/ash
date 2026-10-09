# Cosmetic lookups and the emote relay keep no record of who was near whom

To show other players' cosmetics and emotes, every ash client tells the backend which players it can see. That's a stream of "this player was near these players" that, kept, would be a record of who plays with whom, and roughly where. ash keeps none of it.

## Decision

- **Lookups are anonymous and unlogged.** A batch lookup of equipped cosmetics by UUID needs no sign-in. It's answered from an edge cache where possible, and nothing about who asked for whom is written anywhere: no table, no log line, no analytics event.
- **The emote relay subscribes by player, not by server.** A client subscribes to the UUIDs it can currently see. A started emote goes only to that player's subscribers. The backend never learns which Minecraft server anyone is on. Subscriptions live in memory for the socket's life and are never stored.
- Rooms keyed by server address were rejected: they're simpler, but the backend would learn where everyone plays.

## Consequences

- Backend tests assert that the lookup and the relay write nothing.
- Abuse is limited by rate limits, not by identifying the caller.
- ash-site's privacy policy describes both before either goes live.
