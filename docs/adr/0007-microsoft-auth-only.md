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

## Amendment (2026-10-25): signing in through the browser

The alpha's testers sign in with the **authorization-code flow with PKCE** in their own browser, the code coming back to a one-off listener on `http://localhost:<port>`. The device code stays available as **Use a code instead**.

- **Why:** with a device code, the player reads or pastes a code into a page. Filling it in through the link (`microsoft.com/link?otc=CODE`) was tried, and Microsoft refused it: "The application is a first party application, the user does not have consent… invalid_request". Pasting the code works but is slow. In the browser flow, the browser usually knows the account already, so the player only picks it and approves.
- **What it needs:** ash's app registration lists `http://localhost` as a redirect URI under *Mobile and desktop applications*. Microsoft matches loopback redirects on the address and ignores the port. Without that entry, Microsoft refuses the redirect before any sign-in page shows.
- **Unchanged:** the same app registration, so Mojang's allow-list approval above still applies, along with the same scopes and the same Xbox → XSTS → Minecraft chain. Neither flow handles a password.
- **The listener** binds 127.0.0.1 on a port the OS picks. It accepts only the redirect carrying this sign-in's `state`, turns everything else away, and closes as soon as that redirect arrives, the sign-in is cancelled, or ten minutes pass. Only this computer knows the PKCE verifier, so a code taken in transit can't be redeemed.
