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
- CI gets slower: client game tests need a full game launch per target. **Measured 2026-09-19 on `ubuntu-latest`, across four runs of the finished job:**

  | Step | |
  | --- | --- |
  | `:target-1.21.11:runClientGameTest` | 55–65 seconds |
  | `x11-xserver-utils`, which only the 1.8.9 tier needs | 9 seconds |
  | `:target-1.8.9:runSmokeTest` | 11 seconds |
  | the whole job, both targets, including checkout, JDK and Gradle | 86 seconds |

  The 1.8.9 tier costs a fifth of the modern one and proves less, in the same proportion: no ticking, no screenshot, no framework. Together they are still about the wall time of the compile-only `client` job and a sixth of the Rust job, which is cheap enough that whether to run them on every push does not arise.

  *An earlier revision of this bullet called its 72-second figure cold. It was not.* `setup-gradle` restores `~/.gradle` from the `client` job, so the game's own download is already there — and the modern step lands between 55 and 65 seconds on every run measured, which is what a warm cache looks like rather than a cold one. A genuinely cold run has not been measured and would cost more.
- ~~**Unverified: whether Fabric's client game tests run on a GPU-less Windows CI runner at all.**~~ **Answered 2026-09-19, while implementing #22, and the answer is better than feared on one side and worse on the other.**

  On a GPU-less **Linux** runner it works. Loom wraps the run in `xvfb-run` there, the client reaches its title screen under software GL, and the run log lists `ash 0.1.0` among 52 mods. A screenshot is kept as a CI artifact, so this is checkable by eye and not only by exit code.

  *Corrected: an earlier revision said the only complaint was a non-fatal `X11: Standard cursor shape unavailable`. That was written from the parts of the log that were read rather than from the log.* A headless runner with no sound card, no account and no network to Mojang also loses the narrator (`Unable to load library 'flite'`), Realms (`Failed to fetch user properties`, `Couldn't connect to realms`) and sound (`Error starting SoundSystem. Turning off sounds & music`). All are non-fatal and none is ash's, but "the only complaint" was not true, and a tier whose value is that it notices things should not have its own record overstated.

  On **Windows** it remains unverified and is expected not to work: Loom wraps a client run in `xvfb-run` on Linux and nowhere else, and there is no equivalent for a Windows or macOS runner. So this tier is a Linux-runner capability, while ash itself ships on Windows. That is a gap between where the client is tested and where it runs, and it is the reason the manual acceptance pass does not go away.

  ADR-0017 keeps its safety net, so it is not reopened.
- **What a GPU-less runner actually needs is not the same on both targets, and the difference is LWJGL.** The modern client came up on `ubuntu-latest` unassisted. The 1.8.9 client crashed in `initializeGame` with `No display mode extension is available`, which reads exactly like a runner refusing the job and is not. LWJGL 2 decides whether it can set a display mode in `LinuxDisplay.isXrandrSupported`, and that method looks for an executable named `xrandr` on `PATH` and returns false before it ever asks the X server:

  ```java
  if (findXrandr() == null) {
      return false;
  }
  ```

  The fallback, XF86VidMode, is an extension Xvfb genuinely does not implement. The runner image ships Xvfb but not `x11-xserver-utils`, so both checks failed. Installing that one package fixes it, and the run log now says `Xrandr extension version 1.6` / `Using Xrandr for display mode switching`. LWJGL 3 asks GLFW instead, which is why the modern target never wanted any of this.

  This matters beyond 1.8.9: every version target up to 1.12.2 is LWJGL 2, so any target added below 1.13 inherits the same requirement.
- **Neither target gets sound, and 1.8.9 barely gets a log.** OpenAL fails to open a device on the runner — non-fatal on both, and the client carries on. On 1.8.9, Log4j also rejects most of Fabric's logging config on startup (`Error processing element Queue: CLASS_NOT_FOUND`, then every appender reference left with an invalid level), so INFO goes nowhere and the log file CI uploads holds three ERROR lines about twitch and OpenAL. The modern target's log is 181 lines with the full mod list in it; this one's is three. That is why the 1.8.9 smoke test prints its own mod list to standard out instead of trusting the log.
- Nothing else in the toolchain needs authentication. Loom, `legacy-looming`, Yarn, Legacy Yarn, Fabric API, Legacy Fabric API and Fabric Loader are all MIT, CC0 or Apache-2.0, and every repository ash builds against is anonymous. This is the only acceptance in the chain, which is why it is worth naming.
