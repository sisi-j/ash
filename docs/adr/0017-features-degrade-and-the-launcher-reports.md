# A feature that cannot load degrades, and the launcher says so

When one of ash's mixins does not apply, the game still launches and that feature is simply absent. The client records what loaded in a load report; the launcher reads it and shows any degraded feature before the next play.

## Context

Mixin gives a config three ways to behave when a target is missing. With `"required": true` a failure is terminal and the game does not start. Otherwise the mixin is logged and skipped, and the feature silently does not exist. Injectors that match nothing are governed separately by `require` / `injectors.defaultRequire`.

Sodium and Lithium both set `"required": true`. For an optimisation mod that is clearly right: a half-applied renderer is worse than no renderer.

The argument for copying them is that on a competitive client a feature the player believes is running but isn't is a player at a disadvantage who does not know it. That argument is sound, and it is the reason this cannot simply be `required: false` and a shrug.

The argument against is that a crash on launch is the worst outcome a launcher can produce, and it lands on the player rather than on us.

Both arguments assume nothing catches the problem before release. ADR-0016 removes that assumption: client game tests run a real client on both targets in CI, which is precisely the tier that catches a mixin that has stopped matching. With that in place, degradation is a safety net for the case nobody predicted, rather than a way of making shipping broken builds survivable.

## Decision

Features degrade. The client writes a **load report** naming what loaded and what did not. The launcher reads it and surfaces degraded features before play.

## Consequences

- The client and launcher gain a channel, and it runs one way only: the client writes the load report - `ash/load-report.json` in the instance's game directory, replaced each time the client starts - and the launcher reads it. Its format is pinned by one example both sides test against, `launcher/core/tests/fixtures/load-report.json`: the launcher's tests parse it and the client's must produce it exactly. The launcher never writes into the client's config, which stays true to the settings boundary — synced settings are Phase 4, and inventing that format early would mean inventing it twice.
- **A silently degraded first launch reports nothing**, because the client has not run yet to write a report. The failure surfaces before the *next* play. This is an accepted cost of reporting in the launcher rather than in game: one place to look, and it is the moment the player can still act, at the price of one session.
- Conditional application uses an `IMixinConfigPlugin`, the mechanism both Sodium and Lithium use, rather than marking mixins optional and hoping.

  *How, as built in #25.* `"required": false` alone does not deliver this decision, which was the surprise. An injector whose call site has gone - the likeliest way a game update breaks a mixin - throws an `InjectionError`, and that extends `MixinError`, not `InvalidMixinException`: it escapes Mixin's error handling and stops the game whatever the config says. So both of ash's configs are `required: false` **and** `defaultRequire: 0`, and nothing of ash's can stop the game. That makes a missing match silent - the handler merged, nothing calling it - which is the "marking mixins optional and hoping" this decision rules out. The config plugin is what stops it being hope: it keeps each class ash's mixins went into, and a feature asks, after loading the class its mixin targets, whether every one of its injectors is actually called from it. One that is not is a degraded feature: its binding is not registered, and the load report says so.

  The check is made when the feature asks, not when the plugin is told a mixin applied, because MixinExtras writes its `@WrapOperation`s in a later extension pass; judged at `postApply` every wrap looked unwired. And it is tested where a developer meets it first, not only in CI: `AshMixinsLandTest` stands the real loader up in the 1.21.11 module's unit tests, loads every target ash's mixins name, and fails by mixin and injector when one does not land - so a game-version bump that breaks a mixin fails `./gradlew build` in seconds. Renaming toggle sprint's wrapped call, and separately its target method, each fail it by name while the class still loads.
- ~~If client game tests turn out not to run in CI — see ADR-0016, where that is explicitly unverified — this decision loses the thing that makes it safe and should be reopened, not patched.~~ **Answered 2026-09-19, while implementing #22. They run, and this decision keeps its safety net** — a real client on both version targets, launched on every push, asked whether ash is in it. So this is not reopened.

  The net runs on Linux only, and that is measured rather than assumed: a `windows-latest` job was tried on 2026-09-19 and the client hung acquiring an OpenGL context, 28 minutes of silence until a timeout killed it (ADR-0016 has the detail). ash ships on Windows.

  **What that does and does not cost this decision.** The failure mode named above — a mixin that has stopped matching its target — is settled by bytecode and mappings at class-load time, not by the operating system. A mixin that stops matching does so on Linux as well, so the tier still catches the thing this decision leans on, and that is why this is not reopened.

  What the tier cannot see is a Windows-only runtime failure: the native, driver and windowing paths, which are exactly where Windows differs and where CI now has no signal at all. That was never what degradation was for — it is not a mixin failing to apply — but it is a real blind spot on the platform every ash player is on, and the load report will often be the first anyone hears of one. The manual acceptance pass in the phase spec is the only gate there, which is why it does not go away.
