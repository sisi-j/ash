# Research 0010 — The join handshake from a launcher, and Cloudflare's free plan for ash's backend

*2026-10-09, for #118. Settles spec 0004's Further Notes items 1 and 2.* Sources are tagged as in research 0001:
- **[DOC]** is the vendor's own documentation;
- **[PRACTICE]** is what a shipping product observably does;
- **[COMMUNITY]** is a third party's description, unconfirmed;
- **[ASH]** is ash's own code and tests.

## Summary

- **The handshake is established practice, and it has a known hole that ADR-0020 must close.**
  - Feather Client signs players in to its backend exactly this way [PRACTICE].
  - In 2023, researchers showed the hole: Feather's servers chose the `serverId` the client joined. They could hand over a real server's login hash, Hypixel's for example, and log in to that server as the player [COMMUNITY].
  - So ash's client never joins a `serverId` the backend supplies. It joins Minecraft's digest of `ash-account-sign-in:` followed by the backend's challenge, and the backend computes the same digest to check. ADR-0020 is amended to say so, and ash-core now does it (`Ash::join_for_ash`) [ASH].
- **The endpoints and their errors are documented well enough to build on.**
  - `join` is a JSON POST answered with 204.
  - `hasJoined` is a GET answered with 200 and the profile, or 204.
  - A refused join names its reason. `InsufficientPrivilegesException` means multiplayer is disabled on the Microsoft account; `UserBannedException` means banned [COMMUNITY].
- **Proved live on 2026-10-10** with `cargo run -p ash-core --example join-handshake`: the session server accepts a join made from a launcher with ash's own `serverId`, and confirms it (see §1, "The live check"). How long a join stays confirmable wasn't measured. That doesn't matter for ash, since its sign-in redeems within seconds.
- **Cloudflare's free plan comfortably covers one to a hundred players**, if the lookup of other players' equipped cosmetics is served as cached static files rather than by the Worker. That also makes ADR-0023's "no record" structural, not a matter of discipline.

## 1. The join handshake

### The two endpoints [COMMUNITY: the protocol wiki, minecraft.wiki "Java Edition protocol/Encryption"]

**Join**, sent by the player's machine:
- `POST https://sessionserver.mojang.com/session/minecraft/join`, with `Content-Type: application/json` (without it, the answer is 415 or 403).
- The body is `{"accessToken": <Minecraft token>, "selectedProfile": <undashed UUID>, "serverId": <hash>}`.
- Success is `204 No Content`.

**HasJoined**, sent by the server, which here is ash's backend:
- `GET https://sessionserver.mojang.com/session/minecraft/hasJoined?username=<name>&serverId=<hash>[&ip=<ip>]`.
- `200` returns `{"id", "name", "properties": [textures…]}`. Not joined returns `204`.

**The `serverId` encoding:**
- In a real login it's the SHA-1 of the server's id string, then the shared secret, then the server's public key.
- It's printed as Minecraft's "hexdigest": the 20 bytes read as one signed big-endian number, in lowercase hex without leading zeros, with a minus sign when negative.

ash's implementation is checked against the three published examples (`Notch`, `jeb_` and `simon`) and against an independent Python computation [ASH].

**Errors** [COMMUNITY]:
- `InsufficientPrivilegesException`: the player's Xbox multiplayer setting is off. Child accounts default to this.
- `UserBannedException`: banned from multiplayer.
- A plain 403: a malformed request.

ash-core maps the first two to `multiplayer_disabled` and `multiplayer_banned`. Neither is retryable, and each says playing is unaffected [ASH].

### Precedent, and the hole [PRACTICE + COMMUNITY]

xyzeva's "FeatherMC Concerns" (28 November 2023) describes Feather's sign-in:

> "Feather takes advantage of this by making the client send a request to Mojang with a Server ID Feather knows and provides, and then the client sends a request back to Feather, saying that the client has done it, along with the username of who they are claiming to be."

It also describes the flaw:

> "feather's servers completely have control of the Server ID the client joins, meaning they can spoof a server they want to join as the user (eg. hypixel.net)'s server ID."

Here's the attack in full. The backend, or whoever controls it, opens a real login to a real server and picks the shared secret, as a client does. It computes that login's `serverId` and hands it to the player's client as a "challenge". The client joins it. The attacker's connection then passes the real server's `hasJoined` as that player.

**ash's rule:** the client joins `hexdigest(SHA-1("ash-account-sign-in:" + challenge))`, never the challenge itself.
- A real login's hash starts from 16 bytes of shared secret, followed by a public key the server fixes.
- For a backend to make ash's hash equal a real one, it would need a SHA-1 collision between a prefix it chose and one where nothing can follow the attacker's bytes but the server's own key.
- SHA-1's known chosen-prefix collisions need free blocks appended after both prefixes, and a real login hash has none to give.

The rule is in ADR-0020 (amended) and in `launcher/core/src/session_server.rs` [ASH].

