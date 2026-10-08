# ash launcher

Two crates and a React frontend.

| | |
| --- | --- |
| `core/` | **ash-core** — all launcher behaviour. No Tauri, no UI, no globals. Driven through the `Ash` struct, which is the single inbound seam. |
| `src-tauri/` | The adapter. Each `#[tauri::command]` delegates to exactly one `ash-core` operation and holds no logic. If logic accumulates here, it belongs in `ash-core`. |
| `src/` | React + TypeScript. Talks only to the commands. |

## Running it

```
cd launcher
npm install     # once
npm run tauri dev
```

A dev build launches ash-client instances with the jars from the last
`./gradlew build` in `client/`, gathered into `client/build/dev-run` when it
starts. Build the client first, and restart `tauri dev` after rebuilding it.

## The client

`client/` builds the jar the launcher places into a modded instance. The
launcher does not need it to compile or to test, but an installer does:

```
cd client && ./gradlew build
cd ../launcher && npm run build:installer
```

`npm run tauri build` on its own produces an installer with no client in it.
`build:installer` is the one that carries it - see `client/README.md`.

## Checking an installation

`ash --self-check <report file>` checks an installed ash and exits. It looks
from where the installed launcher looks, and does nothing else: no window,
no game, no sign-in. It reports whether:
- each version's client jar is in the installation and is a jar;
- the `-Dfabric.addMods` argument a launch would send names that jar by a
  path Java can read;
- ash's data folder can be written to.

It exits 0 if all is well and 1 if not, with the findings in the report.

CI's `installed` job builds the real installer, installs it silently on
Windows and runs this. An installation is the only place some faults exist:
the installed ash once handed the game its client jar as
`\\?\C:\Program Files\...`, which 1.8.9's Java cannot read. The same command
works for diagnosing a player's installation.

## Testing

```
cargo test --workspace
```

No test touches the network or spawns a process. Outbound access goes through
a port, and tests supply its fake:
- `HttpPort` gets `FakeHttp`, which serves recorded fixtures and panics on an
  unrouted URL rather than inventing a plausible 404.
- `ServerPort`, which reaches game servers, gets `FakeServerPort`. It runs
  scripted servers over in-memory pipes and refuses any address it has no
  server for. Depot and instance roots are plain config values pointed at a
`TempDir`, which is why `ash-core` never reads the environment itself —
resolving a real path is the adapter's job.

## The launcher UI check

```
cd launcher
npx playwright install chromium   # once
npm run check:ui
```

Builds the real screens as the app builds them, but with
`ui-check/fake-api.ts` in place of `src/api.ts`, and renders each state that
matters in Chromium: signed out, no instances, idle, the account menu,
preparing (with launch sounds on and off), playing, the Servers card (recent servers
first and the title an instance that has joined nothing keeps, online
and offline servers, a Join, and an instance with none), the "This instance" card
(for an ash instance and a vanilla one, and its shortcut to the instance page),
a crash with the player's own mods on (named, with Play without them), a failed
launch (which leaves ash open whatever it is set to do), minimising or
closing once the game starts, a crash,
an instance that could not be checked, Download only, a degradation notice,
the instance page (the player's own mods switched on, custom memory saved, a refused window size, the delete
confirmation, and back to Play after deleting), the new-instance form, and
each sidebar page, with Settings also showing a custom default memory. The
fake is typed against the real module, so the two cannot drift apart without
a type error. The page is served under the app's own content security policy.

A state fails if it throws, logs an error, never shows what it should, fails
a check of its own (no sound with launch sounds off, a still scene with
reduced motion, no failure card for a failed check), or
draws any text in a face other than the bundled Inter - asked of Chromium's
renderer, which names the font it actually drew with. Screenshots land in
`ui-check/screenshots/` and in CI's `launcher-ui` artifact, for looking at;
they are never compared, because font rendering differs by machine.

## Fonts

Inter 4.1 is the only face, in game and here. Its official woff2 files ship
unmodified in `public/fonts/`, in the five weights the design uses, with the
SIL Open Font Licence beside them, so the licence ships in the app with the
fonts. The app's content security policy blocks
every remote host, so nothing is ever fetched.
