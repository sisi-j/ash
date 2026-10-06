# The load report knows the player's own mods, and the notice stops blaming ash for them

This amends ADR-0017. When the player's own mods were loaded, the launcher's notice no longer says a feature that did not load is ash's fault. It also says when the player's copy of a bundled mod ran in place of ash's.

## Context

ADR-0017's notice says a feature that did not load is "a problem with ash, not with your game or your setup". That was true when the only mods in the game were ash's.

Phase 3 lets a player opt an instance in to mods they supply themselves (#46). Two things then change:
- **Another mod can break one of ash's features.** A mod that rewrites the method ash's mixin targets leaves that mixin matching nothing, which is exactly how a degraded feature looks. With the player's mods in the game, ash cannot tell its own fault from theirs.
- **A player's copy of a bundled mod can silently replace ash's.** The loader keeps the newest copy of a mod whose dependencies are met, wherever it came from, and drops the other without a word (`docs/research/0005`, A.4). A player with a newer Fabric API runs ash on a version ash has not tested.

## Decision

The client records two more facts in the load report:
- `third_party_mods`: whether any mod but ash's own was loaded from the player's mods folder;
- `bundled`: for each of ash's bundled mods that loaded, whether the copy that ran was ash's or the player's.

It reads both from the loader, which records the file every mod came from. A mod nested inside another jar counts as coming from the outermost jar.

The launcher's notice uses them:
- **With the player's mods loaded**, a feature that did not load is said to be possibly their mods' doing. The player is told to turn them off and see. The notice never says the fault is ash's.
- **With the player's copy of a bundled mod in use**, the notice says so, by the mod's name, even when every feature loaded.
- **Without either**, the notice is ADR-0017's, word for word.

## Consequences

- The format grows two fields, pinned by the same shared example, `launcher/core/tests/fixtures/load-report.json`. Both fields have defaults, so a report from an older client still reads, as a session with none of the player's mods in it. That is the truth for any client before #46.
- ADR-0017's rule for the log holds:
  - the two facts reach ash's log as words ash chose (`third-party-mods=yes`, `bundled.fabric-api=player`);
  - a mod id goes through a type that admits only the shape the loader allows one, so an id in any other shape makes the report unreadable;
  - display names are never logged.
- Legacy Fabric API is an aggregator plus separately versioned modules. Each module swapped is recorded by its own id, but the notice names "Legacy Fabric API" once, since to the player it is one thing.
- The client names its bundled mods by **mod id**, not file name. One of Legacy Fabric's (`legacy-fabric-keybinding-api-v1-common`) differs from its jar's name (`legacy-fabric-keybindings-api-v1-common-1.2.0.jar`).
- Like everything in the report, these facts arrive one session late (ADR-0017). A crash with the player's mods on is handled at the moment it happens instead, by the crash card from #46.
