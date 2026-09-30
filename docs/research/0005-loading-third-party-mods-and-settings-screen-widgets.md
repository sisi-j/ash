# Loading a player's mods apart from ash's own, and the widgets each target offers a settings screen

Research date: 2026-09-29.

Answers items 4 and 6 of Phase 3's Further Notes (#30): how to load ash's jars and a player's mods separately on the pinned Fabric Loader, on both version targets, and which vanilla widgets, events and modules the in-game settings screen can be built from.

Every claim below is labelled:

- **[PRACTICE]**: read from bytecode on this machine with `javap -c -p`, or observed on this machine. That includes running the real `KnotClient` against ash's own depot, and reading live repository listings, which are named where they are used.
- **[SOURCE]**: upstream source at a named tag, linked.
- **[DOCS]**: first-party documentation, linked.

The game-side names follow the method of `docs/research/0003-vanilla-names-for-toggle-sprint.md`. They were read from the two mapped jars Loom produced for this repository's build: Mojang's names for 1.21.11 and Legacy Yarn build 604 for 1.8.9, at the same paths 0003 gives.

Fabric Loader was read at both pinned tags, `0.19.3` and `0.19.5`, from `https://github.com/FabricMC/fabric-loader`. For every file cited here, the `-sources.jar` Gradle resolved for each pin is identical to the tag **[PRACTICE]**.

---

## Summary

**One correction before anything else.** The two targets do not run the same loader.
- 1.21.11 pins **Fabric Loader 0.19.5** (`loader.rs:313`).
- 1.8.9 pins **0.19.3** (`loader.rs:376`).

It makes no difference to this question. Between the two tags, the discovery code is byte-for-byte identical in `SystemProperties`, `FabricLoaderImpl`, the three candidate finders, `ModDiscoverer`, `ModPrioSorter` and `ModSolver`. `ModResolver` changed only in how it clears cached data after a solve **[SOURCE]**. Everything below holds for both.

### Question A: the mechanism

**Both things the spec asks for exist on the pinned loaders, and both work under the production launch ash builds.** Each behaviour below was run end to end on both targets with the real `KnotClient`, ash's depot and ash's built jars **[PRACTICE]**.

| | Third-party mods **off** | Third-party mods **on** |
| --- | --- | --- |
| ash's client and bundled mods | `-Dfabric.addMods=<file>;<file>;…`, the depot's verified jars and the installation's client jar, each as an absolute file path | the same |
| The instance's `mods` folder | `-Dfabric.modsFolder=<an empty directory ash owns>`, so the loader reads nothing from `mods` | not set, so the loader reads `<gameDir>/mods`, one level deep, as it always has |

Five things ash has to get right:

1. **Add the properties in `launch::assemble`, next to `-Xmx`, never in `LoaderPin::jvm_arguments`.** On 1.8.9 a non-empty JVM argument list in the pin suppresses the `-Djava.library.path`/`-cp` fallback, and the game would lose its classpath (§A.5).
2. **Check every `addMods` path exists before launching.** A missing one is a `WARN` and the game starts without it, so a missing client is a game with no ash in it (§A.2).
3. **There is no switch that turns the mods folder off, only one that moves it.** The loader always scans a mods folder, and creates it if it is missing. "Off" therefore means pointing it at a directory that holds no jars (§A.3).
4. **A mod present twice is not a crash.** The loader keeps the **newest version that satisfies every constraint**, whichever place it came from, and says nothing about the one it drops. The comment in `instance.rs:268` and `:313` that "a loader refuses to start when two files claim one mod id" is wrong for 0.19.x (§A.4).
5. **Today's preparation already deletes and overwrites files a player may own.** `install_bundled_mod` removes every `<artifact>-*.jar` it does not recognise and copies over any file with ash's name. That includes a player's `fabric-api-0.141.5+1.21.11.jar` and Modrinth's `legacy-fabric-api-1.20.1.jar`. Phase 2 instances should be cleaned by exact name and hash, and nothing else (§A.0, §A.6).

### Question B: building blocks per target

