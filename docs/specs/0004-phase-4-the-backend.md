# Phase 4 — The backend: ash accounts, synced settings, news and free cosmetics

*Designed 2026-10-09 in an interview with the product owner. Every decision below was put to them and answered; the few written down as assumptions are marked.*

## Problem Statement

Phases 1 to 3 built everything that runs on the player's machine. ash has a launcher, a client on two version targets and a feature set to compare with Lunar's, but nothing that lives beyond one computer:
- **A player's setup stays on one machine.** Their crosshair, readouts, launcher preferences, instances and server lists have to be made again on every computer they play on.
- **ash can't tell players anything.** The launcher's news page says "coming soon", and nothing reaches a player between releases.
- **ash has no account of its own.** The glossary has defined an ash account, entitlements and cosmetics since Phase 1, but nothing implements them. Cosmetics are the part of a PvP client that other players see, and ash has none.

The money question that ADR-0010 accepted as a risk got sharper in Phase 2. Mojang's guidelines now name capes, ash's headline cosmetic, as the one cosmetic not to sell (research 0002 §6). The publisher is still an individual, and no lawyer has read any of it.

## Solution

Phase 4 builds **the backend** and the launcher and client work that uses it. It covers four areas:
- **ash accounts.** An account is created when a player first signs in to Microsoft in the launcher, and is keyed by their Minecraft profile UUID (ADR-0009). The player proves who they are with Mojang's own join handshake, the one a Minecraft server uses, so their Minecraft token never reaches ash's backend (ADR-0020). They can delete the account from the launcher, and an account with no sign-in for 12 months is deleted automatically.
- **Synced settings**, on by default when signed in. A player's feature settings, launcher preferences, instance definitions and server entries follow them between machines. Machine-local overrides never do (ADR-0011).
- **News posts.** They're written on an admin page and shown in the launcher's News page.
- **Cosmetics, free.** The first catalogue has capes, worn models and emotes. Every ash account is entitled to the whole catalogue. Nothing is sold in this phase, and capes are never sold (ADR-0010, amended). Other ash players see a player's cosmetics, and any player can hide other people's.

The backend runs on Cloudflare Workers, written in TypeScript, in its own public repository (ADR-0021). If the backend can't be reached, play, launching and settings work exactly as they do today; only cosmetics, sync and news stop (ADR-0010, ADR-0017).

## User Stories

### ash accounts

1. As a player, I want an ash account made for me when I sign in, with a line telling me so, so that I get sync and cosmetics without a second sign-up.
2. As a player, I want ash never to hold my Minecraft or Microsoft credentials on its servers, so that a breach of ash can't let anyone play as me.
3. As a player, I want to delete my ash account from the launcher, and have everything ash's servers hold about me go with it, so that leaving ash is complete.
4. As a player who stops using ash, I want my account deleted on its own after a year, so that my data doesn't outlive my interest.
5. As a player, I want ash to work fully for playing when its servers are down or unreachable, so that ash's backend can never stop me playing.

### Synced settings

6. As a player, I want my feature settings — crosshair, readouts, hit indicator and the rest — to be the same on every computer I sign in on, so that my aim doesn't change when I change machines.
7. As a player, I want one set of feature settings across all my instances, so that changing my crosshair in one instance changes it in all of them.
8. As a player, I want my launcher preferences to follow me, so that ash behaves the same everywhere.
9. As a player, I want my instances to appear on a new computer, ready to download, so that I don't recreate them by hand. My worlds and mods stay where they are.
10. As a player, I want each instance's server list to follow me, in my order, so that my servers are there on any machine.
11. As a player, I want memory, Java path, window size and third-party mods to stay on the machine I set them on, so that a setting from my desktop never breaks my laptop.
12. As a player, I want a change I make on one machine to win over an older one from another, setting by setting, so that two machines never undo each other's unrelated changes.
13. As a player, I want to be asked before an instance deleted on another computer is deleted here, so that my worlds are never deleted silently.
14. As a player, I want to turn sync off, so that a machine can keep its own setup.

### News

15. As a player, I want news and changelogs in the launcher's News page, so that I know what changed and what's coming.
16. As the product owner, I want to write, edit and publish posts on a web page, so that I can post without a release.

### Cosmetics

