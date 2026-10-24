# Mods come from Modrinth, into the instance's own folder

The Mods page finds mods on Modrinth and installs them, with their required dependencies, into the instance's `mods` folder. Each file is checked against Modrinth's hash. ash's bundled copy of Fabric API (or Legacy Fabric API's modules) stands in for any dependency on it.

## Context

Testing the alpha needs mods (spec 0005). Players could already drop jars into an instance's `mods` folder and turn third-party mods on. What's missing is finding mods, getting the right file for the version and loader, and getting what each mod requires.

The glossary has said since Phase 2 that the `mods` folder is the player's alone, and that ash never puts anything there.

## Decision

- **Modrinth, and only Modrinth.** It has a free public API with hashes, explicit dependency types, and loader tags that include Legacy Fabric. Search is always filtered to the instance's game version and loader, so an install can't be for the wrong one.
- **The player's request is what puts a file in the folder.** ash adds a mod only when the player installs it, and its required dependencies with it. Jars put there by hand stay as they are, and are listed alongside.
- **Installing turns the instance's third-party mods on,** and says so. It's the same switch as on the instance page.
- **Every file is checked against Modrinth's SHA-512** before it's moved into the folder.
- **ash's bundled mods meet dependencies on themselves.** A mod requiring Fabric API never gets a second copy.
- **On and off is the `.jar.disabled` rename** other launchers use, so a folder looks the same to them.
- **What ash installed is recorded in the instance** (`ash-mods.json`), so mod updates can be added later.
- **Overlaps are noted, never blocked.** A mod that does what an ash feature does is marked. ash's features already step aside when a mod clashes with their hooks (ADR-0017).

## Consequences

- The glossary's "ash never puts anything there" becomes "only what the player asks for".
- ash depends on Modrinth being up for browsing and installing, but not for playing: installed mods are files on disk.
- Mods are machine-local. They don't sync, as worlds don't.
- CurseForge, modpacks and mod updates are later decisions, which `ash-mods.json` leaves room for.
