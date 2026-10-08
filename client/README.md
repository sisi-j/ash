# ash client

The game-side half of ash: a Fabric mod, built once per version target over a
module that cannot see the game.

| | |
| --- | --- |
| `shared/` | Pure logic and the version seam. Cannot name a Minecraft type or a Fabric API type, and `checkNoGameTypes` fails the build if it does. Java 8 bytecode, because the 1.8.9 module consumes it. |
| `target-1.21.11/` | The 1.21.11 adapter. Loom 1.18, Mojang mappings, Fabric Loader, Fabric API. Produces `ash-client-1.21.11.jar`. |
| `target-1.8.9/` | The 1.8.9 adapter. Loom 1.16 plus `legacy-looming`, Legacy Yarn, Legacy Fabric API. Produces `ash-client-1.8.9.jar`. |
| `bench/` | The frame-time measurement's own logic: the scene, the camera's path, the summaries and the result file. A developer tool; only each target's `benchmark` source set uses it, and no player's jar holds any of it. See *Measuring frame time*. |

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

`build` compiles every module, runs their tests, and runs
`checkNoGameTypes`, and `checkJarHasNoBenchmark` on each target. CI runs this,
and also compiles the frame-time measurement, on every push.

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
module has no registries and applies no mixins of its own. (It compiles against
Mixin's API and ASM, since #25, to hold the check that tells a landed mixin from
an empty one - which it tests with class files built by hand, no loader needed.)

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
own client game tests (`src/gametest`): a vanilla client that asserts ash is
loaded and wrote `config/ash.properties`, screenshots the title screen, then
joins a dedicated server, waits for its chunks to render and screenshots that.
A dedicated server rather than a singleplayer world because the latter cannot
finish loading under the framework on a CI runner - ADR-0016 has the chain. On 1.8.9
no such framework exists - none of Legacy Fabric API's 44 modules is a gametest
module - so `src/smoketest` is ash's miniature of it: a vanilla client that
prints its mod list, makes the same two assertions, starts a flat world, lets
the HUD draw for three seconds, screenshots it and shuts itself down.

Before either enters a world, both read the load report the real client wrote
and expect every feature in it to have loaded. Once in the world, both drive
toggle sprint through a real key press and
check the player actually sprints, that a second press stops it, and that its
default key collides with no binding the game has. The 1.21.11 test also asks
the server what it was sent: the sprint key held while toggled on, and released
while an inventory is open - which is what a vanilla client in a menu sends.
The 1.8.9 one drives `KeyBinding`'s own statics rather than a keyboard, since
there is no input framework on that target, so the Windows manual pass is still
what proves the keyboard end of it there.

Both go into a world because ash's in-game code only runs in one. The HUD is
drawn only there, and the player - whose movement tick is where toggle sprint's
mixin lands - only exists there; a test that stopped at the title screen would
pass with all of it broken. What was drawn is checked by eye, from the two
`ash-in-world` screenshots CI keeps, rather than by pixel. Neither test mod declares a dependency on `ash`, deliberately: with one,
the loader would refuse to start when ash was missing and the assertion would
never be the thing that noticed.

Both run in CI on Linux only, and that is a capability rather than a
preference - see `docs/adr/0016-ci-accepts-the-minecraft-eula.md`, which also
records what they cost and what a headless runner does and does not provide.

