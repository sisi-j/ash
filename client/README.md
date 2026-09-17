# ash client

The game-side half of ash: a Fabric mod, built once per version target over a
module that cannot see the game.

| | |
| --- | --- |
| `shared/` | Pure logic and the version seam. Cannot name a Minecraft type or a Fabric API type, and `checkNoGameTypes` fails the build if it does. Java 8 bytecode, because the 1.8.9 module will consume it. |
| `target-1.21.11/` | The 1.21.11 adapter. Mojang mappings, Fabric Loader, Fabric API. Produces `ash-client-1.21.11.jar`, which is what the installer ships. |

A second target module for 1.8.9 lands in #21. The modules are named for the
version target rather than "modern" and "legacy", because those words rot —
1.21.11 is the *last obfuscated* release and 26.x is already out.

Why three modules rather than a source preprocessor spanning both:
`docs/adr/0015-two-client-projects-over-a-shared-module.md`.

## Building

```
./gradlew build
```

Needs a JDK 21 or newer on `JAVA_HOME` — a modern JDK emits the Java 8
bytecode the shared module wants via `--release 8`; only the *game* on 1.8.9
runs on Java 8. The first build downloads Minecraft and Mojang's mappings and
takes a couple of minutes; later ones are seconds.

`build` compiles both modules, runs the shared module's unit tests and the
target module's loader tests, and runs `checkNoGameTypes`. CI runs exactly this
on every push.

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

## How the jar reaches a player

`target-1.21.11` builds `ash-client-1.21.11.jar` — a fixed name with no
version in it. The launcher looks it up by that name to place it in an
instance's `mods` directory, and the two ship in one installer, so a version
in the file name would be a second place to change on every release with
nothing to catch getting it wrong. Which version is running is a question
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
the Fabric API version. All three are also pinned by the launcher in
`launcher/core/src/loader.rs`, and they have to agree: the launcher installs
the loader and the API that the client then declares it depends on. Moving one
means moving both.