### Rate limits [COMMUNITY, weak]

- **Mojang publishes no rate limits** for the session server. Research 0001 §5 found the same for `api.minecraftservices.com`.
- An aggregator (apis.io, "Mojang · Rate Limits") claims:
  - roughly 400 requests per 10 seconds per IP across the session server;
  - a stricter per-account quota on joins;
  - 429 as the throttle.

  It cites no source. Treat it as order-of-magnitude only.
- **Consequences for ash:**
  - one join per sign-in, not per request;
  - the backend's `hasJoined` calls come from Cloudflare's shared egress, so 429 from Mojang must be its own retryable error (`mojang_unavailable`), never "not signed in";
  - the backend keeps its own session token for days, so the handshake runs once per launcher start or game start, not per call.

### The `ip` parameter: not used

`hasJoined` can also check that the join came from a given IP. ash doesn't pass it:
- The player's join goes to Mojang directly.
- Their request to ash goes through Cloudflare.
- The two can leave the machine over different address families (IPv4 to Mojang, IPv6 to Cloudflare), and a mismatch would refuse a real player.

The challenge being single-use and short-lived does the binding instead.

### The live check [PRACTICE: the product owner's run, 2026-10-10]

`cargo run -p ash-core --example join-handshake`, with a fresh device-code sign-in held in memory:

```
Signed in as oinkr.
Joined server id -e69e43fe5590a5d95d9f13e520cada782a72385.
hasJoined, a server id never joined:   204, not joined
hasJoined, ash's server id, at   1s:  200, confirmed this profile
```

- **Settled:** Mojang's session server accepts a join from a launcher, of a server id derived from ash's own challenge, and `hasJoined` confirms it for that profile. A server id nobody joined comes back 204, so the "yes" is meaningful. The negative digest (`-e69e...`) went through as Minecraft's own logins send it.
- **Not measured:** the run was stopped after the 1-second check, so how long Mojang keeps a join confirmable is still unknown.
- **What that leaves:** in ash the sign-in redeems its challenge within a second or two of the join, so the backend's 30-second challenge lifetime (`CHALLENGE_LIFETIME_MS`) stays. It matters only if Mojang forgets a join in under about 30 seconds. If a real sign-in ever comes back `not_joined` after a successful join, rerun the example to the end and shorten the lifetime below what it shows.

### Terms

Nothing in the Microsoft identity platform terms, Mojang's EULA or the Usage Guidelines (research 0001 §5, §7 and §8) addresses the session server specifically. The clauses that bear on it:
- **§1.2.5 (minimum data)** is met. The join sends Mojang the token Mojang issued, and nothing else.
- **§1.2.4 (no copies beyond the intended use)** is met. The backend keeps the UUID and name `hasJoined` returns, which are the account key (ADR-0009), and never the textures.
- **§2.2 (deletion)** is spec 0004's 12-month rule.

Using the session server for a service's own sign-in is long-standing practice: Feather's backend, and the many server websites that verify players this way. Nothing found says Mojang objects. As with everything here, that is evidence of tolerance, not permission (ADR-0010).

## 2. Cloudflare's free plan, for this shape

### The limits that bind [DOC, developers.cloudflare.com, read 2026-10-09]

| Product | Free allowance | What happens over it |
| --- | --- | --- |
| Workers | 100,000 requests/day; **10 ms CPU per request**; 50 subrequests per request; 128 MB memory | further requests error until 00:00 UTC |
| Cron triggers | 5 per account | — |
| D1 | 10 databases; 500 MB each; 5 GB in total; 5 million rows read and 100,000 rows written per day; 50 queries per invocation; 7 days of Time Travel | queries error until the reset |
| Durable Objects | SQLite-backed only; 100,000 requests/day; 13,000 GB-s/day; 5 million rows read and 100,000 written per day; 5 GB | further operations error |
| WebSockets on DOs | incoming messages billed 20:1 as requests; outgoing free; a hibernated object isn't billed for duration | — |
| R2 | 10 GB-month; 1 million Class A and 10 million Class B operations per month; egress free | — |
| Access (Zero Trust) | free for up to 50 users | — |

### Headroom, from one to a hundred players

Rough figures per active player per day:
- the handshake: 2–4 Worker requests;
- sync: about 10 requests and a few dozen D1 row writes;
- news: 1–2 requests.

At 100 players that's about 1,500 Worker requests and a few thousand D1 writes, roughly 2% of the allowances.

**The cosmetics lookup is what could use them up.** A client asks about every player it sees, and a busy server shows hundreds an hour. Served by the Worker, 100 players could pass 100,000 requests a day.

**The recommendation:**
- Publish each account's equipped cosmetics as a small static file, `profiles/<uuid>.json` in R2. It's written when the player equips something and deleted with the account.
- Clients read it through R2's public custom domain with an edge cache.
- Cached reads never invoke the Worker or count against its allowance.
- The Worker never sees who looked up whom, so ADR-0023 holds by construction.
- A player with no file has no ash cosmetics, which is also how a non-ash player looks.

