# One beta switch, set when the launcher is built

The beta phases' work is held back from releases by **one switch, `ASH_BETA`, read when the launcher is built**. A build with `ASH_BETA=1` is a **test build**, with the beta work in it; any other build is a release build, without it. ash's client has no switch of its own: the launcher tells the game which build started it.

## Context

The beta is released once, as `0.2.0-beta.1`, after all five beta phases (spec 0006). Until then the beta phases merge to `main` as they finish, and alpha patch releases are still cut from `main`. Without a switch, the first alpha patch after a beta merge would ship half a beta to testers.

There are three places beta work lives: ash-core, the launcher's UI, and ash's client jars. A switch read separately in each could disagree. The client jars are a particular trap. CI builds them once, in the `client` job, and that one set of jars is both tested and shipped inside the release installer. A switch built into the jar would have to be on for the tests and off for the release, from the same build.

## Decision

- **Read once.** The launcher's build script, `src-tauri/build.rs`, reads `ASH_BETA`. `1` sets the `ash_beta` cfg, and the adapter hands it to ash-core as `Config::beta`. Nothing else reads the environment.
- **ash-core** sees only `Config::beta`, a value like the rest of `Config`, so a test can try either build. A beta feature in ash-core checks it.
- **The UI** asks the launcher once (`beta_build`), and a beta feature reads it with `useBeta()` (`src/beta.tsx`). It's off until the launcher answers.
- **The client** is told by the launch: a test build adds `-Dash.beta=true` to every launch that loads ash's client, and a release build adds nothing. A beta feature in the client asks `Beta.on()`. The jars are the same in both builds, so CI's one set of jars can be tested with the switch on and shipped with it off.
- **Tests run with it on.** CI's Rust job sets `ASH_BETA=1`. Every Gradle run of the game, including the game tests, smoke tests and benchmarks, sets `ash.beta`. `ui-check`'s fake launcher reports a test build in every state but one. ash-core's tests set `Config::beta` for whichever build they're testing.
- **Releases run with it off.** CI's `installed` job builds the installer a release publishes without `ASH_BETA`, and fails if the installed ash's self-check reports a test build.
- **A test build is made by** `npm run build:test-installer`, from `launcher/`. It's never published.
- **A test build says so:** Settings shows "test build, with beta features" beside the version.

## Consequences

- Alpha patch releases can be cut from `main` at any point during the beta.
- Beta work is in a release's code and its client jars, switched off. Only the launcher can switch it on, and a release launcher never does. A player who started the game by hand with `-Dash.beta=true` would get unfinished features. That's their choice, not a leak.
- Every beta feature carries one check until the beta release. The merge that sets `0.2.0-beta.1` should remove the switch, and every check with it, so no dead branch outlives the beta.
- An alpha patch fix to something a beta phase has changed has to work with the switch both off and on. ash-core tests can cover both builds; the client's game tests cover only the test build.
