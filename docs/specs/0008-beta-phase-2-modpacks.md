# Beta phase 2: Modpacks

*Designed 2026-10-28 in a grilling session with the product owner (see 0006, the beta). Every decision below was put to them and answered. The few written down as assumptions are marked.*

## Problem Statement

A player's modded game usually lives somewhere else already, as a Modrinth pack, a Prism instance or a Modrinth app profile, and ash can't take any of it in:
- **Packs don't import.** A `.mrpack` file means nothing to ash, and the Mods page finds single mods, not packs.
- **Most packs couldn't run anyway.** ash runs Fabric only on its version targets. The most-downloaded Fabric packs are on 1.20.1, 1.21.1 and 26.x, and only 26.3 will be a target.
- **Other launchers' instances stay there.** Moving an instance from Prism, MultiMC or the Modrinth app means rebuilding it mod by mod and copying configs by hand. The official launcher's worlds, resource packs and server list don't come across either.
- **Nothing updates.** A mod installed from the Mods page stays at the version it was installed at. `ash-mods.json` records what an update would need, but nothing reads it for that.
- **Nothing goes back out.** A player can't hand their ash setup to a friend.

## Solution

- **Fabric on any version.** An instance can have Fabric on any release Fabric supports, 1.14 to 26.3. On a version target it has ash's client. On any other version it's a **Fabric-only instance**: plain Fabric, with the player's mods and no ash features, and it says so. New instance offers every version this way.
- **Modrinth modpacks:**
  - import a `.mrpack` file, or browse and install Modrinth packs from the Mods page;
  - Fabric packs import on any version, and on a version target they get ash's client too;
  - Forge, NeoForge and Quilt packs are refused, with the reason.
- **Imports from other launchers:**
  - instances from Prism and MultiMC, and profiles from the Modrinth app, with mods, configs, resource and shader packs, worlds and servers;
  - from the official launcher, worlds, resource packs, screenshots and the server list, into an ash instance.
  - An import copies; it never changes or removes anything in the other launcher.
- **Export:** an instance can be saved as a `.mrpack` that ash or any launcher imports.
- **Updates, on request.** The Mods page shows an update for any mod or pack that has one, and Update all updates them in one click. Nothing updates without the player asking. The player's configs and worlds are kept.

## User Stories

### Fabric on any version

1. As a player, I want to make an instance on any Minecraft version Fabric supports, so that I can play the versions my friends and servers use.
2. As a player, I want New instance to mark versions without ash's client "Fabric only", so that I know before I choose what I'll get.
3. As a player in a Fabric-only instance, I want its tile and details to say ash's features need one of ash's versions, so that I don't wonder where the crosshair went.
4. As a player, I want a Fabric-only instance to get the right Java by itself, so that old and new versions just launch.
5. As a player, I want my mods to load in a Fabric-only instance without switching anything on, so that mods are simply the point of it.
6. As a player, I want the Mods page to install Fabric API like any other mod in a Fabric-only instance, so that mods that need it work.
7. As a player, I want a Fabric-only instance to show its play time, servers and last session like any other, so that it's a first-class instance.
8. As a player, I want ash to choose the newest stable Fabric Loader for a new Fabric-only instance, so that I don't need to know what a loader version is.

### Modrinth modpacks

9. As a player, I want to import a `.mrpack` file by choosing it or dropping it on ash, so that a pack someone sent me becomes an instance.
10. As a player, I want to browse Modrinth's modpacks in ash and install one in a click, so that I never need a website to get a pack.
11. As a player, I want to search packs and sort them like mods, so that I find packs the way I find mods.
12. As a player, I want a pack's name, icon and description on the instance it makes, so that I can tell my instances apart.
13. As a player, I want to see which version and loader a pack needs before I install it, so that I'm not surprised.
14. As a player, I want a Forge, NeoForge or Quilt pack refused with a plain reason, so that I know it's not something I did wrong.
15. As a player, I want a pack on one of ash's versions to get ash's client as well as its mods, so that I have both.
16. As a player, I want the pack's own config files in place after import, so that it plays as its author meant.
17. As a player, I want optional mods in a pack offered as choices at import, so that I take only the extras I want.
18. As a player, I want every file a pack downloads checked against the hash the pack lists, so that I never run a tampered file.
19. As a player, I want a pack that asks for files from an unknown website refused, so that a pack can't make ash download from anywhere.
20. As a player, I want a pack with a file path that escapes its instance refused, so that a pack can't write elsewhere on my computer.
21. As a player, I want a pack's download to show progress and resume after a dropped connection, so that big packs import reliably.
22. As a player, I want to cancel an import, with nothing half-made left behind, so that a mistake costs nothing.
23. As a player, I want a pack whose mods duplicate ash's own features noted, as the Mods page notes a single mod today, so that I can choose which to keep.

