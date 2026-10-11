# Synced settings: the latest change wins per setting, applied by the launcher while the game is closed

Synced settings carry an account's feature settings, launcher preferences, instance definitions and server entries. This is the second writer that the client settings' glossary entry has waited for since Phase 2, and these are its rules.

## Decision

- **Each setting keeps its own time, and the latest change wins.** A newer change on one machine never undoes an unrelated change from another. A server list is one setting, since its order is part of it.
- **Only the launcher syncs.**
  - Before a launch, it applies remote changes to the instance's `config/ash.properties` and `servers.dat`.
  - After the session, it reads what the player changed in game and uploads it.
- **Never while the game runs.** The client stays the only writer of `ash.properties` during play, as it has been. The launcher writes it on ADR-0019's terms for `servers.dat`:
  - only while that instance's game is closed;
  - only the changed values;
  - comments, order and unknown keys kept byte for byte.
- **Feature settings belong to the account, not the instance.** A change made in one instance is copied to the player's other local instances before their next launch. A setting that exists only on 1.8.9 rides along unused on 1.21.11.
- **Deletions ask.** An instance deleted on another machine is offered for deletion here ("Delete here" or "Keep here"). Worlds are never deleted silently.
- **Machine-local overrides can't sync,** because no synced type can hold one (ADR-0011).

## Consequences

- A setting changed in game reaches the account only after the session ends. If the launcher was closed during play, it reaches it the next time ash opens. That's accepted, because the alternative is a second writer inside a running game.
- Whole-blob "latest wins" was rejected because it loses the other machine's unrelated changes. Asking the player at every conflict was rejected as noise for a single player on two machines.
- A game started outside ash still isn't seen as running (ADR-0019), so the same caveat applies to `ash.properties`.

## Amended 2026-10-10 by research 0011

- **Instances sync by a sync id, never by their directory id.** A directory id is a slug of the name, so two machines' "PvP" instances share one by accident. A new instance from another machine gets a fresh local id here.
- **`panel.size` (Interface size) stays on each machine.** It suits a screen, as the window size does (ADR-0011). Every other client setting is account-wide.
- **The backend clamps a change's time to its own clock,** so a machine with a fast clock can't win every later conflict.
- **The sync switch is machine-local.**
