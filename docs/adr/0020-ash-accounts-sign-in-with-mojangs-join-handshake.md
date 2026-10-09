# ash accounts sign in with Mojang's join handshake

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