17. As a player, I want to choose a cape, something to wear on my head and something on my back, so that my character looks like mine.
18. As a player, I want to see my choice on my own skin in 3D in the launcher before I play, so that I know what I'm wearing.
19. As a player, I want to change what I wear in game too, so that I can see it in third person without leaving.
20. As a player, I want other ash players to see my cosmetics, and to see theirs, so that cosmetics mean something.
21. As a player, I want my ash cape to replace my Mojang cape while I wear it, and my Mojang cape back when I take it off, so that I'm never wearing two.
22. As a player, I want to put up to 8 emotes on a wheel and play one by holding a key, so that emoting is quick.
23. As a player, I want an emote to show me in third person while it plays and to stop as soon as I move, attack or am hurt, so that it never gets in the way of a fight.
24. As a player in a fight, I want to hide other players' cosmetics, so that nothing distracts me from hitboxes.
25. As a player, I want cosmetics to look the same on 1.8.9 and 1.21.11, so that I'm the same player on both. *(Assumption, from the principle every Phase 3 feature follows.)*
26. As a player, I want ash's servers never to keep a record of who I was near or what server I was on, so that showing cosmetics doesn't track me.

### Building and testing

27. As a developer, I want the backend's every endpoint tested against a local Workers runtime in CI, so that a change can't break the API unseen.
28. As a developer, I want the launcher and client to talk to a fake backend in their tests, so that their tests stay fast and don't need the network.
29. As a developer, I want a staging backend beside production, so that a change is tried on a real Cloudflare deployment before players get it.
30. As a developer, I want cosmetics authored in Blockbench and exported to one ash format, so that both version targets draw from the same files.

## Implementation Decisions

### Shape

- **The backend is a separate deployable, in its own public repository** (`sisi-j/ash-backend`), as the glossary has said since Phase 1. It's TypeScript on Cloudflare Workers, with:
  - D1 for accounts, settings and posts;
  - R2 for cosmetic files and news images;
  - Durable Objects for the emote relay's WebSockets.

  ADR-0021 records why.
