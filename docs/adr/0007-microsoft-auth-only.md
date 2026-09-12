# Microsoft authentication only, no offline mode

The launcher authenticates through the Microsoft/Xbox OAuth device-code flow and refuses to launch without a valid Minecraft entitlement. Offline and cracked-account paths are not implemented, not scaffolded, and not accepted as contributions.

## Consequences

- Launching requires network access on first sign-in per account; cached refresh tokens cover subsequent launches.
- Third-party access to the Minecraft Services API is gated behind a **Mojang-run manual allow-list**, announced 2023-05-30, with applications predating it grandfathered in. A fresh Azure app registration completes the Microsoft, Xbox Live and XSTS hops and is then refused at Minecraft Services with `403 Invalid app registration`.
- The application form publishes a **weekly review cadence** and states that resubmitting does not speed it up. No SLA, no acknowledgement, and no public record of any applicant's outcome. Request submitted 2026-09-10; **approved 2026-09-12**, verified by completing all seven hops against the live services rather than by reading the notification — which was a generic batch email that confirmed nothing about any particular applicant.
- **The allow list is a granted permission, not a property of the code.** It can be withdrawn, and the same 403 that once meant “never approved” would then mean “no longer approved” — indistinguishable from outside. `launcher/core/examples/allow-list.rs` re-checks it on demand, and `AshError::NotAllowListed` stays in the error set permanently.
- The form also states outright that **"any applications that bypass security/auth/license checks or disable safety features will not be approved."** This decision is therefore a condition of approval, not merely a principle — an offline path would disqualify ash from the API it needs to function at all.
- This is an EULA and distribution-survival constraint, not a preference.

See `docs/research/0001-minecraft-launcher-api-access.md` for sources, the full auth chain, and what is verified versus reverse-engineered.
