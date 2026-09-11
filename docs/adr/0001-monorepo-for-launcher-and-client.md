# Monorepo for launcher and client, separate repo for backend

The launcher and the client ship together, version together and share a domain vocabulary, so they live in one repo and one `CONTEXT.md`. The backend has an independent deploy cadence, a different runtime and its own operational concerns, so it stays a separate repo even though the launcher is its only consumer at first.

## Consequences

- Two toolchains in one repo: Rust/TypeScript under `launcher/`, Java/Gradle under `client/`. CI has to handle both.
- Types shared between the launcher and the backend cross a repo boundary and need publishing or duplicating.