That replaces the spec's "batched lookups" with per-player cached files. The trade is more, smaller requests from the client, each answered at the edge.

**The emote relay** fits easily:
- A connection and its subscription changes are a few DO requests.
- Emotes are incoming messages at 20:1.
- Hibernation means an idle socket costs no duration.

**The 10 ms CPU limit** is per request and counts only computation, not waiting on `fetch`:
- verifying an Access JWT (RS256 through WebCrypto), hashing a challenge and a few D1 queries all fit;
- nothing in the step 1 design is CPU-heavy;
- image processing (news uploads) belongs in the browser, before upload.

### Cloudflare Access for `/admin` [DOC]

- Access sends a signed JWT in the `Cf-Access-Jwt-Assertion` header.
- The Worker verifies it against the keys at `https://<team>.cloudflareaccess.com/cdn-cgi/access/certs`, and checks:
  - `iss` is the team domain;
  - `aud` is the application's AUD tag.
- The keys rotate every six weeks, and the old ones stay valid for seven days. Cloudflare's Workers example uses `jose` with a remote JWKS.
- Verifying in the Worker as well as Access protecting the route means a misconfigured Access policy fails closed.

### Testing locally [DOC]

- `@cloudflare/vitest-plugin`, with the `cloudflareTest()` plugin, needs Vitest 4.1 or later. Tests run inside workerd through Miniflare, with real local bindings and storage isolated per test file.
- D1 migrations: `readD1Migrations()` (Node side, from `@cloudflare/vitest-plugin/config`) feeds `applyD1Migrations(db, migrations)` in the test setup.
- Durable Objects can be driven with `runInDurableObject`, and the Worker through `exports.default.fetch()`. Unlike the former `SELF`, `exports` doesn't expose static assets.
- **Faking Mojang:** the pages read don't document mocking outbound `fetch`. ash shouldn't need it: the backend takes its Mojang client as a parameter, as ash-core takes `HttpPort`, and tests pass a fake. That keeps tests off the network by construction, which is the rule the Rust side already follows.

### Deploying, and what the product owner creates by hand

- **Wrangler environments** `staging` and `production`, each with its own D1 database and R2 bucket, deployed from GitHub Actions with `cloudflare/wrangler-action`.
- **By hand, once:**
  1. A Cloudflare API token, scoped to this account:
     - Workers Scripts: Edit;
     - D1: Edit;
     - Workers R2 Storage: Edit;
     - Account Settings: Read;
     - on the `ashlauncher.com` zone, Workers Routes: Edit.
  2. That token and the account id stored as Actions secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` in `sisi-j/ash-backend`.
  3. A Zero Trust team (free). Its name is the `<team>` above.
  4. An Access application for the admin path, allowing the product owner's email, with its AUD tag stored as a Worker variable.
  5. Confirmation of the host names: `api.ashlauncher.com`, a staging host, and a public R2 domain for profiles and cosmetic files, such as `cdn.ashlauncher.com`.

## What changed because of this

- **ADR-0020, amended:** the client derives the `serverId`; the `ip` parameter isn't used.
- **ADR-0023 and spec 0004, amended:** equipped cosmetics are served as static, edge-cached files, not by a Worker endpoint.
- **ash-core** gains `Ash::join_for_ash`, the `serverId` derivation, and the `multiplayer_disabled` and `multiplayer_banned` errors, with fixture tests.
- **New:** `examples/join-handshake.rs`, for the one live check.

## Sources

- [Protocol encryption, minecraft.wiki](https://minecraft.wiki/w/Java_Edition_protocol/Encryption) (formerly wiki.vg).
- [FeatherMC Concerns](https://blog.mmpa.info/posts/feathermc-concerns/), xyzeva, 2023-11-28.
- [Mojang · Rate Limits, apis.io](https://apis.io/rate-limits/mojang/mojang-rate-limits/), unsourced aggregator.
- Cloudflare documentation:
  - [Workers limits](https://developers.cloudflare.com/workers/platform/limits/);
  - [D1 limits](https://developers.cloudflare.com/d1/platform/limits/);
  - [D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/);
  - [Durable Objects pricing](https://developers.cloudflare.com/durable-objects/platform/pricing/);
  - [R2 pricing](https://developers.cloudflare.com/r2/pricing/);
  - [validating Access JWTs](https://developers.cloudflare.com/cloudflare-one/identity/authorization-cookie/validating-json/);
  - [Vitest integration](https://developers.cloudflare.com/workers/testing/vitest-integration/write-your-first-test/);
  - [test APIs](https://developers.cloudflare.com/workers/testing/vitest-integration/test-apis/).
- [Cloudflare Zero Trust free plan, 50 users](https://blog.cloudflare.com/teams-plans).
- Research 0001 §5, §7, §8 and §11.
