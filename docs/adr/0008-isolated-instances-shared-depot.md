# Isolated instance directories over a shared depot

Each instance owns its game directory — saves, config, resource packs, screenshots, mods — while downloaded jars, libraries and assets live once in a content-addressed depot every instance draws from. This is the Prism/MultiMC model, and it avoids the collisions a shared `.minecraft` produces across a version span this wide.

## Consequences

- 1.8.9 and 1.21.x resource packs use incompatible pack formats and would otherwise share one folder; isolation removes a whole class of confusing runtime bugs.
- Recorded because migrating users' game directories after release is genuinely painful, so this is expensive to reverse once anyone has installed.
- Instance settings that sync must be separated from machine-local overrides, since memory allocation and Java paths cannot follow a player to a different machine.
