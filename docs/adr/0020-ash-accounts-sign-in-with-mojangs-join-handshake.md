# ash accounts sign in with Mojang's join handshake

> **Amended 2026-10-23: the join handshake is replaced by the player's certificate** (research 0013). Mojang's firewall refuses every request from Cloudflare Workers, so the backend's `hasJoined` call could never succeed. A player now proves who they are with the key Mojang certifies for signed chat. The launcher sends Mojang's certificate for that key, and ash's challenge signed with it, and the backend checks both offline against Mojang's published keys. The aims below still hold, and the "Amendment" section says what changed.

A player proves to ash's backend which Minecraft profile they are the way they prove it to any Minecraft server. The backend issues a one-time challenge, the player's machine "joins" it at Mojang's session server, and the backend asks Mojang whether that profile has joined. If Mojang says yes, the backend issues an ash session token. The player's Minecraft and Microsoft tokens never leave their machine.

## Context

Phase 4 needs the backend to know a request comes from the owner of a Minecraft profile UUID, the ash account's key (ADR-0009). The other two ways considered were:
- **Sending the Minecraft token to the backend,** which checks it with Mojang. The backend would then hold a credential that can play as the player, and a breach of ash would be a breach of their game.
- **A separate ash email and password.** This is a second identity to secure and recover, and it says nothing about which Minecraft profile is behind it.

## Decision

- **The handshake is the only sign-in.** The launcher runs it after every Microsoft sign-in. The client runs its own in game, with the token the game already holds for the session, so the launcher never hands the game an ash token.
- **The client derives the server id; the backend never chooses it.** *(Added 2026-10-09 by research 0010.)* The client joins Minecraft's digest of `ash-account-sign-in:` followed by the challenge, and the backend computes the same digest to check. A backend that could choose the server id could hand over a real server's login hash, and log in to that server as the player. That's the hole found in Feather's version of this handshake in 2023.
- **`hasJoined`'s `ip` parameter isn't used.** The join goes to Mojang directly and the sign-in goes through Cloudflare, possibly over different address families. The single-use challenge does the binding instead.
- **A challenge is single-use and short-lived.** A challenge that was never issued, or that was already used, is refused.
- **Mojang being unreachable is its own error.** It isn't "not signed in", and it never stops a launch.
- **Reading public data needs no sign-in.** That covers another player's equipped cosmetics and published news, both rate-limited.

## Consequences

- ash's servers hold no credential that can play as anyone.
- A player whose Microsoft account has multiplayer disabled may fail the join. *(Unverified: spec 0004, Further Notes item 1.)* That player gets no ash account, and still plays.
- Sign-in depends on Mojang's session server being up, as joining a server does. It isn't needed for playing.

## Amendment, 2026-10-23: the player's certificate

**Why.** From the deployed Worker, Mojang's `hasJoined` answers 403 "Service unavailable", while the same request from a home connection answers normally (research 0013 §1). Nothing on Cloudflare can ask Mojang anything, so the join handshake could never sign anyone in.

**The new decision.**
- **The proof is the player's certified key.** The launcher gets the player's certificate from Mojang (`/player/certificates`, the call the game makes at every launch), and signs `ash-account-sign-in:` followed by the challenge with that key. The backend checks the challenge, the key's expiry, Mojang's signature over the UUID, expiry and key, and the player's signature over the challenge, in that order (research 0013 §3).
- **The backend never calls Mojang.** Mojang's player-certificate keys are bundled with it, because it can't fetch them either (research 0013 §4).
- **The username is the player's word.** The certificate names a UUID only. The name is kept for display; nothing is keyed or decided on it.

**What still holds.** The handshake is the only sign-in. A challenge is single-use and short-lived. No Minecraft or Microsoft token reaches ash: the token goes to Mojang, for the certificate. Reading public data needs no sign-in.

**What no longer applies.** The server-id derivation and the `ip` parameter: there's no join. "Mojang being unreachable" as a backend error: the backend no longer asks Mojang. The launcher getting the certificate can still fail, and that's an error on the player's side, retried in the background.

**Consequences.**
- The client's own in-game sign-in, when it comes, uses the key the game already holds.
- A player whose account has multiplayer disabled may get no certificate, as they may have been refused the join. They get no ash account, and still play.
- If Mojang changes its certificate keys, sign-ins fail with `certificate_invalid` until the bundled keys are fetched again from a home connection and redeployed.