### Importing from other launchers

24. As a Prism or MultiMC player, I want ash to find my instances and import one, so that I don't rebuild it.
25. As a player, I want to point ash at an instance folder myself, so that a portable or unusual install still imports.
26. As a Modrinth app player, I want ash to find my profiles and import one, so that I can move over.
27. As a player, I want an import to bring mods, configs, resource and shader packs, worlds and the server list, so that the instance is as it was.
28. As a player, I want an imported instance to keep its version and Fabric Loader, so that it behaves exactly as before.
29. As a player, I want a Forge, NeoForge or Quilt instance refused with a plain reason, so that I know why it didn't import.
30. As a player, I want an import never to change or remove anything in the other launcher, so that I can keep using it.
31. As a player of the official launcher, I want to bring my worlds, resource packs, screenshots and server list into an ash instance, so that I keep what I made.
32. As a player, I want to choose which worlds to bring in, so that I don't copy gigabytes I don't need.
33. As a player, I want a warning if a world was last played on a newer version than the instance, so that I don't break a world by opening it on an older game.
34. As a player, I want servers from another list added to my instance's list without duplicates, so that nothing I had is lost.

### Export

35. As a player, I want to save an instance as a `.mrpack`, so that I can send my setup to a friend.
36. As a player, I want the export to name mods that are on Modrinth by link rather than copying them, so that the file stays small and downloads stay with Modrinth.
37. As a player, I want mods that aren't on Modrinth included as files, so that the pack still works.
38. As a player, I want to choose whether configs, resource packs and shader packs go in, so that I share only what I mean to.
39. As a player, I want my worlds, screenshots and logs left out, so that I never share them by accident.
40. As a player whose instance is on 1.8.9, I want to be told why it can't be exported as a `.mrpack`, so that the button isn't a mystery.

### Updates

41. As a player, I want the Mods page to show which of my mods have updates for my version, so that I know without checking each one.
42. As a player, I want to update one mod, or Update all, so that keeping current takes one click.
43. As a player, I want an update to bring the new version's required dependencies, so that an update never leaves a mod broken.
44. As a player, I want an update to keep my configs, so that updating doesn't reset my settings.
45. As a player, I want mods I added by hand checked for updates too, if Modrinth knows them, so that every mod can stay current.
46. As a player, I want an instance made from a pack to show when the pack has a new version, so that I get the author's fixes.
47. As a player, I want a pack update to keep mods I added myself, so that my additions survive.
48. As a player, I want a pack update to leave config files I changed alone, so that my tweaks survive.
49. As a player, I want a pack update that needs a newer Minecraft version offered as a new instance, so that my world isn't upgraded behind my back.
50. As a player, I want nothing to update unless I click, so that a setup that works stays working.
51. As a player, I want updates refused while the game is running, with the reason, so that ash never pulls a jar out from under a game.

## Implementation Decisions

### Fabric on any version
- **The versions:** an instance's version can be any **stable** game version Fabric's meta lists, from 1.14 on. Version targets are a subset that also gets ash's client. 1.8.9 stays Legacy Fabric, as now; Legacy Fabric on other old versions is out of scope.
- **Fabric-only instance** is a new glossary term: an instance with the Fabric loader on a version that isn't a version target.
  - It's still an instance like any other, with its version and loader fixed at creation.
  - ash's client and bundled mods aren't added to it.
  - Its third-party mods are always on, so the opt-in doesn't apply and isn't shown.
- **The loader:**
  - at creation, ash records the newest stable Fabric Loader for that version, or a pack's or import's own loader version;
  - the loader profile comes from Fabric's meta, merged onto Mojang's version document as for the targets;
  - its libraries come from Fabric's Maven and are checked against the Maven's published hashes. Research 0017 settles which hashes Fabric's Maven publishes and how ash verifies them (ADR-0024 applies).
