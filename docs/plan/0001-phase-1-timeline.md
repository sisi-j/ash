# Phase 1 timeline — sign in, prepare, launch vanilla on Windows

Written 2026-09-09. Revise as reality disagrees with it.

## Assumptions this rests on

- **~10 hours per week**, evenings and weekends. Every date below moves proportionally if that changes.
- **Rust proficiency is the largest unknown.** These estimates assume you can write Rust without fighting the borrow checker for hours. If Rust is new, add 30–50% to every engineering estimate — and that is normal, not a failure.
- Effort is stated in hours because hours are what you control. Dates are derived, not promised.
- The allow-list wait is unbounded and excluded from the critical path below. Nothing in Track B waits on it.

## The shape of it

Two tracks run in parallel. Track A is short, mostly not code, and gates the *end* of Phase 1. Track B is long, entirely unblocked, and is where the actual work is.

The one sequencing rule that matters: **Track A goes first even though Track B is more interesting.** The allow-list request has an unknown wait that starts only when you submit. Every evening you spend on Rust before submitting is an evening added to the end of the project, not the middle.

---

## Track A — unblock the acceptance criterion

**Weeks 1–2 · 2026-09-09 → 09-22 · ~12h**

| # | Task | Effort |
|---|---|---|
| A1 | Register the Azure app. Name it `ash` — nothing containing "Minecraft". Personal Microsoft accounts, public client flow, no secret, scopes `XboxLive.signin offline_access`. | 1h |
| A2 | Stand up the publisher site: who the publisher is, a real contact email (not a Discord invite), a privacy statement, an EULA, and the disclaimer *"NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."* | 8h |
| A3 | Submit the allow-list request at `aka.ms/mce-reviewappid`. **Record every question the form asks, verbatim, and the submission date, into the research document.** Nobody has published this. | 2h |
| A4 | Register the legal entity, or at minimum decide what it will be. ADR-0010 requires a named publisher and seller. | 1h |

**Gate:** request submitted. After this, Track A is waiting, not working. Check monthly; record the outcome and elapsed time either way.

Do not bundle the monetisation question into this form. It is a security review; keep it clean.

---

## Track B — Phase 1 engineering

**Weeks 2–20 · ~145h**

### Foundation — weeks 2–3 · ~14h

| # | Task | Effort |
|---|---|---|
| B1 | `gh auth login`, create the private repo, push, move the spec to a labelled issue, run `/to-tickets`. | 2h |
| B2 | Workspace scaffold: `ash-core` library crate, `src-tauri` adapter, React frontend, a window that opens. | 6h |
| B3 | Test harness: the HTTP port and its fake, the process port and its fake, fixture loading. Build this before the modules that need it. | 6h |

**Gate:** `cargo test` runs green against a fixture, and the app window opens.

### Accounts — weeks 4–6 · ~20h

The biggest single chunk and the one with the most unknowns. Hops 1–4 hit real services; hops 5–7 run on hand-written fixtures until the allow-list lands.

Device-code flow, the full token chain, Windows Credential Manager storage, account CRUD, silent refresh, and the distinct typed outcomes — expiry, denial, no entitlement, throttling. Capture real fixtures for hops 1–4 while you build; that de-risks the module now rather than at approval time.

**Gate:** a fixture-driven sign-in produces an account keyed by Minecraft profile UUID, and every failure mode returns its own typed error.

### Catalogue and instances — weeks 7–8 · ~16h

Version manifest fetch, cache, release/snapshot filtering, cache age. Then instance CRUD, the isolated directory layout, settings, and the machine-local override split.

**Gate:** two instances exist on disk with genuinely separate game directories.

### Depot — weeks 9–11 · ~20h

Content-addressed storage, bounded parallel downloads, resume, hash and size verification, dedup, typed progress events, cancellation. Fiddlier than it sounds — resume plus concurrency plus verification is where the bugs live.

**Gate:** two instances on the same version cause exactly one download; a corrupted file is detected and re-fetched.

### Runtime — week 12 · ~8h

Java runtime provisioning from Mojang's manifest. System Java ignored entirely.

### Launch — weeks 12–15 · ~24h

The most bug-prone module in the project. Version metadata resolution, rule evaluation, classpath assembly, natives extraction, and **both** argument formats — the legacy string for 1.8.9 and the structured rule-evaluated lists for 1.21.x.

Build 1.21.x first, then 1.8.9. When 1.8.9 forces the abstraction to change, that is the module working as intended.

**Gate:** the recorded invocation is correct for both targets, asserted without spawning a JVM. This is the highest-value test in Phase 1.

### UI — weeks 15–17 · ~20h

Left rail, play CTA, account switcher, instance details, progress, settings. Grayscale palette, Sora / Inter / JetBrains Mono. Judged on function, not polish — the branding pass is Phase 5.

### Hardening — weeks 18–19 · ~12h

Paths with spaces and non-ASCII characters, disk full, antivirus quarantine, the single-instance lock, concurrent launches of *different* instances, logging with token redaction.

### Packaging — week 20 · ~6h

Windows installer. Fresh-machine install test.

---

## Milestones

| Milestone | Target | Blocked on |
|---|---|---|
| Allow-list request submitted | 2026-09-22 | nothing — do it first |
| First green test | 2026-09-29 | nothing |
| Sign-in works on fixtures | 2026-10-20 | nothing |
| A prepared instance on disk | 2026-11-24 | nothing |
| Correct invocation, both targets | 2026-12-22 | nothing |
| Feature-complete, installable | 2027-01-26 | nothing |
| **Phase 1 done** | **unknown** | **allow-list approval** |

Feature-complete lands around **late January 2027**. Phase 1 *done* — the spec's real-account, real-machine, main-menu acceptance — lands whenever Mojang answers. That gap is why Track A goes first.

## What to do if the answer is no, or never comes

Worth deciding before it happens rather than during. The realistic options are: ship as a launcher that requires the player to have signed into the official launcher at least once and reuse what is available locally; pivot to client-only distribution as a mod loaded through an existing allow-listed launcher; or stop. Each has a very different shape, and none is a small adjustment. If nothing has come back by the feature-complete milestone, that is the moment to choose.

## Deliberately not in this timeline

Phases 2–5 — the client, Legacy Fabric, the backend, macOS, the branding pass. Estimating them now would be fiction; the Phase 1 build will teach you enough to estimate Phase 2 honestly. Revisit at feature-complete.