- **Two deployments:** staging and production, at `api.ashlauncher.com` and a staging host beside it. *(Assumption: the host names.)* CI deploys from the backend repository's own GitHub Actions.
- **The free plan first.** Workers Paid costs $5 a month. ash moves to it only when the free limits get close, and only after the product owner agrees.
- **`Ash` stays the launcher's single inbound seam.** ~~The backend is reached through a new outbound port in ash-core, with a fake for tests, as the Microsoft and Mojang HTTP ports are.~~ The backend is reached over the existing `HttpPort`, at `Config::backend_url`. Tests use `FakeHttp`, serving the backend's own contract examples (*amended 2026-10-09 with #119*).
- **The contract is tested on both sides, not shared as code.** The backend's tests pin each response's shape. ash-core's fake is built from the same examples, so a change to one side fails the other's tests.
- **Severable.** Every Phase 4 piece fails alone. Unreachable or returning errors, the backend costs the player:
  - their cosmetics and other players' cosmetics;
  - sync, which catches up next time;
  - news.

  It never costs them a launch. In the load report's sense, cosmetics and emotes are features (ADR-0017).

### ash accounts and signing in

- **Mojang's join handshake proves identity** (ADR-0020):
  1. The backend issues a one-time challenge.
  2. The player's own machine sends it to Mojang's session server as a "join".
  3. The backend asks Mojang whether that player has joined with that challenge.
  4. If they have, it issues its own ash session token.

  This is what every Minecraft server does at login. The player's Minecraft token stays on their machine.
- **The launcher signs in after every Microsoft sign-in.** It shows the notice that an ash account exists at the first one. ash's session token is stored the way the Microsoft tokens already are.
  - *Amended 2026-10-09 with #119:* it signs in in the background after every Microsoft sign-in, and at startup when the ash session is missing or within a week of ending. It doesn't sign in on every token refresh: a launch must never wait on ash, and Mojang rate-limits joins (research 0010).
  - *Amended 2026-10-09 with #120:* deleting the ash account stops background sign-ins on that machine. Signing in to Microsoft again makes a fresh ash account, and says so again.
- **The game signs in itself.** The client holds the player's Minecraft token for the session already, so it does its own handshake for what needs sign-in in game: changing what it wears, and sending emotes. The launcher never hands the game an ash token.
- **Looking up another player's cosmetics needs no sign-in.** A player's equipped cosmetics are public by UUID, with rate limits.
- **Deletion is total:**
  - the launcher's "Delete ash account" removes the account, its settings and its equipped cosmetics at once;
  - a scheduled job deletes any account with no sign-in for 12 months, which covers players who uninstall without saying so (Microsoft's terms, research 0001 §5).

  The next sign-in after a deletion makes a fresh, empty account.

### Synced settings

- **What syncs:**
  - feature settings, kept per account rather than per instance;
  - launcher preferences;
  - instance definitions;
  - each instance's server entries.

  Machine-local overrides can't sync, because no synced type can hold one (ADR-0011).
- **Each setting keeps its own time, and the latest change wins** (ADR-0022). A server list is one setting, since its order is part of it. Server icons aren't synced; the game fetches them again.
- **Only the launcher syncs, and only while the game is closed.**
  - Before a launch, it applies remote changes to the instance's client settings, `config/ash.properties`, and to `servers.dat`.
  - After the session, it collects what the player changed in game and uploads it.
  - The client stays the only writer of its settings file while the game runs.
  - The launcher writes `ash.properties` on the same terms ADR-0019 set for `servers.dat`: game closed, unknown keys and comments kept, and only the changed values touched.
- **Feature settings are per account,** so a change in one instance is copied to the player's other instances on the same machine. Settings that exist only on 1.8.9 ride along unused on 1.21.11.
- **Instance definitions** create the instance on another machine, ready to prepare. Worlds, mods, screenshots and machine-local overrides stay behind.
- **A deletion from another machine asks:** "X was deleted on another computer". *Delete here* removes it, worlds included. *Keep here* makes it local to this machine and stops syncing it.
- **Sync is on when signed in,** with a switch in Settings. Turning it off stops sending and receiving, and keeps what's on this machine.

### News

- Posts are written, edited and published on an admin page served by the backend at `/admin`. Cloudflare Access protects it, allowing only the product owner's email, so ash has no password code of its own.
- The launcher's News page reads published posts from a public endpoint and caches the last copy for when it's offline.

### Cosmetics

- **Free, all of them, to everyone** (ADR-0010, amended). Entitlements are built as real per-account grants, so that whatever the pre-launch phase decides about granting needs no new model. Nothing is sold, and capes are never sold.
- **Slots:**
  - one cape;
  - one item on the head;
  - one item on the back;
  - up to 8 emotes on the wheel.

  An equipped cosmetic requires an entitlement.
- **An equipped ash cape replaces the player's Mojang cape** for ash players who see it. With no ash cape, the Mojang cape shows as it does today.
- **Seeing others' cosmetics:**
  - ~~the client looks up the UUIDs of the players it can see, in batches;~~ each account's equipped cosmetics are a static file, `profiles/<uuid>.json` in R2, read by the client through an edge-cached public domain (*amended 2026-10-09 by research 0010*);
  - ~~the backend answers from an edge cache and logs nothing about who asked for whom.~~ a lookup never reaches ash's code, so there is nothing to log.

  ADR-0023 records the no-record rule.
- **Hiding others' cosmetics** is an option on the client's Cosmetics feature. It's off by default, so other players' cosmetics show unless the player turns it on.
- **Models have no size limit.** The product owner chose hiding over a size rule.
- **Emotes:**
  - a vanilla server doesn't relay ash's messages, so the backend does;
  - each signed-in client keeps one WebSocket open and subscribes to the players it can currently see;
  - when a player starts an emote, the backend sends it to that player's subscribers only;
  - the backend never learns which server anyone is on, and keeps nothing;
  - the same socket carries live equip changes.
- **Playing an emote:**
  - holding B, which is rebindable, opens a wheel, and letting go on an emote plays it;
  - the player's own view goes to third person and comes back when it ends;
  - it stops on move, attack or hurt;
  - it's purely visual, and the server sees nothing.
- **Equipping happens in two places:**
  - the launcher's **Wardrobe** page, with a 3D preview on the player's own skin;
  - an equip page in the client's settings panel.

  "Store" stays reserved for a paid storefront that doesn't exist.
- **Art** is made in Blockbench (models, textures and animations) and exported to a small ash format that both version targets draw. The client downloads it from R2 and keeps it in a cache. Simple placeholders are made for building and testing; the real art is the product owner's or commissioned.
- **Cosmetics look identical on 1.8.9 and 1.21.11.** *(Assumption.)*

### Legal surface

- **ash-site's privacy policy and terms are updated before each piece goes live:**
  - accounts and their deletion before sign-in ships;
  - cosmetic lookups and the emote relay before either ships.
- **Before any money is taken:** the publisher incorporates and a lawyer reads the cosmetics position (ADR-0010).

## Testing Decisions

The bar is Phase 3's: a test states a fact a player or the product owner would recognise, drives the product through a seam, and can't pass for the wrong reason.

**Backend.** Every endpoint is tested in a local Workers runtime in CI, with real D1 and Durable Objects from the local runtime, never mocks of them. What needs covering:
- the handshake:
  - a challenge that was never issued is refused, and so is one used twice;
  - a "has joined" that Mojang denies issues no session;
  - Mojang being unreachable is its own error, not "not signed in";
- deletion leaves no row for the account in any table, and the 12-month job deletes exactly the stale accounts;
- the latest change wins setting by setting, and an older write never replaces a newer one;
- equipping without an entitlement is refused;
- the emote relay reaches subscribers only, and stores nothing;
- the cosmetics lookup writes nothing anywhere;
- `/admin` refuses a request without Cloudflare Access's signed header.

**Launcher.** `Ash` is driven against a fake backend. What needs covering:
- sign-in creates the session, and the notice shows once;
- the backend being down never fails a launch;
- remote settings are applied to `ash.properties` and `servers.dat` only while the game is closed, with every unknown byte kept (prior art: ADR-0019's golden files);
- a change made in game is collected after the session;
- feature settings are copied across the player's local instances;
- a deletion from elsewhere asks first;
- machine-local overrides never leave the machine (ADR-0011's tests, extended to the sync payload).

**Launcher UI.** The ui-check renders the Wardrobe, the News page, the sign-in notice, the sync switch and the "deleted on another computer" question against the fake API.

**Client.** The real-game tests on both targets, against a local fake backend. What needs covering:
- a cape and a worn model are drawn on a player, with a screenshot;
- an ash cape replaces a Mojang cape;
- "hide others' cosmetics" hides them;
- an emote plays and stops on movement;
- with the backend unreachable, the game loads and the load report names cosmetics as degraded.

**Manual acceptance, not automated.** On a real Windows machine with a real account, and with a second machine or a fresh install:
- settings, instances and servers follow the account;
- a deletion asks;
- a news post published from `/admin` shows in the launcher;
- a cape, a hat and an emote are seen by a second ash client on a local server;
- deleting the account removes it.

## Out of Scope

- Selling anything: payments, a storefront, a wallet currency. Granting is revisited in the phase that prepares for launch.
- Stats. Nothing defines them, and anything the client reports about PvP can be forged.
- Cosmetics on vanilla instances, which don't run the client.
- A size limit for worn models.
- Syncing worlds, mods, screenshots or machine-local overrides.
- Server icons in synced server lists.
- Serving freelook's block list from the backend. It stays in the client.
- macOS (Phase 5).

## Further Notes

- **Unverified, and to be settled by research before the tickets that depend on them:**
  1. ~~**The join handshake from a launcher rather than a game connection:**~~ *Answered 2026-10-09 in `docs/research/0010-the-join-handshake-and-cloudflares-free-plan.md`, except the live check, which the product owner runs with `examples/join-handshake.rs`.* The client derives the server id, so the backend can never choose one (ADR-0020, amended). What it covered:
     - that Mojang's session server accepts a join with ash's own challenge;
     - its rate limits;
     - its behaviour for a player whose multiplayer is disabled on their Microsoft account;
     - whether anything in Mojang's or Microsoft's terms bears on it.

     Blocks the sign-in tickets.
  2. ~~**Cloudflare's free-plan limits for this shape:**~~ *Answered 2026-10-09 in research 0010:* one to a hundred players fit comfortably, once cosmetics lookups are static files rather than Worker requests. What it covered: D1, Durable Objects with WebSocket hibernation, cron triggers and R2. It also covers Cloudflare Access's signed header for `/admin`, and testing Workers locally. Blocks the backend skeleton's choices.
  3. **Drawing cosmetics on each target:**
     - where each draws the cape, how to attach models to the head and body, and how to pose the player model for an emote;
     - whether Sodium touches any of it.

     It needs a pass through both mapped jars like research 0004's. Blocks the cosmetics tickets.
  4. **The Blockbench export:** what to take from its format, and the licence position of exported files. Blocks the cosmetics format.
  5. **The launcher's 3D preview:** a renderer such as skinview3d, its licence, and fetching the player's own skin texture under the launcher's content security policy. Blocks the Wardrobe.
  6. **What an instance definition holds today,** and whether instance ids can collide between machines. Blocks instance sync.
- **Order**, as the product owner chose:
  1. the foundation: accounts, the handshake and deletion;
  2. news;
  3. synced settings;
  4. capes, which prove the cosmetics pipeline end to end;
  5. worn models;
  6. emotes.

  Each step is usable before the next one starts.
- **Spending.** The backend starts on Cloudflare's free plan. Anything paid is put to the product owner first.
- **Phase 3 isn't formally closed:** #30 awaits manual acceptance. Phase 4's foundation doesn't touch the client, so it can start alongside.

## Tracker

Filed as [#117](https://github.com/sisi-j/ash/issues/117), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. Its step 1 tickets are sub-issues of it, across this repository and [`sisi-j/ash-backend`](https://github.com/sisi-j/ash-backend), with blocking links set. This file is the source of truth; the issue is the tracker entry. Keep them in step if either changes.
