# Two client projects over a module that cannot see the game

The client is two Gradle projects, one per version target, depending on a shared module that contains no Minecraft types and no Fabric API types. The version-abstraction seam ADR-0005 asks for is a set of Java interfaces, not a build step.

## Context

ReplayMod proves a single Gradle project can span 1.8.9 through 26.2, using a source preprocessor plus literal rename tables between mapping families. It works, and it is the right answer for a mod that supports a decade of versions.

ash has two targets. A source-transforming build is a large, permanent piece of machinery to carry for two, and its failures are debugged with the preprocessor held in your head. Two per-target projects over a shared module put the seam in ordinary Java, where it can be read, and — more importantly — tested. Fabric ships Fabric Loader JUnit precisely so that mod logic can be unit-tested without launching the game, and it runs on every `./gradlew build`, CI included.

The seam's shape is not a matter of taste. It is dictated by the widest gap between the two targets, which is the HUD. Modern Fabric API gives a fine-grained, ordered, *named* element registry — 24 vanilla element identifiers, with `addFirst`, `addLast`, `attachElementBefore`, `attachElementAfter`, `removeElement` and `replaceElement`. Legacy Fabric API gives **one callback**, `HudRenderCallback`, which fires after the vanilla HUD and can only draw on top. No identifier, no ordering, no replacement, no removal.

So the shared drawing surface must be expressible as "draw these primitives at this position". It can never be "replace element X", because 1.8.9 cannot honour that. A custom crosshair becomes "suppress the vanilla crosshair by whatever means this target allows, then draw ours", with the suppression living entirely inside the version module.

The rule has to exclude Fabric API too, not just Minecraft. The APIs diverge in their own surface: `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper` against `net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper`. Fabric renamed the module in its Mojmap migration. A shared module that names either one is already broken.

## Decision

Three modules. A shared module of pure logic — feature state machines, timers, settings, formatting — that cannot import anything from the game or from either API. One module per version target that adapts the game to it.

## Consequences

- The shared module is unit-testable with no game running, and cannot rot toward one target, because it cannot see either.
- Mappings differ per target and that is fine: Mojang mappings on 1.21.11 (Fabric's own recommendation for new mods, and 26.x uses Mojang names at runtime, so a later move is close to a no-op), Legacy Yarn on 1.8.9 because Mojang publishes no mappings for it. The shared module never sees a mapped name, so the two families never have to be reconciled.
- Mojang's mappings may not be redistributed complete and unmodified. Loom fetches them at build time and nothing is committed, so this constrains the repository rather than the build.
- Toggle sprint is the feature that proves the rule has teeth, which is why it is in Phase 2 alongside something as simple as an FPS readout. Its binding helper has a different class name on each target and its latch is exactly the kind of state machine the shared module exists to hold.
- Adding a third target means a third thin module rather than a new branch in a preprocessor. If ash ever supports ten versions, revisit this — the preprocessor wins at that scale, and this decision is cheap to unwind while the shared module stays honest.
