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
- CI gets slower: client game tests need a full game launch per target. **Measured 2026-09-19, on the modern target: 72 seconds** for the whole job on `ubuntu-latest`, cold — checkout, JDK, Gradle, the game's own download, and the client run. That is the same wall time as the compile-only `client` job, and about a sixth of the Rust job. It is cheap enough that the question of whether to run it on every push does not arise.
- ~~**Unverified: whether Fabric's client game tests run on a GPU-less Windows CI runner at all.**~~ **Answered 2026-09-19, while implementing #22, and the answer is better than feared on one side and worse on the other.**

  On a GPU-less **Linux** runner it works. Loom wraps the run in `xvfb-run` there, the client reaches its title screen under software GL, and the run log lists `ash 0.1.0` among 52 mods. The only complaint is a non-fatal `X11: Standard cursor shape unavailable`. A screenshot is kept as a CI artifact, so this is checkable by eye and not only by exit code.

  On **Windows** it remains unverified and is expected not to work: Loom wraps a client run in `xvfb-run` on Linux and nowhere else, and there is no equivalent for a Windows or macOS runner. So this tier is a Linux-runner capability, while ash itself ships on Windows. That is a gap between where the client is tested and where it runs, and it is the reason the manual acceptance pass does not go away.

  ADR-0017 keeps its safety net, so it is not reopened.
- Nothing else in the toolchain needs authentication. Loom, `legacy-looming`, Yarn, Legacy Yarn, Fabric API, Legacy Fabric API and Fabric Loader are all MIT, CC0 or Apache-2.0, and every repository ash builds against is anonymous. This is the only acceptance in the chain, which is why it is worth naming.
