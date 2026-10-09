# The backend runs on Cloudflare Workers, in TypeScript, in its own public repository

## Decision

- **Cloudflare Workers,** with:
  - D1 for accounts, settings and news posts;
  - R2 for cosmetic files and news images;
  - Durable Objects for the emote relay's WebSockets;
  - cron triggers for the 12-month account expiry.
- **TypeScript,** in `sisi-j/ash-backend`, public.
- **Two deployments,** staging and production, each deployed from the backend repository's own CI.
- **The free plan until it's outgrown.** Moving to Workers Paid, $5 a month, is put to the product owner first.

## Why

- ashlauncher.com's DNS is already on Cloudflare. Workers run near the player, which matters for the in-game cosmetics lookup, and leave nothing to patch or operate.
- Azure was considered because the publisher site and the Azure app registration live there. A VPS running Rust was considered because it could share types with ash-core.
- TypeScript is Workers' first-class language and matches the launcher's UI. Rust on Workers compiles to WebAssembly with thinner support.
- Public, like ash: the code holds no secrets, and players can read exactly what a service that knows their UUID does with it.

## Consequences

- **No types are shared with ash-core.** The contract is held by tests on both sides: the backend's tests pin each response's shape, and ash-core's fake backend is built from the same examples.
- **The backend's own repository** has its own CI, and its pull requests are separate from ash's. Phase 4 tickets say which repository they belong to.
- Cloudflare is now a dependency of ash's online features, never of playing (ADR-0010's severability, ADR-0017).
