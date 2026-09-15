## Checks

From the repo root:

- `cargo test --workspace`, or one file while iterating: `cargo test -p ash-core --test launch`
- `cargo clippy --workspace --all-targets -- -D warnings`
- `cargo fmt --all`
- `cd launcher && npx tsc --noEmit`

Tests run in milliseconds; the cost is compilation. `target/debug` grows past 10GB and is safe to delete.

## Coding standards

`CODING_STANDARDS.md`, read during review rather than while implementing.

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues, using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout (`CONTEXT.md` + `docs/adr/` at the repo root). See `docs/agents/domain.md`.
