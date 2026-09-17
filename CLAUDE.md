## Checks

From the repo root:

- `cargo test --workspace`, or one file while iterating: `cargo test -p ash-core --test launch`
- `cargo clippy --workspace --all-targets -- -D warnings`
- `cargo fmt --all`
- `cd launcher && npx tsc --noEmit`
- `cd client && ./gradlew build` - compiles both client modules, runs the
  shared module's unit tests, and checks that it still cannot see the game

Tests run in milliseconds; the cost is compilation. `target/debug` grows past 10GB and is safe to delete.

The client build is the slow one: its first run downloads Minecraft and
Mojang's mappings. Nothing in the Rust workspace depends on it - see
`client/README.md` for why that is deliberate.

## Coding standards

`CODING_STANDARDS.md`, read during review rather than while implementing.

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues, using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout (`CONTEXT.md` + `docs/adr/` at the repo root). See `docs/agents/domain.md`.
