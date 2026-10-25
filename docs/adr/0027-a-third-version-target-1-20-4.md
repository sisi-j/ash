# A third version target: 1.20.4

ash adds **1.20.4** as a third version target, alongside 1.21.11 and 1.8.9. It gets ash's whole client, its own pinned Fabric, and the same real-game tests and FPS marks as the others.

## Context

ADR-0005 started with two targets and set readiness as the trigger for more: versions would be added once doing so was mechanical. The alpha's testers play 1.20.4 (spec 0005), so it comes first and alone, ahead of any bulk addition. 1.20.4 is in the same obfuscated, Fabric-and-Yarn era as 1.21.11, which ADR-0005 called the cheap case.

## Decision

- **A third client project, `target-1.20.4`,** over the shared module, as ADR-0015 laid out. Fabric Loader and Fabric API are pinned for it (ADR-0014).
- **The whole feature set,** at hooks found by research before any code. A hook 1.20.4 doesn't have is a degraded feature with its reason (ADR-0017), never a silent gap.
- **Tested in a real game, like the others, and measured:** FPS marks by the README's method. *Amended 2026-10-24:* Fabric's client game tests begin with 1.21.4, so 1.20.4 can't run 1.21.11's. It gets ash's own smoke test, as 1.8.9 does. The test launches a real 1.20.4 client in CI, checks every feature loaded, reads the launcher's server list, walks into a flat world, opens ash's settings with their key and waits for the test CDN's cape. Whether each mixin lands is checked on every build too, by `AshMixinsLandTest` against the real game classes.
- **The client targets Java 17,** the Java Mojang names for 1.20.4.

## Consequences

- Every feature is now written, tested and maintained three times. The shared module carries everything that doesn't touch the game's code, so the cost stays in the mixins.
- It's the first test of ADR-0005's claim that a target in the same era is cheap. How long the port takes is the evidence for adding versions in bulk later.
- CI gets a third in-game test: 1.20.4's smoke test, beside 1.21.11's game test and 1.8.9's smoke test.
