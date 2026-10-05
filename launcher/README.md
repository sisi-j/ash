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

## The client

`client/` builds the jar the launcher places into a modded instance. The
launcher does not need it to compile or to test, but an installer does:

```
cd client && ./gradlew build
cd ../launcher && npm run build:installer
```

`npm run tauri build` on its own produces an installer with no client in it.
`build:installer` is the one that carries it - see `client/README.md`.

## Testing

```
cargo test --workspace
```

No test touches the network or spawns a process. Outbound access goes through
a port (`ash_core::http::HttpPort` today) and tests supply `FakeHttp`, which
serves recorded fixtures and panics on an unrouted URL rather than inventing a
plausible 404. Depot and instance roots are plain config values pointed at a
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
preparing (with launch sounds on and off), playing, a failed launch, a crash,
an instance that could not be checked, Download only, a degradation notice,
the instance page, the new-instance form, and each sidebar page. The
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
