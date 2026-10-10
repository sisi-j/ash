# Research 0011 — What synced settings carry, and how each is applied

*2026-10-10. Settles spec 0004's Further Notes item 6 and the details ADR-0022 left open, from ash's own code (all [ASH]).*

## Summary

- **Instance ids collide between machines:**
  - an instance's id is its directory name, a slug of its display name (`instance.rs`, `unique_id`);
  - two computers each with a "PvP" instance both have `pvp`, and they need not be the same instance;
  - so a synced instance gets a **sync id**, a random UUID kept in its metadata. It's synced under that, and the local directory id never leaves the machine.
- **Every client setting is account-wide except one:**
  - `panel.size` (Interface size) suits a screen, as the window size does, and the window size is already machine-local (ADR-0011);
  - it stays on each machine, and the rest sync.
- **The client's settings file is ISO-8859-1** (Java `Properties`), with every value ASCII. The launcher edits it as bytes mapped one-to-one to characters, changes only the value on a key's line, and appends a key the file lacks. That's the same edit the client's own `PropertiesText.withValue` makes.
- **Server lists are applied through ADR-0019's changes, never rewritten:**
  - a remote list is reached by removes, adds, edits and moves;
  - icons, hidden entries and unknown tags stay byte for byte;
  - hidden entries (1.21.11) stay local and aren't synced.
- **Times come from the machine that made the change,** so the latest change wins per setting (ADR-0022). The backend clamps a time to its own clock, so a machine whose clock runs fast can't win every later conflict.

## 1. The keys

One flat map per account, `key → (value, deleted, updated_at)`:

| Key | Value | Local source |
| --- | --- | --- |
| `feature:<property>` | the property's value, as written | every modded instance's `config/ash.properties`, `panel.size` excepted |
| `launcher:launch_sounds`, `launcher:on_game_start` | `true` / `false`, `keep_open` / `minimise` / `close` | ash-core's launcher preferences |
| `instance:<sync id>` | JSON: `name`, `version_id`, `loader`, `created_at_ms` | the instance's metadata |
| `servers:<sync id>` | JSON: `[{name, address}]` in the game's order, hidden entries excluded | the instance's `servers.dat` |

A deletion is a key with `deleted: true`. Only instances are deleted that way, and an instance's server list is marked deleted with it. A server list with nothing in it is the empty list `[]`.

## 2. Finding a local change

ash keeps, per machine, what it last agreed with the backend: `data/sync.json`, holding each key's value and time. A local value that differs from it is a local change:
- **A feature setting** that differs in one modded instance's file is a change made in game, timed by that file's modification time. If several instances differ, the newest file wins, which is the order the player made them in.
- **Preferences, instances and server lists** are timed by when the launcher sees the difference. Their edits are made in the launcher or the game, and a sync runs soon after either.

## 3. Applying a remote change

- **Only while that instance's game is closed** (ADR-0022). An instance whose game is running is skipped, and caught up on the next sync.
- **Feature settings** are written into every modded instance's `ash.properties` that differs. A vanilla instance has no client and no file.
- **A new instance from another machine** is created here with a fresh local id, its sync id and its definition. It's prepared, meaning downloaded, the first time it's launched, as any new instance is.
- **A deleted instance** is never deleted silently. The launcher asks: *Delete here* deletes it as the player's own deletion would, and *Keep here* drops its sync id, so it becomes local-only.
- **A server list** is reached through `server_list::change`. A list the game can't read is left alone, as ADR-0019 requires.

## 4. When it runs

- At startup, once the ash session is known.
- Every few minutes while the launcher is open.
- After the game exits.

All of it runs in the background: a launch never waits on a sync, so "applied before a launch" means applied by the last sync, not one started by the launch.

The switch to turn sync off is machine-local, like the overrides of ADR-0011: one computer can keep its own setup while another syncs.