**The 1.21.11 test runs twice: once as it is, and once with Sodium** (#48),
as `runClientGameTestSodium`.

- Sodium's mixin configs are required, so a clash with one of ash's is a crash
  at launch. The second run finds that before a player does, and every later
  feature is held to it.
- The jar is pinned by version, URL and SHA-512 in `gradle.properties`. A
  download whose hash is not the pinned one is refused.
- It goes into that run's own `mods` folder, where the loader remaps it as it
  would a player's.
- The test is told which run it is in. It insists Sodium loaded exactly when
  promised, and that the load report counts it as a player's mod.
- Sodium is tested against and never shipped: none of it reaches the jar
  (ADR-0013).

## Measuring frame time

The frame-time measurement runs one fixed scene in a real game and records
every frame's time. Phase 3 uses it to decide which 1.8.9 optimisations
exist, whether Lithium earns its place, and what each feature costs.

```
./gradlew :target-1.8.9:runBenchmark -Pbench.label=baseline
./gradlew :target-1.21.11:runBenchmark -Pbench.label=baseline
```

**Run it by hand, on a real machine.** CI has no GPU, and its software
renderer's numbers mean nothing for a player, so CI only compiles it. Plug a
laptop in and set Windows to the **Best performance** power mode. On Balanced,
a laptop's clocks drift: in trials, 1.21.11 rose 18% within one run. Close
what you can, start the command, and leave the window alone
until it closes itself: about four minutes on 1.8.9, and up to five on
1.21.11, whose world takes longer to generate.

**The scene** is in `bench/.../Scene.java`, the same numbers on both targets:

- a world from one seed, made afresh each run;
- a spectator 110 blocks up, turning a full circle every 30 seconds, by the
  clock rather than by the frame;
- noon, clear weather, no mobs;
- render distance 8, a 1280 by 720 window, GUI scale 2, no frame-rate cap,
  no vsync.

**What happens:**

1. A warm-up of at least 40 seconds, more than a full turn, while every chunk
   in view is built once and the JIT settles. On 1.21.11 it lasts until the
   world has also *settled* for 5 seconds: no chunk work waiting on the
   server, every section in view built. That took 61 to 92 seconds in
   trials. If the world hasn't settled by 3 minutes, the passes start anyway
   and the result says `"settled": false`.
2. Five measured passes of 30 seconds each.
3. On 1.8.9 only, 15 seconds with the game's own profiler on: the F3 pie
   chart's data. Those frames are not counted, because profiling costs time.

**Results** go to `client/benchmark-results/`, outside `build` so a clean
keeps them. Each run writes two files:

- `<target>-<label>-<time>.json`, which has:
  - the scene;
  - the machine (processor, graphics card, driver, OS, Java);
  - ash's settings for the run;
  - each pass, and all passes together, as average FPS, average frame time,
    **1% low** (the frame rate of the slowest hundredth of frames) and worst
    frame;
  - the **spread** between passes, and the **uncertainty** of the average;
  - how long the warm-up took, and whether the world had settled;
  - on 1.8.9, the **profile**: each section of the game's frame worth at least
    1% of it, five levels deep. One section is the measurement's own:
    `terrain_setup.culling.open_faces`, the scan of the camera's chunk section
    that the game's culling makes each frame. A mixin in the benchmark's source
    set marks it out, so it never reaches a player.
- `...-frames.csv`, with every frame's time.

The log's last lines say the same in one sentence.

**Comparing two runs:**

- A single pass varies. In trials on a laptop, 1.21.11 at around 600 FPS
  swung between 566 and 635 from one half-minute to the next. So compare runs
  by their averages, not by single passes.
- The **spread** is the standard deviation of the passes' average frame rates
  over their mean. The **uncertainty** is the spread over the square root of
  the number of passes: the standard error of the run's average.
- A run whose uncertainty is over 2%, or whose world never settled, is marked
  `"comparable": false`. Run it again.
- Two runs differ only when their averages are further apart than twice
  their combined uncertainty, √(u₁² + u₂²). Anything less has not been shown
  to move the frame rate. Run the baseline twice first, to see how far this
  machine disagrees with itself.
- **Compare runs from one sitting only.** Between two sittings an hour apart,
  the same 1.8.9 build on the same laptop moved from 440 to 379 FPS, far
  outside either run's uncertainty. So a before and after are run back to
  back, alternating - off, on, off, on - and the pairs are compared, never a
  run against an older one.

**Features on and off:** before a run, edit
`target-<version>/build/run/benchmark/config/ash.properties`. It is the run's
own settings file, written on the first run. The result records every setting,
so a run with a feature off is never mistaken for one with it on.

## Mixins, and what happens when one stops matching

A feature whose mixin does not land is left out and reported rather than
crashing the game - ADR-0017. Both targets' `ash.mixins.json` are `"required": false` with
`defaultRequire: 0`, because `required: false` alone is not enough: an
injector whose call site has gone throws an `InjectionError` that escapes
Mixin's error handling and stops the game whatever the config says.

(Not every Mixin error goes: an `allow` limit exceeded or a `CAPTURE_FAILHARD`
mismatch still throws, which is why ash's mixins use neither.)

That makes a missing match silent, so `AshMixinPlugin` (in `shared`, because
Mixin and ASM are the same API on both targets) keeps each class ash's mixins
went into, and `InjectorWiring` checks that every injector's handler is called
from it. Each target's `AshClient` loads the class a feature's mixin targets
at startup - which is what applies the mixin - and asks. A feature whose mixin
did not land does not register its binding, and the client writes
`ash/load-report.json` for the launcher to read before the next play.

`AshMixinsLandTest` runs that same check on every `./gradlew build` in the
1.21.11 module, against the real loader and the real game classes: a game
version that breaks a mixin fails the build by mixin and injector. There is no
equivalent on 1.8.9, where Fabric Loader JUnit cannot start (above), so there
it is the smoke test, reading the load report the real client wrote.

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
