# ash accounts are keyed by Minecraft profile UUID

The brief said "Microsoft UUID", but the Microsoft account's object id and the Minecraft profile UUID are different identifiers. The Minecraft profile UUID is the key: it is what other players see, what cosmetics render against in game, and what stays stable if Microsoft's auth internals change. The Microsoft token chain becomes an authentication detail rather than an identity.

## Consequences

- Cosmetics can be resolved for any player visible in a world from their profile UUID alone, without knowing anything about their Microsoft account.
- A player who somehow moves their Minecraft profile to a different Microsoft account keeps their ash account, entitlements and stats.
- The backend never stores a Microsoft account identifier as a primary key.
