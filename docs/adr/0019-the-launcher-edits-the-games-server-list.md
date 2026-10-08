# The launcher edits the game's own server list, only while the game is closed

Until now ash only read an instance's `servers.dat`: it is the game's file, written by its multiplayer screen. From here, the launcher also changes it. It can add, edit, remove and reorder servers. It does so only while that instance's game is closed, and it keeps every byte of what it does not change.

## Context

The product owner chose hand-made server entries, listed for Phase 5, as the next thing to build after Phase 3, with reordering. The game's multiplayer screen is the only other place to manage a server list, and it needs the game running first. Two programs writing one file is the risk to design around.

## Decision

- **Never while the game runs.** The game loads the list into memory and saves it back whole when the player changes it in game. A change ash made underneath would be overwritten, or survive in half. So every change is refused while ash's game for that instance is running (`server_list_in_use`).
- **A change names what it expects.** Every change but adding names the server by its position in the game's order and by the address expected there. If the file no longer has that address there, because the player changed the list in game or by hand, the change is refused (`server_list_changed`) and the launcher reads the list again.
- **Everything else stays byte for byte.** ash finds each entry's bytes and moves, drops or adds whole entries. Within an edited entry it replaces only `name` and `ip`. Every other entry, hidden ones on 1.21.11 included, and every tag ash does not know, such as an icon, a resource-pack choice or a later version's flags, is kept exactly as the game wrote it.
- **A list ash cannot read is never written** (`server_list_unreadable`).
- **Saved as the game saves.** The old file is kept as `servers.dat_old`. The new one is written beside it and moved into place, so an interruption leaves one whole file or the other.
- **The game's own limits:** names of up to 32 characters and addresses of up to 128, counted as the game counts them (UTF-16 units). An empty name becomes the game's "Minecraft Server". A new server goes after the last one the game shows, where the game puts one.

## Consequences

- **Proved against the game itself.** The launcher's tests apply a fixed set of changes to each version's real, game-written list and compare the result with a committed file. Each version's real-game test (`AshLoadsGameTest`, `AshSmokeTest`) loads that file with the game's own `ServerList`. Names in Java's modified UTF-8, emoji included, the order, Hypixel's kept icon and 1.21.11's hidden entry all have to come back as meant.
- **One more launcher fix fell out of this.** Reading names as Java's modified UTF-8, rather than plain UTF-8, fixed a name with an emoji, written by the game itself, showing garbled in the launcher.
- **A game started outside ash is not seen as running.** ash knows only the games it started. A player who edits the list in ash while running the same game directory from another launcher can lose that change when the game saves. The position-and-address check catches a list that changed before the edit, not one the game will overwrite after it.