- **Fabric API:** "Included with ash" applies only to version targets. In a Fabric-only instance, Fabric API is an ordinary Modrinth mod that dependency resolution installs.
- **New instance:** the version list shows every stable release, with "Fabric only" beside non-target versions.
- **The instance's tile and glance:** a Fabric-only instance says "Fabric only". Its glance says ash's features need 1.8.9, 1.20.4, 1.21.11 or 26.3.

### Modrinth modpacks
- **The `.mrpack` format, as Modrinth documents it (research 0017 confirms):**
  - `modrinth.index.json` lists files with paths, hashes, sizes, download URLs and client/server environment;
  - its dependencies name the Minecraft version and the loader;
  - `overrides/` and `client-overrides/` are copied into the game directory.
- **What imports:**
  - **Fabric:** a pack whose loader dependency is `fabric-loader` imports, on any version.
  - **Not Fabric:** `forge`, `neoforge` and `quilt-loader` packs are refused, with a message naming the loader and saying ash runs Fabric.
  - **On a version target:** a pack on a target gets ash's client. Its mods are third-party mods, switched on at import.
  - **The pack's own Fabric API** is left out on a target, since ash bundles one. Research 0017 checks what Fabric Loader does with two copies, and settles this.
- **Safety:**
  - downloads only from Modrinth's own allowed domains for packs: `cdn.modrinth.com`, `github.com`, `raw.githubusercontent.com` and `gitlab.com`;
  - every file is checked against its SHA-512 before it's kept;
  - any path or override entry that would land outside the game directory refuses the whole pack;
  - files whose client environment is `unsupported` are skipped;
  - `optional` ones are offered as choices at import.
- **Browsing:**
  - the Mods page gains a Modpacks view: Modrinth search with the `modpack` project type and the Fabric category;
  - it has the same sorts as mods, but no Suggested;
  - installing a pack makes a new instance from it.
  - The `.mrpack` button and drop target live there too, and on the instance list's New menu.
- **The instance remembers its pack.** A new per-instance record, `ash-modpack.json`, holds:
  - the pack's project and version;
  - the index's files with their hashes;
  - a hash of every override file as imported.
  Updates use it. Removing the pack's files doesn't touch anything the player added.
- **Progress and cancel:** downloads go through the depot's progress events and resume. Cancelling removes the half-made instance and nothing else.

### Imports from other launchers
- **Prism and MultiMC:**
  - **Where:** found in Prism's default instances folder; any other folder can be chosen.
  - **What's read:** `instance.cfg` gives the name. `mmc-pack.json`'s components give the Minecraft version and the Fabric Loader version.
  - **Refused:** any other loader component (Forge, NeoForge, Quilt, LiteLoader).
- **Modrinth app:** its profiles are found in the app's data folder. Research 0017 establishes where the app keeps each profile's version and loader, which in current releases is its own database. ash only reads it.
- **What comes across:**
  - the game folder's `mods`, `config`, `resourcepacks`, `shaderpacks`, `saves`, `screenshots` and `servers.dat`;
  - `options.txt`, so key bindings and video settings carry over.
  - Logs and crash reports don't.
- **Copying, never moving.** The other launcher's folders are opened read-only. Big copies show progress and can be cancelled.
- **The official launcher:** "Bring in from Minecraft" on an instance's page copies chosen worlds, resource packs, screenshots and the server list from `.minecraft` into that instance.
  - **Newer worlds:** a world whose saved version is newer than the instance's is marked, and taking it needs a second confirmation.
  - **Servers:** they're merged into the instance's server list, skipping addresses it already has. This uses the existing server-list editing (ADR-0019).

### Export
- **Export as `.mrpack`** is on an instance's page.
- **Mods:** each mod is looked up on Modrinth by hash. Found mods are listed by URL and hash; others go into `overrides/mods`.
- **Choices:** configs, resource packs and shader packs are each on by default and can be unticked.
- **Never included:**
  - saves, screenshots, logs, crash reports;
  - ash's own folder, `options.txt` and `servers.dat`.
