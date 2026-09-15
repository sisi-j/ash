# Two version targets to start: 1.8.9 and 1.21.11

The original brief called for 1.16 through 1.21.x plus 1.8.9, but a range is not a set — each minor version is its own mixin target, and every feature would be written and regression-tested six or seven times. Starting with 1.8.9 and one modern target exercises both loader paths and covers where competitive players actually are, at the smallest maintenance surface that proves the pattern.

## Revised 2026-09-15: the modern target is 1.21.11, and that is now a choice

This ADR originally said "the latest 1.21.x". That phrase no longer picks out what it used to, and leaving it would have meant inheriting a decision instead of making one.

Minecraft: Java Edition changed its version scheme. The current release is 26.2; 1.21.11 (December 2025) is the last 1.21.x. The numbering is the smaller half of it. **From 26.1 the game ships unobfuscated** — no `client_mappings` download, Fabric Meta reports intermediary `0.0.0`, and Fabric has deprecated Yarn, saying it "can't see a way to justify maintaining Yarn in its current state".

So the modern target is now a choice between the last obfuscated version and the first unobfuscated era. 26.x is the easier build by some distance: no intermediary, no refmaps — refmaps exist only because of obfuscation, and their failures are runtime-only and read as "the injector matched nothing" — and no mappings licence problem.

ash targets **1.21.11** anyway, because that is where competitive players are, which is the same reason this ADR gave for its original choice. 1.21.11 is also frozen, so its maintenance cost does not grow.

**The trigger to move is readiness, not a date or an upstream event.** When the client is developed enough that adding a version target is mechanical, versions get added in bulk and the modern era is entered then. That is only true if the seam holds, which is what ADR-0015 exists to protect.

## Consequences

- Adding targets later is additive, so this is cheap to revisit — but only if features are written against a version-abstraction seam from the start rather than against one version's API directly. This was always the condition; it is now load-bearing, because the plan is explicitly to add versions in bulk later.
- "Cheap to add versions" holds **within** an era, not across one. 1.21.11 and 26.x differ by obfuscation. 1.8.9 needs Legacy Yarn because Mojang publishes no mappings for it at all — its version metadata carries `client`, `server` and `windows_server`, and nothing else. Mass-adding 1.16 through 1.21 later is genuinely cheap; 1.8.9 and 26.x will always be their own work.
- ash builds 1.21.11 against a toolchain Fabric has announced it is retiring. That is accepted: the target is frozen, so the toolchain only has to keep working, not keep up.
- Recorded explicitly because it contradicts the written brief; the narrowing is deliberate, not an oversight.
