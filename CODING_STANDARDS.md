# Coding standards

Read during review, not implementation. Every rule here applies to the diff under review; the review is not done until each one has been checked against it.

These are the conventions that are not obvious from the code and not enforced by a tool. Formatting, lints and types are already enforced — `rustfmt`, `clippy -D warnings`, `tsc --noEmit` — so reviewing them by eye is wasted effort.

## The seam

**All launcher behaviour lives in `ash-core`.** The Tauri layer in `launcher/src-tauri` is an adapter: each command delegates to one `Ash` method and holds no logic of its own. A decision that lands in the adapter is wrong even when it is one line, because nothing can test it there.

**Reaching outside the process goes through a port.** `HttpPort`, `CredentialStore`, `ProcessPort`. A new outbound dependency means a new port and a fake beside it, never a direct call.

**A fake must be able to express the failure being tested.** `FakeHttp` could originally only say "a server answered badly", so "there is no network" had to be added before offline behaviour could be tested at all. When a test needs a situation the fake cannot produce, extend the fake — reaching around it proves nothing.

**Tests drive the product through `Ash`.** No test touches the network, the credential store, or spawns a JVM.

*Scoped 2026-09-19, while implementing #22.* That rule is about the Rust workspace, where `Ash` is the seam and a spawned JVM would mean the fakes had been reached around. It never governed the client's own build: the tier that launches a real vanilla client on each version target in CI both touches the network and spawns a JVM, on purpose, because there is no other way to find out whether a mixin still matches its target. See ADR-0016.

## Secrets

**Redaction is structural, never a filter at the point of output.** A type that can carry a token does not derive `Serialize` — see `Invocation` — and the shape that crosses to the UI is redacted by construction — see `InvocationView`. Where a secret has to sit in a struct beside ordinary fields, `Debug` is written by hand — see `Session`. A redaction pass that scans strings on the way out protects only the sites someone remembered to route through it.

**A new field carrying a token, a device code, or a refresh token is not reviewed until its `Debug`, its `Serialize`, and every path that can log it have been checked in the same diff.**

## Errors

**`user_message` never leaks a URL, a filesystem path, a token, or a library's error text.** One documented exception: `VerificationFailed` and `FileVanished` name the depot-relative path, because that is the one thing a player needs in order to write an antivirus exclusion. The exception carries a comment saying why, in place. Extending it requires the same.

**One variant per distinct player action.** Two failures that need the player to do different things — or where one is retryable and the other is not — are two variants. The UI branches on `kind()`, so `kind()` is part of the contract; the display string is not.

## Comments

**Comments say why.** The crate runs about 18% comment lines. That density is intended: the reason a thing is the way it is cannot be recovered from the code, and this codebase is read far more often by someone arriving cold than by its author.

**A comment that exists to stop something being reintroduced stays.** `version.rs` explains why there is deliberately no `artifact_for`; the comment is the only thing standing between this crate and the bug returning. Treat deleting one of these as a behaviour change.

## Tests

**A test that could pass without exercising its subject asserts that it did.** `hardening.rs` checks that the flag it is about to search for redaction is present at all — *"the test proves nothing if the flag is absent"*. This has already caught one test that was passing while proving nothing. Apply it wherever a test's premise could disappear silently.

**Fixtures that are hashed are built, not checked in.** A stored archive and the hash asserted beside it drift apart; a fixture built in the test cannot.

## Vocabulary

**Domain terms come from `CONTEXT.md`.** Names in code, tests and errors use the glossary's term, not a synonym it marks as avoided. A concept the glossary does not have yet is a signal: either it is invented language, or there is a real gap worth recording.

**Decisions that are hard to reverse are recorded as ADRs** in `docs/adr/`, and a diff that contradicts one says so out loud rather than quietly overriding it.