- **Not exportable:** a 1.8.9 instance, since a `.mrpack` can't name Legacy Fabric; the button says why. ash's client and bundled mods are never in an export; a friend importing it into ash gets them from ash.

### Updates
- **Mods:**
  - **Finding updates:** opening the Mods page asks Modrinth, in one request by file hash, for the newest version of every mod in the instance that fits the instance's version and loader. That covers ash-installed mods and hand-added jars Modrinth knows by hash.
  - **What shows:** each mod with an update shows it, and Update all appears when any do.
  - **Applying one:** an update replaces the jar the same way an install writes one. It's verified, recorded in `ash-mods.json`, and brings new required dependencies.
  - **What stays:** the old jar is removed only after the new one is in place, and configs aren't touched. A mod switched off stays off.
- **Packs:**
  - **Finding updates:** a pack instance asks Modrinth for the pack's newest version for the instance's Minecraft version and loader.
  - **Mods:** a pack update installs files the new index adds and replaces ones it changes. It removes files the new index drops, but only if they're unchanged since import. Files the player added are never touched.
  - **Config files:** an override file is replaced only if the player hasn't changed it since import, by the hash in `ash-modpack.json`. Ones the player changed are kept, and listed after the update.
  - **New Minecraft versions:** a pack version for a newer Minecraft version isn't an update. It's offered as "Make a new instance from it".
- **Not while it runs:** updates are refused while that instance's game is running, as removing a mod is today.

## Testing Decisions

- **Fabric on any version:**
  - ash-core tests against fake Fabric meta and Maven: creating and launching a Fabric-only instance builds the right invocation;
  - the tests check that no ash client or bundled mod is in it, and that its mods are always loaded;
  - the hash checks refuse a tampered library.
  - Prior art: `tests/fabric.rs`.
- **Modpacks:**
  - ash-core tests import recorded `.mrpack` fixtures: a plain Fabric pack, one on a version target, a Forge pack (refused), and a pack with a download from a foreign domain (refused);
  - also a pack with a `../` path (refused), and one with optional files;
  - the instance's files, `ash-modpack.json` and the third-party switch are asserted.
  - Prior art: `tests/mods.rs`.
- **Imports:**
  - fixture folders laid out like a Prism instance, a MultiMC instance, a Modrinth app profile and a `.minecraft`;
  - the tests assert what's copied, what's refused, the newer-world mark, the server merge without duplicates, and that the source folder's bytes are unchanged afterwards.
- **Export:** an exported pack's index is asserted against a fixture instance with a fake Modrinth hash lookup. It's imported back to check the round trip.
- **Updates:**
  - fake Modrinth answers for the hash-based update lookup and for pack versions;
  - the tests cover replacing, keeping configs, keeping player-added mods, leaving changed overrides, the newer-Minecraft refusal, and the refusal while the game runs.
- **Screens:** the Modpacks view, the import pickers, export choices, update badges and Update all are `ui-check` states.

## Out of Scope

- CurseForge packs and the CurseForge app.
- Forge, NeoForge and Quilt, as instances or packs.
- Legacy Fabric on versions other than 1.8.9, and exporting 1.8.9 instances.
- Automatic updates.
- Syncing a pack instance's mods between computers. Instance definitions sync as now; their mods don't.
- Publishing packs to Modrinth from ash.

## Further Notes

1. **Research before modpacks: research 0017.** It covers:
   - the `.mrpack` format as Modrinth documents it;
   - which hashes Fabric's Maven publishes, and how ash verifies Fabric-only libraries;
   - what Fabric Loader does with two copies of Fabric API;
   - where the Modrinth app keeps profiles' versions and loaders;
   - Modrinth's hash-based update endpoint;
   - whether Legacy Fabric packs on Modrinth declare `fabric-loader`.
2. **Assumed, not asked:**
   - what an export includes by default: mods, configs, resource packs and shader packs, but not worlds;
   - `options.txt` coming across on import;
   - "Bring in from Minecraft" living on an instance's page.

## Tracker

Filed as [#170](https://github.com/sisi-j/ash/issues/170), a sub-issue of the beta's tracker, [#168](https://github.com/sisi-j/ash/issues/168), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. Its tickets, #187 to #197, are its sub-issues, with blocking links set. This file is the source of truth, and the issue is the tracker entry.
