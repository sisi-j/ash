# Microsoft authentication only, no offline mode

The launcher authenticates through the Microsoft/Xbox OAuth device-code flow and refuses to launch without a valid Minecraft entitlement. Offline and cracked-account paths are not implemented, not scaffolded, and not accepted as contributions.

## Consequences

- Launching requires network access on first sign-in per account; cached refresh tokens cover subsequent launches.
- Third-party access to the Minecraft Services API is gated behind a **Mojang-run manual allow-list**, announced 2023-05-30, with applications predating it grandfathered in. A fresh Azure app registration completes the Microsoft, Xbox Live and XSTS hops and is then refused at Minecraft Services with `403 Invalid app registration`.
- **No eligibility criteria, turnaround time or SLA is published anywhere, and there is no public record of any applicant's outcome.** Treat the wait as unbounded and outside the project's control. Submit the request before writing auth code — it is the longest-lead item in the project.
- This is an EULA and distribution-survival constraint, not a preference.

See `docs/research/0001-minecraft-launcher-api-access.md` for sources, the full auth chain, and what is verified versus reverse-engineered.
