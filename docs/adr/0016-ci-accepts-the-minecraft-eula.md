# CI accepts the Minecraft EULA, deliberately

ash's client build enables Fabric's client game tests, which requires `fabricApi { configureTests { eula = true } }` in a Gradle script. Fabric documents that line as: *"By setting this to true, you agree to the Minecraft EULA."*

This ADR exists because that is a legal acceptance sitting in a build file, made on behalf of whoever runs CI, and it would otherwise arrive by copying a template.

## Context

Fabric offers three tiers of testing. Fabric Loader JUnit unit-tests mod logic with no game and no acceptance. Game tests use Minecraft's server-side framework. **Client game tests** launch a real client, and those are the only tier that can catch a mixin which has stopped matching its target.

ash needs that tier specifically. ADR-0017 chooses to let features degrade rather than crash when a mixin does not apply, and a degradation that nothing detects is indistinguishable from a feature quietly not existing. Client game tests are what make that choice safe rather than merely survivable.

## Decision

Enable client game tests, and record the acceptance here rather than let it live only as a boolean in a build script.

## Consequences

- Every CI run accepts the Minecraft EULA on behalf of the repository owner. Anyone forking or running this build is doing the same, and this ADR is where they can find that out.
- CI gets slower: client game tests need a full game launch per target.
- **Unverified: whether Fabric's client game tests run on a GPU-less Windows CI runner at all.** This is not established and is the first thing to find out when the client build lands. If it does not work, the choice in ADR-0017 loses its safety net and that decision should be reopened rather than worked around.
- Nothing else in the toolchain needs authentication. Loom, `legacy-looming`, Yarn, Legacy Yarn, Fabric API, Legacy Fabric API and Fabric Loader are all MIT, CC0 or Apache-2.0, and every repository ash builds against is anonymous. This is the only acceptance in the chain, which is why it is worth naming.
