# ash client

The game-side half of ash: a Fabric mod, built once per version target over a
module that cannot see the game.

| | |
| --- | --- |
| `shared/` | Pure logic and the version seam. Cannot name a Minecraft type or a Fabric API type, and `checkNoGameTypes` fails the build if it does. Java 8 bytecode, because the 1.8.9 module consumes it. |
| `target-1.21.11/` | The 1.21.11 adapter. Loom 1.18, Mojang mappings, Fabric Loader, Fabric API. Produces `ash-client-1.21.11.jar`. |
| `target-1.8.9/` | The 1.8.9 adapter. Loom 1.16 plus `legacy-looming`, Legacy Yarn, Legacy Fabric API. Produces `ash-client-1.8.9.jar`. |

The modules are named for the version target rather than "modern" and
"legacy", because those words rot — 1.21.11 is the *last obfuscated* release
and 26.x is already out.

**Two Looms, one build.** `legacy-looming` is capped at 1.16.1 and has to match
the Loom it companions, while the modern side is on 1.18.2. That costs nothing,
because Loom is modularised: `fabric-loom` and `net.fabricmc.fabric-loom-remap`
are different artifacts, so Gradle has no version to resolve between them and
both sit on one plugin classpath. Each project prints its own at configure
time. A third target on a third Loom would be the same story.

Why three modules rather than a source preprocessor spanning both:
`docs/adr/0015-two-client-projects-over-a-shared-module.md`.

## Building

```
./gradlew build
```

Needs a JDK **25 or newer** on `JAVA_HOME`: Fabric Loom 1.18 is compiled for
Java 25 and will not resolve on anything older.

That is the JDK that *builds*, which is a different thing from the JDK the
game runs on — 1.21.11 wants Java 21 and 1.8.9 wants Java 8, and the launcher
provisions those itself. `--release` in each module decides what bytecode
comes out, so one modern JDK builds both targets.

The first build downloads Minecraft and Mojang's mappings and takes a couple
of minutes; later ones are seconds.

`build` compiles all three modules, runs their tests, and runs
`checkNoGameTypes`. CI runs exactly this on every push.

## The rule, and what enforces it

The shared module may not name `net.minecraft`, `com.mojang`,
`net.fabricmc.fabric.api` or `net.legacyfabric`. Its compile classpath has
none of them, which is the first half; `checkNoGameTypes` in
`shared/build.gradle` is the half that survives someone adding a dependency.

It reads the compiled bytecode rather than the imports. The constant pool
names every type a class actually touches, so a fully qualified name written
inline is caught as well as an import — and because the pool holds string
literals, so is a class named for reflection. It also fails when it finds no
classes at all, because a check with nothing to check proves nothing.

## Testing

The shared module's tests are **plain JUnit**, not Fabric Loader JUnit, and
that is a deliberate departure from what `docs/specs/0002-phase-2-client.md`
assumed.

Fabric Loader JUnit stands Fabric Loader up inside the test JVM, and the
loader's first act is to locate the game. There is no supported way to run it
without one: `fabric.skipMcProvider` disables the Minecraft game provider and
Knot then fails with `No game providers present on the class path!`, because
it requires at least one. So the tier needs a game jar, and a game jar belongs
to exactly one version target — the one thing this module may never have.

That costs nothing here. Fabric ships that tier because mod code relies on
Mixin and on registries that only exist once the loader is up, and the shared
module has neither by construction.

**The tier lives in the target modules instead**, where the game already is.
`target-1.21.11/src/test/java/.../AshModMetadataTest.java` stands the real
loader up and asks it what it made of `fabric.mod.json` — so a mod id that
changed, a version that failed to expand at build time, or a lost Fabric API
dependency fails there rather than in a game that comes up without ash in it.
That is the acceptance criterion "it appears in the game's own mod list",
proved as far as it can be without a game.

**On 1.8.9 that tier does not work either**, and this one is not by choice. The
loader starts, and then its Minecraft game provider cannot classify a 1.8.9 dev
jar: `Minecraft game provider couldn't locate the game!`, with
`fabric.gameJarPath` and `fabric.gameVersion` both pointed straight at it.

That rules out the tier, not testing. `FabricModJsonTest` reads the processed
manifest itself and asserts the two things most worth catching — a `${version}`
that never expanded, and an entrypoint naming a class that has since moved.
Weaker than asking the loader, because it is a second reading rather than the
loader's own; better than nothing, which is what "that tier does not work"
would otherwise buy.

**The rest is a real game, and #22 automated it.** On 1.21.11 that is Fabric's
own client game tests (`src/gametest`), which launch a vanilla client, wait
twenty ticks, assert ash is loaded and keep a screenshot. On 1.8.9 no such
framework exists - none of Legacy Fabric API's 44 modules is a gametest module
- so `src/smoketest` is ash's miniature of it: a vanilla client, launched the
same way, that prints its mod list, says whether ash is in it and shuts itself
down. Neither test mod declares a dependency on `ash`, deliberately: with one,
the loader would refuse to start when ash was missing and the assertion would
never be the thing that noticed.

Both run in CI on Linux only, and that is a capability rather than a
preference - see `docs/adr/0016-ci-accepts-the-minecraft-eula.md`, which also
records what they cost and what a headless runner does and does not provide.

## How the jar reaches a player

Each target module builds `ash-client-<version target>.jar` — a fixed name with
no version in it. The launcher looks it up by that name to place it in an
instance's `mods` directory, and the two ship in one installer, so a version in
the file name would be a second place to change on every release with nothing
to catch getting it wrong. Which version is running is a question
`fabric.mod.json` answers, in the game's own mod list.

The installer picks the jar up through `launcher/src-tauri/tauri.bundle.conf.json`:

```
cd client && ./gradlew build
cd ../launcher && npm run build:installer
```

That config is separate from `tauri.conf.json` on purpose. `tauri-build`
checks resource paths in its build script, so declaring the jar in the base
config would make `cargo build` itself fail without it — putting a Java
toolchain and a Minecraft download in front of `cargo test`. The guarantee
belongs to bundling, so it lives in the config that bundles, and
`npm run build:installer` fails loudly if the client has not been built.

For a manual launch from the repo, `cargo run -p ash-core --example
real-launch -- <client-id> 1.21.11 fabric` reads the jar straight out of
`target-1.21.11/build/libs`, so `./gradlew build` first is all it takes.

## What has to stay in step

`gradle.properties` names the Minecraft version, the Fabric Loader version and
the API version, per target. Every one is also pinned by the launcher in
`launcher/core/src/loader.rs`, and they have to agree: the launcher installs
the loader and the API that the client then declares it depends on. Moving one
means moving both.

On 1.8.9 that goes one level further. Legacy Fabric API is 44 separately
versioned modules behind a metadata-only aggregator, every one of their POMs is
empty, and the launcher ships only the modules ash uses — so
`target-1.8.9/build.gradle` compiles against exactly the list `loader.rs` pins.
Reaching for a class from a module ash does not ship fails the build rather
than failing in a player's game. Adding one means adding it in both places and
mirroring it; `launcher/core/src/loader.rs` has a test for the mirroring half.
