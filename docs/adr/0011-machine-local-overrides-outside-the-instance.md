# Machine-local overrides live outside the instance

Memory allocation, Java path and window resolution are stored under ash's data root at `data/machine/<instance id>.json`, not inside `instances/<id>/`. They are a distinct type, `MachineOverrides`, and no field of `Instance` can hold one.

## Context

Phase 4 syncs an account's launcher state between machines. An instance is a directory: its definition, its worlds, its mods, its screenshots. That directory is what a sync, a backup, or a player copying a folder to a second machine would carry.

Some settings only make sense on the machine they were set on. A memory figure from a 32GB desktop is a game that will not start on an 8GB laptop. A Java path that exists on one machine is a spawn failure on another. A resolution chosen for a 4K monitor is a window off the edge of a 1080p screen.

## Decision

The boundary is **storage, not discipline**.

- `MachineOverrides` is its own type. `Instance` holds no field of that type, so whatever a future sync serialises an `Instance` into cannot contain one.
- The file lives under the data root, which is ash's state for *this* machine, and never inside `instances/`.

A future sync cannot leak one of these values by forgetting a filter, because it would have to go looking somewhere else entirely to find it.

## Consequences

- Deleting an instance must explicitly forget its overrides. Removing the instance directory does not remove them, and an orphan would be inherited by the next instance that happened to take the same id. `Ash::delete_instance` does this, and a test reuses an id to prove it.
- An instance's configuration is split across two roots. This is the point, and it is worth the mild surprise: the split is exactly the line between "what this instance is" and "what this machine can run".
- Copying an instance directory to another machine carries the worlds and the mods and leaves the tuning behind, which is the correct outcome.
- A player naming a Java binary is a machine-local override, not ash consulting the system. `runtime.rs` still never reads `PATH` or `JAVA_HOME`; ADR-0007's position that ash manages its own runtimes is unchanged. The override is an escape hatch for a machine the provisioned runtime cannot run on, and it is applied at launch rather than by searching.
- `None` means "whatever ash would do", which is deliberately not the same as a stored value that happens to match today's default. A player who never opens the settings screen follows the default as it changes.

## Enforcement

Two tests carry this, and both assert behaviour rather than intent:

- Setting a distinctive memory figure and resolution, then walking every file under `instances/` and asserting none of them contains either value.
- Serialising an `Instance` and asserting the output names no machine-local key.
