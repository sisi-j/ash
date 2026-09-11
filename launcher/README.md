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

## Fonts

The app's CSP blocks remote hosts, so Sora, Inter and JetBrains Mono need
bundling as local woff2 before they can be used. A system stack stands in
until then rather than silently failing to load.
