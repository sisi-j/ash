# Legacy Fabric as the loader for 1.8.9

Fabric proper does not target 1.8.9, and 1.8.9 is a first-class version target because that is where a large part of the competitive PvP population still plays. Legacy Fabric backports the Fabric toolchain to that era and is the established community route, so the client uses one loader family across both targets instead of adding Forge as a second paradigm.

## Consequences

- Two loaders to install and manage, but one mixin and build model.
- Legacy Fabric is a smaller volunteer project than Fabric; upstream breakage has fewer people to fix it.