| Need | 1.21.11 (Mojang names) | 1.8.9 (Legacy Yarn 604) |
| --- | --- | --- |
| Screen base | `net.minecraft.client.gui.screens.Screen`, `protected Screen(Component)` | `net.minecraft.client.gui.screen.Screen`, `public Screen()` |
| On/off | `CycleButton.onOffBuilder(boolean)`, or `Checkbox.builder(Component, Font)` | `ButtonWidget` whose `message` ash rewrites (vanilla's own way, `options.on`/`options.off`). `SwitchWidget` exists but reads "Yes/No". |
| One of a fixed set | `CycleButton.builder(Function<T,Component>, T).withValues(...)` | **No cycle class.** A `ButtonWidget` stepped in `buttonClicked`, as vanilla does. |
| Whole number in a range | subclass `AbstractSliderButton(int, int, int, int, Component, double)` | `SliderWidget(PagedEntryListWidget.Listener, int, int, int, String, float, float, float, SliderWidget.LabelSupplier)`: **it exists**, but it is float-valued and 150 wide |
| Colour with opacity | four integer sliders, or `EditBox` with `setFilter` | four `SliderWidget`s, or `TextFieldWidget` with `setTextPredicate` |
| Scrolling list of rows | `ContainerObjectSelectionList<E>` with `ContainerObjectSelectionList.Entry<E>` | `EntryListWidget` with `EntryListWidget.Entry`; `ControlsListWidget` is the vanilla prior art |
| Open / close | `Minecraft.setScreen(Screen)` / `Screen.onClose()` | `MinecraftClient.setScreen(Screen)` / `setScreen(null)` |
| Drag / release | `mouseDragged(MouseButtonEvent, double, double)` / `mouseReleased(MouseButtonEvent)` | `mouseDragged(int, int, int, long)` / `mouseReleased(int, int, int)` |
| Filled rectangle / text | `GuiGraphics.fill(int, int, int, int, int)` / `GuiGraphics.drawString(Font, String, int, int, int)` | static `DrawableHelper.fill(int, int, int, int, int)` / `TextRenderer.drawWithShadow(String, float, float, int)` |
| A tick to poll the key | `ClientTickEvents.END_CLIENT_TICK`, from `fabric-lifecycle-events-v1` 2.6.15, already inside Fabric API 0.141.6 | **recommended:** ash's own mixin at `MinecraftClient.tick()`; **or** Legacy Fabric's `ClientTickEvents`, which takes two more modules |
| Modules ash must add | **none** | **none** with the mixin; with the event, `legacy-fabric-lifecycle-events-v1-common-1.8.9:1.2.0+1.8.9` **and** `legacy-fabric-lifecycle-events-v1-common:1.2.0` |

**No screen-events module exists for Legacy Fabric at any version** [PRACTICE]. None is needed: ash's screen is its own `Screen` subclass and receives its own input.

**On both targets a key binding receives no presses while a screen is open.** So the settings key can open the screen, but the screen has to recognise the same key itself in order to close on it (§B.4).

---

## A. Loading ash's jars and a player's mods separately

### A.0 What ash does today

**[PRACTICE]**, read from `launcher/core`:

- **Preparing** downloads the bundled mods into the depot like any library (`depot.rs:444`). It then copies each one into `<instance>/minecraft/mods` (`lib.rs:383`, `instance::install_bundled_mod`), then copies ash's client in last (`lib.rs:396`, `instance::install_client`). The comment at `lib.rs:379` states the premise: "the loader only ever looks in the instance's own mods directory".
- **`install_bundled_mod` deletes** (`instance.rs:276-298`). For each bundled mod, it removes every file in `mods` that starts with `<artifact>-`, ends in `.jar` and is not the pinned file name (`instance.rs:285-291`). It then `fs::copy`s the pinned jar in, overwriting any file of that name (`instance.rs:295`).
  - On 1.21.11 the artifact is `fabric-api`, so any `fabric-api-*.jar` a player put there is deleted.
  - On 1.8.9 the aggregator's artifact is `legacy-fabric-api`. Every `legacy-fabric-api-*.jar` is therefore deleted, and the next loop iterations re-copy ash's own `legacy-fabric-api-base-common-1.2.2.jar`. The Legacy Fabric API a 1.8.9 player downloads from Modrinth today is named `legacy-fabric-api-1.20.1.jar`, so preparing deletes it (§A.4).
- **`install_client` overwrites** `ash-client-<target>.jar` and deletes nothing else (`instance.rs:314-328`).
- **Nothing else in `mods` is ever deleted.** `delete` removes the whole instance directory, which is its job (`instance.rs:375-381`).

So Phase 2 already breaks user story 50 ("ash never deletes, moves or renames a mod I put there") for the two artifact prefixes it manages. No player can have been hurt yet, because nothing tells a player to put mods there.

### A.1 The launch ash builds

**[PRACTICE]**:
- Both targets start through `net.fabricmc.loader.impl.launch.knot.KnotClient`. It is the `mainClass.client` of each pinned loader document, and `synthesise_profile` copies it into the profile (`loader.rs:770-800`).
- Neither launch sets `fabric.development`, so it is a **production** launch.
- The game directory reaches the loader as `--gameDir ${game_directory}`, from Mojang's own arguments on both eras. The JVM's working directory is the same directory (`launch.rs:93`).

### A.2 `fabric.addMods`

**Name.** The system property is `fabric.addMods` [SOURCE: [`SystemProperties.java#L52-L53`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/util/SystemProperties.java#L52-L53)]. The same key is also accepted as a game argument, `--fabric.addMods <list>`. The loader removes that argument from the list before the game sees it, and reads the system property first [SOURCE: [`ArgumentModCandidateFinder.java#L46-L52`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/discovery/ArgumentModCandidateFinder.java#L46-L52)].

**Syntax** [SOURCE: [`ArgumentModCandidateFinder.java#L54-L138`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/discovery/ArgumentModCandidateFinder.java#L54-L138)]:
- **Separator.** Entries are split on `File.pathSeparator`, which is `;` on Windows and `:` elsewhere. Empty entries are skipped.
- **`@listfile`.** An entry starting with `@` names a list file. Each line is trimmed, and each non-blank line is treated as one path.
  - A line is handed straight to the single-path handler, so a list file cannot name another list file.
  - A missing list file is a `WARN` and is skipped.
- **Each path** is one of three things:
  - a **jar file**, which must end `.jar`, not be hidden and not start with `.`;
  - a **directory containing `fabric.mod.json`**, taken as an unpacked mod;
  - **any other directory**, which is **walked recursively with no depth limit** for jars. Hidden subdirectories are skipped, and the non-jar files are listed in a `WARN`.
- **Relative paths** are resolved by `Paths.get`, which means against the JVM's working directory.

The Loader 0.12 release notes describe the same four forms, including `-Dfabric.addMods=@/path/to/extraMods.txt` [DOCS: [Fabric Loader 0.12](https://fabricmc.net/2021/10/03/loader-0120.html)].

**A missing path is a warning, not an error** [SOURCE: `#L88-L89`]. Observed with a list file naming a jar that does not exist **[PRACTICE]**:

```
[main/WARN]: Skipping missing system property file ...\ash-mods.txt provided mod path ...\jars\does-not-exist.jar
[main/INFO]: Loading 49 mods:
```

The game carried on. If ash's client jar is missing from the installation, the loader starts the game without it, so the launcher has to check the paths itself before it launches. The same run confirmed the recursive walk: a directory entry found `ash-client-1.21.11.jar` two levels down.

**Order and deduplication.**
- The loader registers three finders in a fixed order: the classpath, then the mods folder, then `addMods` [SOURCE: [`FabricLoaderImpl.java#L216-L219`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/FabricLoaderImpl.java#L216-L219)].
- Discovery suppresses a second sighting of the **same normalised path** and nothing else [SOURCE: [`ModDiscoverer.java#L91-L103`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/discovery/ModDiscoverer.java#L91-L103)].
- The candidates are then gathered into an identity set, which does not keep the finder order (`#L190-L217`). **Where a mod was found gives it no precedence.** What happens to two jars with one mod id is decided later, by version (§A.4).

**Development versus production.**
- `remapRegularMods` is `isDevelopmentEnvironment()`, and the mods-folder finder and the `addMods` finder both receive it [SOURCE: `FabricLoaderImpl.java#L210`, `#L218-L219`]. So the two are treated identically in either mode.
  - In production nothing is remapped, and a jar must already be in intermediary names. ash's `build/libs` jars are; the `build/devlibs` ones are not.
  - In development, both are remapped at runtime when Loom supplies `fabric.remapClasspathFile` (`#L246-L256`).
- The classpath is **not** an alternative in production. The classpath finder adds only the loader itself unless `isDevelopment()` [SOURCE: [`ClasspathModCandidateFinder.java#L44-L78`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/discovery/ClasspathModCandidateFinder.java#L44-L78)].

**Under ash's production launch it works.** Run on 1.21.11 with `-Dfabric.addMods=<depot>\…\fabric-api-0.141.6+1.21.11.jar;…\ash-client-1.21.11.jar` and nothing of ash's in `mods`, the mod list reads `ash 0.1.0` and `fabric-api 0.141.6+1.21.11` **[PRACTICE]**. §A.5 shows the same on 1.8.9.

### A.3 Moving or disabling the mods folder

**`fabric.modsFolder` exists at both tags** [SOURCE: [`SystemProperties.java#L50-L51`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/util/SystemProperties.java#L50-L51)]. Fabric's documentation names it: "Mods are loaded both from the classpath and from the `mods` directory. This directory can be changed with the `fabric.modsFolder` system property." [DOCS: [docs.fabricmc.net, Fabric Loader](https://docs.fabricmc.net/develop/loader/)].

**The code that decides where the mods folder is:**

```java
// FabricLoaderImpl.java, 0.19.3 and 0.19.5, L623-L627
protected Path getModsDirectory0() {
    String directory = System.getProperty(SystemProperties.MODS_FOLDER);
    return directory != null ? Paths.get(directory) : gameDir.resolve("mods");
}
```

[SOURCE: [`FabricLoaderImpl.java#L623-L627`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/FabricLoaderImpl.java#L623-L627)]

`gameDir` is the game provider's launch directory, set in `setGameProvider` (`#L130-L139`). The Minecraft provider reads it from `--gameDir`, defaulting to `.` [SOURCE: [`MinecraftGameProvider.java#L310-L312`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/minecraft/src/main/java/net/fabricmc/loader/impl/game/minecraft/MinecraftGameProvider.java#L310-L312) at 0.19.3, [`#L313-L315`](https://github.com/FabricMC/fabric-loader/blob/0.19.5/minecraft/src/main/java/net/fabricmc/loader/impl/game/minecraft/MinecraftGameProvider.java#L313-L315) at 0.19.5]. The only accessor a mod has for the mods folder is the deprecated `net.fabricmc.loader.FabricLoader.getModsDirectory()`, which returns the same value.

**There is no off switch.** The mods-folder finder is registered unconditionally (`FabricLoaderImpl.java#L218`), and it runs as follows [SOURCE: [`DirectoryModCandidateFinder.java#L41-L94`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/discovery/DirectoryModCandidateFinder.java#L41-L94)]:
- If the path does not exist, it is created with `Files.createDirectory`, so **its parent must exist**, or the launch fails with "Could not create directory". Nothing is then loaded from it.
- If the path exists but is not a directory, the launch fails with "… is not a directory!".
- Otherwise it is scanned **one level deep**. It takes regular files ending `.jar` that are not hidden and do not start with `.`.

**So "off" is a redirection to a directory with no jars in it.** Observed on 1.21.11 **[PRACTICE]**:
- Set-up: `fabric-api-0.141.4+1.21.11.jar` was placed in `mods` as the player's jar. `-Dfabric.modsFolder` pointed at a directory that did not exist, under one that did. ash's jars came through `addMods`.
- The loader created the empty directory.
- The mod list showed `fabric-api 0.141.6+1.21.11`, ash's copy. The player's 0.141.4 was not loaded.
- `mods` still held the player's file afterwards, untouched.

**How to redirect it.**
- **Where.** Use a directory **under ash's own data root, not inside the instance**. A player who never sees it will never fill it.
- **Path.** Pass it as an **absolute path**; a relative one resolves against the working directory.
- **Before launch.** Create its parent, and check that it holds no `.jar`. That is a reality check, in the sense of ash's no-dead-end-errors rule, not a nicety.

**Alternatives considered and rejected:**
- **Pointing `--gameDir` somewhere else.** The config directory is `gameDir/config` (`FabricLoaderImpl.java#L138`), and saves, `options.txt`, logs, screenshots and the `.fabric` cache all follow it. Every setting ash's client reads moves with it.
- **Moving the folder aside for the session.** It is a rename of the player's directory, which story 50 forbids. A crash or a killed launcher would also leave it renamed.
- **`fabric.debug.disableModIds`.** It is a debug property "mostly useful for unit testing" (`SystemProperties.java#L54-L55`). It works by mod id, so ash would have to open every player jar to learn the ids.
- **Passing the player's `mods` through `addMods` instead.** `addMods` walks directories recursively, and the mods folder is read one level deep. A player's `mods/disabled/` subfolder would start loading.

### A.4 The same mod present twice

**What the loader does** [SOURCE, identical at both tags]:
1. It sorts candidates with root mods first, then by id, then **by version, highest first** [[`ModPrioSorter.java#L151-L176`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/discovery/ModPrioSorter.java#L151-L176)]. A jar in `mods` and a jar from `addMods` are both root mods. Two roots with the same id and version compare equal (`#L176`).
2. For an id with several root candidates, the solver adds two constraints, "at least one of them loads" and "at most one loads" [[`ModSolver.java#L845-L893`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/discovery/ModSolver.java#L845-L893)]. It weights the choice towards the earlier, newer candidate (`#L816-L829`).
3. The warnings pass reports only unmet `recommends` and matched `conflicts` [[`ResultAnalyzer.java#L222-L260`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/discovery/ResultAnalyzer.java#L222-L260)]. **Nothing is logged about the copy that was dropped.**
4. The only hard duplicate error is a mod that shares an id with a built-in one: `minecraft`, `java` or `fabricloader` [[`ModResolver.java#L84-L106`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/impl/discovery/ModResolver.java#L84-L106)].

Fabric's own words match:
- "Fabric Loader 0.12.0 will no longer refuse multiple versions of the same mod … It will select the latest compatible version if more than one option is present" [DOCS: [Fabric Loader 0.12](https://fabricmc.net/2021/10/03/loader-0120.html)].
- "Only one version of a mod may be loaded at a time" [DOCS: [docs.fabricmc.net](https://docs.fabricmc.net/develop/loader/)].

0.19.5's `ModResolverTest` has the case as `testDuplicateRootModsOfDiffVers` (2.0.0 is loaded over 1.0.0) and `testDuplicateRootModsOfDiffVersLowestCompat` [SOURCE: [`ModResolverTest.java#L139-L171`](https://github.com/FabricMC/fabric-loader/blob/0.19.5/src/test/java/net/fabricmc/loader/impl/discovery/ModResolverTest.java#L139-L171)]. Its `testDuplicateRootMods`, which does throw, passes the **same candidate object** twice. That is not what two jars produce.

**Observed** **[PRACTICE]**. Third-party mods were on (no `fabric.modsFolder`), and ash's jars came through `addMods`:

| Run | In `mods` | Through `addMods` | Loaded | Log |
| --- | --- | --- | --- | --- |
| 1.21.11, older copy | `fabric-api-0.141.4` | `fabric-api-0.141.6`, ash | `fabric-api 0.141.6` | no warning |
| 1.21.11, newer copy in `mods` | `fabric-api-0.141.6` | `fabric-api-0.141.4`, ash | `fabric-api 0.141.6`, **from `mods`** | no warning |
| 1.21.11, same version twice | `fabric-api-0.141.6`, `ash-client-1.21.11.jar` | the same two | one of each | no warning |
| 1.8.9, same version twice | `legacy-fabric-rendering-api-v1-common-1.0.1.jar` | ash's five Legacy Fabric jars, ash | one copy | no warning |
| 1.8.9, Modrinth's Legacy Fabric API | `legacy-fabric-api-1.20.1.jar` | ash's five, ash | `legacy-fabric-api 1.13.5+1.8.9`, **ash's** | no warning |

The last row is the version-conflict case, and it is the one to design around.
- Modrinth's `legacy-fabric-api-1.20.1.jar` shares the mod id `legacy-fabric-api` with ash's 1.13.5 aggregator. It depends on `osl`, which was not installed.
- The loader cannot load the newer copy, so it quietly takes ash's older one. **The player's jar is dropped with no message at all.**
- If no copy can satisfy the constraints, the launch fails with "Some of your mods are incompatible with the game or each other!" (`ModResolver.java#L147-L148`).

**What that means for Phase 3:**
- **A player can replace ash's pinned API without knowing.** ash's 1.21.11 client depends on `"fabric-api": "*"`, so a newer Fabric API in `mods` wins and ash then runs against a version it was not tested on. The 1.8.9 client's `depends` are also `*`.
  - To tell which copy loaded, the client can compare `FabricLoader.getModContainer(id).getOrigin().getPaths()` with ash's own paths [SOURCE: [`ModContainer.java#L83`](https://github.com/FabricMC/fabric-loader/blob/0.19.3/src/main/java/net/fabricmc/loader/api/ModContainer.java#L83)].
  - That is the evidence the load report needs to say "one of your mods may be the cause".
- **A player's copy of the same version is harmless on 1.21.11.** Modrinth serves Fabric API 0.141.6+1.21.11 as `fabric-api-0.141.6+1.21.11.jar`, SHA-1 `c98467cbbaf4d197377266795ae015f4130d65b6`: ash's pin, byte for byte **[PRACTICE: `api.modrinth.com/v2/project/fabric-api/version`]**.
- **The comment in `instance.rs:268` and `:313`, and the ones in `tests/fabric.rs:299` and `:431`, are wrong for 0.19.x.** Two files claiming one id do not stop the loader; one of them is ignored. The tests they sit beside may still be right to want one file, but not for that reason.

### A.5 Legacy Fabric's launch of the same loader

**[PRACTICE]**, from ash's code and from running it:
- **Same loader and entry point.** Legacy Fabric runs the upstream loader jar, whose `KnotClient` and `MinecraftGameProvider` handle 1.8.9 too. The `--gameDir` it reads comes from vanilla 1.8.9's `minecraftArguments`.
- **The JVM-argument trap.**
  - Legacy Fabric's meta service emits no JVM arguments, so the pin's `jvm_arguments` is `&[]` (`loader.rs:459`).
  - `profile::merge` concatenates the parent's and the child's JVM lists (`profile.rs:67`). `launch::jvm_entries` only supplies `-Djava.library.path=${natives_directory} -cp ${classpath}` **when that merged list is empty** (`launch.rs:209-218`).
  - A `-Dfabric.addMods` added to the 1.8.9 pin would make the list non-empty, and the launch would lose its classpath. Put both properties where `-Xmx` goes (`launch.rs:65`), which is per-instance anyway.
- **Runs on Java 8.** The 1.8.9 runs used the depot's `jre-legacy` (1.8.0_51):
  - With `fabric.modsFolder` redirected, and `legacy-fabric-lifecycle-events-v1-common-1.2.0.jar` in `mods` as the player's jar, the mod list held ash and its five Legacy Fabric jars and nothing else. The redirected folder was created, and the player's file stayed where it was.
  - Without the redirection, the same player's jar loaded alongside ash's.

**Legacy Fabric API's distribution has split, which matters to a 1.8.9 player adding mods** **[PRACTICE]**:
- **The Maven line ash pins has stopped.** `repo.legacyfabric.net`'s per-version line ends at `legacy-fabric-api` 1.13.5+1.8.9 (`maven-metadata.xml`, `lastUpdated` 2026-05-31). Its module lines end at exactly the versions ash pins: rendering 1.0.1, keybindings 1.2.0, lifecycle 1.2.0.
- **Modrinth carries newer, universal releases.** It carries 1.14 to **1.20.1** (published 2026-09-09), as one jar for 1.3 to 1.12.2 that depends on Ornithe Standard Libraries (`osl`).
- **Its nested modules differ from ash's.**
  - Its nested `legacy-fabric-rendering-api-v1` is 1.1.1+1.8.9, newer than ash's 1.0.1+1.8.9.
  - Its 1.8.9 keybinding module is `legacy-fabric-keybinding-api-v1` 1.4.0+1.8.9, a different id from the `legacy-fabric-keybinding-api-v1-common` ash depends on. Its `-common` id is now built for 1.3 to 1.6.4 only.
- **What that means with third-party mods on.**
  - A player who installs it **with** OSL gets newer rendering classes under ash's client, and a second key-binding API beside ash's. **I did not test that combination.**
  - **Without** OSL, their copy is silently dropped (§A.4).

### A.6 Instances prepared in Phase 2

The spec says preparing removes "only the files ash itself put there, by name". For a bundled mod, the safe rule is **exact file name and the pin's SHA-1**:
- If the bytes match, the file is ash's artifact, whoever downloaded it.
- If they do not, it is not ash's to touch.

The names:

| Target | Remove if name and hash match the pin | Remove by exact name |
| --- | --- | --- |
| 1.21.11 | `fabric-api-0.141.6+1.21.11.jar` | `ash-client-1.21.11.jar` |
| 1.8.9 | `legacy-fabric-api-1.13.5+1.8.9.jar`, `legacy-fabric-api-base-common-1.2.2.jar`, `legacy-fabric-rendering-api-v1-1.0.1+1.8.9.jar`, `legacy-fabric-rendering-api-v1-common-1.0.1.jar`, `legacy-fabric-keybindings-api-v1-common-1.2.0.jar` | `ash-client-1.8.9.jar` |

The client jar has no pinned hash, because it ships inside the installer, so its fixed name is all there is to match on. The prefix rule in `install_bundled_mod` should go entirely once ash's jars no longer live in `mods`.

---

## B. Settings-screen building blocks per target

### B.1 1.21.11, Mojang's names

All **[PRACTICE]**, from the mapped jar.

**Screen.**
- `net.minecraft.client.gui.screens.Screen` is abstract, with `protected Screen(Component title)`.
- It exposes `protected void init()`, `public void render(GuiGraphics, int, int, float)`, `public void onClose()`, `public void removed()`, `public boolean isPauseScreen()` and `public boolean keyPressed(KeyEvent)`.
- Widgets are added with `protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T)`.
- Fields: `minecraft`, `font`, `width`, `height`.

**Opening and closing.**
- `Minecraft.setScreen(Screen)` opens a screen.
- `Screen.onClose()` is `minecraft.setScreen(null)`.
- `Minecraft.screen` is the open screen.

**Input.** Since 1.21.9 the handlers take records, not loose numbers:
- `mouseClicked(MouseButtonEvent, boolean doubleClick)`
- `mouseReleased(MouseButtonEvent)`
- `mouseDragged(MouseButtonEvent, double dragX, double dragY)`
- `mouseScrolled(double, double, double, double)`
- `keyPressed(KeyEvent)`

`MouseButtonEvent` is `record(double x, double y, MouseButtonInfo buttonInfo)`, with `button()` and `modifiers()`.

`MouseHandler.handleAccumulatedMovement` calls `Screen.mouseDragged` whenever a button is held (`activeButton != null`) and the pointer moves. The coordinates are GUI-scaled. So a screen that moves HUD readouts overrides these three mouse methods and needs nothing else.

**Drawing.**
- `GuiGraphics.fill(int x1, int y1, int x2, int y2, int argb)`
- `drawString(Font, String, int, int, int)` and `drawString(Font, String, int, int, int, boolean shadow)`
- `drawCenteredString(Font, String|Component, int, int, int)`
- `renderOutline(int x, int y, int w, int h, int argb)`
- `guiWidth()` and `guiHeight()`

`drawString` returns without drawing **when the colour's alpha byte is 0** (`ARGB.alpha(color)`, then `ifne`, then `return`).

**Widgets:**
- **`Button`** is **abstract**; its constructor is `protected`.
  - Build one with `Button.builder(Component, Button.OnPress)` and `.bounds(x, y, w, h)` / `.pos` / `.size` / `.tooltip`, then `.build()`, which returns a `Button$Plain`.
  - `Button.OnPress` is `void onPress(Button)`.
- **`Checkbox`** has a package-private constructor.
  - Use `Checkbox.builder(Component, Font)`, with `.pos(int, int)`, `.selected(boolean)` and `.onValueChange(Checkbox.OnValueChange)`, then `.build()`.
  - `Checkbox.OnValueChange` is `void onValueChange(Checkbox, boolean)`.
- **`CycleButton<T>`** has a package-private constructor.
  - `CycleButton.onOffBuilder(boolean)` is the game's own on/off button.
  - For a fixed set, use `CycleButton.builder(Function<T, Component>, T)` or `builder(Function<T, Component>, Supplier<T>)`, then `.withValues(T...)` or `.withValues(Collection<T>)`, then `.create(int x, int y, int w, int h, Component, CycleButton.OnValueChange<T>)`.
  - Two behaviours read from the bytecode matter for "put back to defaults":
    - The builder's second argument is **both** the initial value and what `resetValue()` restores; `create` and `resetValue` both call the same supplier.
    - `setValue(T)` does **not** fire `OnValueChange`; only a click's `cycleValue` does.
  - So: build with the declared default, then `setValue` the current value.
- **`AbstractSliderButton`** has a `public` constructor, `AbstractSliderButton(int x, int y, int w, int h, Component, double value)`.
  - `value` is a `protected double` in 0 to 1.
  - Subclasses implement `protected abstract void updateMessage()` and `protected abstract void applyValue()`, and may call `protected void setValue(double)`.
  - A whole-number slider maps 0 to 1 onto `[min, max]` and rounds. That is the vanilla pattern.
  - `OptionInstance` with `OptionInstance.IntRange(int, int)` and `createButton(Options, int, int, int, Consumer<T>)` is vanilla's ready-made integer slider. It ties the option to the game's option machinery and a translation key, so it is a poorer fit.
- **`EditBox`** has constructors `EditBox(Font, int x, int y, int w, int h, Component)` and `EditBox(Font, int w, int h, Component)`.
  - Methods: `setValue(String)`, `getValue()`, `setResponder(Consumer<String>)`, `setFilter(Predicate<String>)`, `setMaxLength(int)` and `setHint(Component)`.
  - For a hex colour, filter to `[0-9a-fA-F]{0,8}`.
- **`ContainerObjectSelectionList<E extends ContainerObjectSelectionList.Entry<E>>`** is abstract, with `(Minecraft, int width, int height, int y, int itemHeight)`; the order was read from `AbstractSelectionList.<init>`.
  - Rows are added with `protected int addEntry(E)`.
  - An `Entry<E>` implements `renderContent(GuiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick)`, `children()` and `narratables()`. It positions its widgets from `getContentX()`, `getContentY()` and `getContentWidth()`.
  - Entries are container event handlers, so a slider inside a row receives its drag. Vanilla's own options lists do exactly this.
- **`OptionsList`** has `(Minecraft, int width, OptionsSubScreen)` and `addSmall(AbstractWidget, AbstractWidget)` / `addBig(OptionInstance)` / `addHeader(Component)`.
  - It requires an `OptionsSubScreen` parent. `OptionsSubScreen.removed()` calls `Options.save()`, which rewrites the game's own `options.txt` every time ash's screen closes.
  - Use `ContainerObjectSelectionList` instead.

**Key binding and tick.**
- The binding: `new KeyMapping(String, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT /* 344 */, KeyMapping.Category.MISC)`, registered through `KeyBindingHelper.registerKeyBinding`. `KeyMapping.Category.register(Identifier)` makes an ash category if wanted.
- The tick: `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK`, whose listener is `onEndTick(Minecraft)`. The module is **`fabric-lifecycle-events-v1` 2.6.15+4ebb5c083e**, nested in Fabric API 0.141.6+1.21.11. Poll with `while (key.consumeClick())`.
- **Nothing new to pin.** Fabric API ships whole, and `fabric-screen-api-v1` 3.1.7 is also there if ever wanted.

### B.2 1.8.9, Legacy Yarn 604

All **[PRACTICE]**, from the mapped jar.

**Screen.**
- `net.minecraft.client.gui.screen.Screen` is abstract, with `public Screen()`.
- `public void init()` adds to the `protected List<ButtonWidget> buttons`.
- `public void render(int mouseX, int mouseY, float tickDelta)` draws.
- `protected void buttonClicked(ButtonWidget)` receives clicks on those buttons.
- Other members: `protected void keyPressed(char, int keyCode)`, `public void tick()`, `public void removed()`, `public boolean shouldPauseGame()`, `public void renderBackground()`, `public void handleMouse()`.
- Fields: `client`, `textRenderer`, `width`, `height`.

**Opening and closing.**
- `MinecraftClient.getInstance().setScreen(Screen)` opens a screen. `MinecraftClient.currentScreen` is the open one.
- `setScreen(null)` calls the old screen's `removed()` and then `closeScreen()`, which gives the mouse back to the game.
- `Screen.keyPressed` already does `setScreen(null)` on Escape (key code 1).

**Input.** Per LWJGL event, `Screen.handleMouse` works like this:
- A press calls `mouseClicked(int x, int y, int button)`.
- A release calls `mouseReleased(int x, int y, int button)`.
- Movement with a button held calls `mouseDragged(int x, int y, int button, long msSinceClick)`.

The coordinates are already GUI-scaled. A HUD-layout screen overrides these three.

**A naming trap, like the ones in 0003.**
- `ButtonWidget.isMouseOver(MinecraftClient, int, int)` **is the press handler**, not a hover test. `Screen.mouseClicked` calls it for each button, and on `true` plays the click and calls `buttonClicked`. `SwitchWidget` and `SliderWidget` override it to change their value.
- The hover test is `isHovered()`.

**Drawing.**
- `DrawableHelper.fill(int x1, int y1, int x2, int y2, int argb)` is **static**.
- `drawCenteredString(TextRenderer, String, int, int, int)` and `drawWithShadow(TextRenderer, String, int, int, int)` are instance methods, and `Screen` inherits them.
- `TextRenderer.drawWithShadow(String, float, float, int)`, `draw(String, int, int, int)`, `getStringWidth(String)` and `fontHeight` are also available.

**Text alpha differs from 1.21.11.** The 1.8.9 text path does `if ((colour & 0xFC000000) == 0) colour |= 0xFF000000`. So a text colour with alpha 0 to 3 draws **opaque** on 1.8.9, and at alpha 0 **not at all** on 1.21.11. The shared module should decide what a near-zero opacity means, and not leave it to each target.

**Widgets:**
- **`ButtonWidget`** has `(int id, int x, int y, String message)`, 200 by 20, and `(int id, int x, int y, int w, int h, String message)`.
  - `message`, `active`, `visible`, `x` and `y` are public fields, with `setWidth(int)`.
  - The on/off switch and the cycle button are both a `ButtonWidget` whose `message` ash rewrites in `buttonClicked`. That is how vanilla's own options do it: `GameOptions.getValueMessage` uses `options.on`/`options.off` through `I18n.translate(String, Object...)`.
  - **1.8.9 has no cycle-button class.** `OptionButtonWidget` exists but is bound to the `GameOptions.Option` enum.
- **`SwitchWidget`** is `(PagedEntryListWidget.Listener, int id, int x, int y, String label, boolean)`, 150 by 20.
  - It reads "label: Yes"/"label: No" (`gui.yes`/`gui.no`) and reports through `Listener.setBooleanValue(int id, boolean)`.
  - It is usable, but it does not read like the game's own ON/OFF options.
- **`SliderWidget` exists.** Its signature is `(PagedEntryListWidget.Listener, int id, int x, int y, String label, float min, float max, float initial, SliderWidget.LabelSupplier)`.
  - The constructor fixes it at **150 by 20**; `setWidth(int)` changes that.
  - Values are **float**. It reports `Listener.setFloatValue(int id, float)` while dragging. `setSliderValue(float, boolean notify)` sets a value, and calls the listener only when `notify` is true.
  - The label comes from `LabelSupplier.getLabel(int id, String label, float value)`.
  - A whole-number slider rounds in the listener and snaps with `setSliderValue(Math.round(v), false)`.
  - `OptionSliderWidget` also exists but is bound to `GameOptions.Option`.
- **`TextFieldWidget`** is `(int id, TextRenderer, int x, int y, int w, int h)`.
  - Methods: `setText`, `getText`, `setMaxLength`, `setTextPredicate(com.google.common.base.Predicate<String>)`, `setListener(PagedEntryListWidget.Listener)`, `setFocused` and `render()`.
  - It is not a `ButtonWidget` and is not in `buttons`. The screen forwards `keyPressed(char, int)`, `mouseClicked(int, int, int)` and `tick()` to it, and calls `render()` itself.
- **`EntryListWidget`** is abstract, with `(MinecraftClient, int width, int height, int top, int bottom, int entryHeight)`.
  - It implements `getEntry(int)` and `getEntryCount()`, and needs `public boolean mouseClicked(int, int, int)` / `mouseReleased(int, int, int)` forwarded from the screen.
  - `handleMouse()` must be called from the screen's own `handleMouse()`, for the wheel, and `render(int, int, float)` from the screen's `render`.
  - Rows implement the interface `EntryListWidget.Entry`: `render(int index, int x, int y, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean hovered)`, `mouseClicked(int index, int mouseX, int mouseY, int button, int relX, int relY)`, `mouseReleased(...)` and `updatePosition(int, int, int)`.
  - **Prior art:** `net.minecraft.client.gui.screen.options.ControlsListWidget`. Its `KeyBindingEntry` holds two `ButtonWidget`s and forwards clicks by calling `isMouseOver` on them itself.
  - A slider in a row still drags, because `ButtonWidget.render` calls `mouseDragged(MinecraftClient, int, int)` every frame. `EntryListWidget.mouseReleased` forwards the release to every entry, which clears the slider's `focused` flag.
- **`PagedEntryListWidget`** is the Customize World screen's list. It builds sliders, switches, text fields and labels from `PagedEntryListWidget.ListEntry` descriptors, two to a row, in pages. It is the only ready-made list of setting widgets on 1.8.9, but its two-column, paged layout is not 1.21.11's, and the spec wants the same layout on both.

**Key binding and tick.**
- The binding: `new KeyBinding(String, Keyboard.KEY_RSHIFT /* 54 */, String category)`, through Legacy Fabric's `KeyBindingHelper`, which is already shipped.
- **Legacy Fabric API does have a client tick event, but not where one would look.**
  - `net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents`, with `START_CLIENT_TICK`/`END_CLIENT_TICK` and `onStartTick`/`onEndTick(MinecraftClient)`, is in **`legacy-fabric-lifecycle-events-v1-common-1.8.9`** 1.2.0+1.8.9.
  - Its mod id is `legacy-fabric-lifecycle-events-v1-common-versioned`.
  - It is fired by that jar's `MinecraftClientMixin`, which injects at `HEAD` and `RETURN` of `class_1600.method_2954`. Legacy Yarn 604's `mappings.tiny` names that `MinecraftClient.tick()`.
  - That jar cannot ship alone: its `common.MinecraftServerMixin` reads `ServerTickEvents.START_SERVER_TICK`, which lives in **`legacy-fabric-lifecycle-events-v1-common`** 1.2.0.
  - The third lifecycle artifact, `legacy-fabric-lifecycle-events-v1` 1.2.0+1.8.9, holds only two server mixins. Neither of the other two references it.
- **The alternative is ash's own mixin**, `@Mixin(MinecraftClient.class)` with `@Inject(method = "tick", at = @At("RETURN"))`.
  - 1.8.9's keyboard loop runs inside `tick()`, so at `RETURN` this tick's presses have been counted and `KeyBinding.wasPressed()` can consume them.
  - Do not reuse toggle sprint's `ClientPlayerEntity.tickMovement` mixin. That would make the settings key depend on another feature's mixin, which ADR-0017's "each fails alone" rules out.
  - The HUD render callback is no substitute either: it stops when the HUD is hidden.

### B.3 Which modules ash would add

**1.21.11: none.** The tick event, key bindings and screen events are all inside the Fabric API jar ash already pins and ships.

**1.8.9: none, if ash takes the mixin. That is the recommendation.** The reason is failure behaviour:
- Legacy Fabric's lifecycle mixin configs are `"required": true` with `"injectors": {"defaultRequire": 1}`. A lifecycle mixin that fails to apply stops the game.
- ash's own `ash.mixins.json` is `"required": false`, `"defaultRequire": 0`. It goes through `AshMixinPlugin`, so a failed injection degrades one feature, as ADR-0017 wants.
- The lifecycle modules would also add eight required mixins to ash's 1.8.9 surface. `-common` has five, including three on the server and chunk classes; `-common-1.8.9` has three, one of them a `@WrapOperation` in `MinecraftServer.run`. That is a lot of surface for one poll of one key.

**If ash takes the event instead, it pins, mirrors and ships two artifacts**, as `docs/mirror.md`'s "Adding an artifact" describes. Each hash was read from its `.sha1` sidecar on `repo.legacyfabric.net` and matched a local hash **[PRACTICE]**:

| Coordinate | Mod id | SHA-1 | Size |
| --- | --- | --- | --- |
| `net.legacyfabric.legacy-fabric-api:legacy-fabric-lifecycle-events-v1-common-1.8.9:1.2.0+1.8.9` | `legacy-fabric-lifecycle-events-v1-common-versioned` | `6491cbcf9f65bfb054606cfac23af4cab134bcf3` | 20,362 |
| `net.legacyfabric.legacy-fabric-api:legacy-fabric-lifecycle-events-v1-common:1.2.0` | `legacy-fabric-lifecycle-events-v1-common` | `97147156204f9de7ec634050cc8c1738317c9d2b` | 37,928 |

Both are the versions the 1.13.5+1.8.9 aggregator's POM names. Between them they reference only each other's classes and `api/event`, which is in `legacy-fabric-api-base-common` and already shipped. Nothing else is transitively missing.
- As with keybindings, **artifact and mod id disagree**. A `depends` names the mod id: `…-common-versioned`, not `…-common-1.8.9`.
- The mirror's `+`→`-` rule makes the first asset `legacy-fabric-lifecycle-events-v1-common-1.8.9-1.2.0-1.8.9.jar`.

**No other module is needed on 1.8.9.**
- **No screen module is needed**, and none exists. The module list at `repo.legacyfabric.net/legacyfabric/net/legacyfabric/legacy-fabric-api/` has no screen, GUI or input module **[PRACTICE]**.
- **The widgets are vanilla.**
- **The resource loader** (`legacy-fabric-resource-loader-v1`) would be needed only for translation keys. Toggle sprint avoided it by using display text directly (0003 §6), and the settings screen can do the same with strings from the shared module.

### B.4 The key, on both targets

- **Right Shift is unbound by default on both.**
  - The key codes are `GLFW_KEY_RIGHT_SHIFT` = 344 on 1.21.11 and `Keyboard.KEY_RSHIFT` = 54 on 1.8.9.
  - Neither `Options.<init>` (1.21.11) nor `GameOptions.<init>` (1.8.9) pushes that constant. The same search finds sneak's Left Shift once in each, so it is not a vacuous zero **[PRACTICE]**.
  - Both real-game tests already check for default collisions at runtime.
- **A key binding sees no press while a screen is open.**
  - **1.8.9:** `MinecraftClient.tick()` feeds bindings (`KeyBinding.setKeyPressed`/`onKeyPressed`) only inside `if (currentScreen == null || currentScreen.passEvents)`. Before that, an open screen's `handleInput()` has already drained the LWJGL queue.
  - **1.21.11:** `KeyboardHandler.keyPress` gives the key to `Screen.keyPressed` first and returns if the screen handled it. Key mappings are only clicked with no screen open, or with a menu-less pause screen or the game-mode switcher.
  - So the settings screen must recognise its own key to close on it:
    - 1.21.11: `KeyMapping.matches(KeyEvent)` in `keyPressed`.
    - 1.8.9: `keyCode == binding.getCode()` in `keyPressed(char, int)`.

---

## Confidence and gaps

**High confidence:**
- `fabric.addMods` and `fabric.modsFolder` exist and behave as described, on both pinned tags. Each was read at the tag and matched against the resolved sources jars.
- They work under ash's own production launch on both targets. The real `KnotClient`, ash's depot, ash's built jars and Java 21 and 8 were used, against scratch game directories.
- The duplicate rule (newest compatible wins, silently, source ignored) was run five ways.

**Things that contradict or sharpen the spec:**
- **The mods folder cannot be disabled, only redirected.** "With the setting off, the loader loads nothing from it" is achievable, but as "the loader reads a different, empty directory". That directory's parent must exist, and ash should verify it holds no jars.
- **Duplicates are not a crash, and not a warning either.** Story 55, Sodium alongside ash, is unaffected. But with third-party mods on, a player can **replace ash's pinned Fabric API or Legacy Fabric modules with newer ones without any message**, or have **their own copy silently dropped** (the Modrinth Legacy Fabric API case). The spec's crash and degradation wording assumes a failure is visible. Here it may not be, unless the client reports which copy of each bundled mod loaded.
- **Phase 2's preparation is not as careful as the spec's premise.** It deletes by prefix and overwrites by name inside `mods`, so the cleanup of Phase 2 instances has to be narrower than today's code.
- **1.8.9 has a slider.** It is float-valued, fixed at 150 wide until `setWidth`, and reports through a listener keyed by an integer id.
- **1.8.9 has no cycle button and no checkbox.** A plain `ButtonWidget` with rewritten text is what vanilla itself uses.
- **1.8.9 does have a client tick event**, but it takes two more artifacts, whose mixins crash the game rather than degrade. ash's own mixin at `MinecraftClient.tick()` is lighter and fails alone.
- **Colour opacity renders differently at the extreme.** Near-zero alpha text is opaque on 1.8.9 and invisible on 1.21.11.

**Not established:**
- **Which of two same-version, byte-different copies wins.** The sort compares them equal, and the order before sorting comes from an identity set. For byte-identical copies it does not matter. For a stale `ash-client` jar of the same version (`mod_version` is `0.1.0` across builds) it could. Bumping `mod_version` every release makes the newer one win by rule.
- **Modrinth's Legacy Fabric API 1.20.x with OSL installed, alongside ash's pinned modules.** It was not run. The shared ids and the second key-binding API are an untested risk for 1.8.9 players who opt in.
- **Where Legacy Fabric now publishes its 1.14 to 1.20 modules on Maven, if anywhere.** The coordinates ash pins stop at 1.13.5. A future pin bump needs that answered first.
- **The exact gating condition in 1.21.11's `KeyboardHandler.keyPress`.** It was traced far enough to know a handled key stops at the screen, and that mappings are clicked only with no screen or one of two special screens. The full branch structure was not traced.
- **None of the widget code was compiled or run in a game.** Constructor signatures, parameter orders and call paths come from bytecode. How rows lay out, and how a slider drags inside a list row on 1.8.9, are the first things the settings-screen ticket should prove on screen.
