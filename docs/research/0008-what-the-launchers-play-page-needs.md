# What the launcher's new Play page needs from the game and the system

Research date: 2026-10-03.

This answers item 10 of Phase 3's Further Notes (`docs/specs/0003-phase-3-the-full-client.md`), added with the final design. The Play page is in the spec's *The launcher's look*.

Every claim below is labelled:

- **[DOC]**: the owner's own published text says this.
- **[PRACTICE]**: observed directly, in the bytes of a mapped game jar or in ash's own code.
- **[COMMUNITY]**: only a secondary source says this. Used as a lead, never as the basis of a finding.

Where this document gives an opinion rather than a source, it says **Judgement**.

---

## Summary

| Need | Finding | Effort |
| --- | --- | --- |
| A server's list of servers | `servers.dat` in the game directory: uncompressed NBT, the same shape on both targets | Small: an NBT reader for three tags |
| "**Recent** servers" | The file has **no** record of when a server was last played. Its order is the player's own | **A decision for the product owner** (below) |
| Status and player count | The game's own server-list ping, which the launcher can send exactly as the game does | Medium: one TCP exchange, with a timeout |
| Join straight into a server | 1.21.11 takes `--quickPlayMultiplayer`; 1.8.9 takes `--server` and `--port`. ash already appends its own arguments for both eras | Small |
| Play time and last session | The launcher already starts the game and watches it exit | Small: two timestamps per instance |
| Its own title bar | Tauri supports it: `decorations: false`, a drag region, and four window permissions | Small, with one Windows limitation |
| The launch sound | Synthesised in the web view at run time, as the approved mockup does | None: no file, so no licence |

## 1. The server list

**Where and what.** Both targets read `servers.dat` from the game directory with `NbtIo.read`, which is uncompressed NBT **[PRACTICE]**:

- 1.21.11: `ServerList.load()` resolves `"servers.dat"`, calls `NbtIo.read(Path)`, and reads the list `"servers"`. Each entry is written by `ServerData.write()` with `"name"`, `"ip"` and `"icon"`, plus newer flags such as `"acceptedCodeOfConduct"`. Entries can also carry `"hidden"`, which keeps a server out of the visible list.
- 1.8.9: `ServerList` (yarn `net.minecraft.client.option.ServerList`) resolves `"servers.dat"`, calls `NbtIo.read(File)`, and reads `"servers"`. Each `ServerInfo` has `"name"`, `"ip"`, `"icon"` and `"acceptTextures"`.

So one reader serves both: a root compound holding a list `servers` of compounds, of which ash needs `name`, `ip`, the optional base64 PNG `icon`, and `hidden`. Hidden entries are skipped.

**The catch: nothing says which servers are recent.** The file keeps the player's own order, which they set by moving entries in the game's multiplayer screen. It holds no join count and no time. A page titled "Recent servers" cannot honestly be filled from it alone.

**Options, for the product owner** (**Judgement** on each):

1. **The ash client records the servers it joins.** On connect, the client appends the address and the time to a small file in the game directory, just as it writes the load report today. The launcher reads it. This is exact, and works for servers joined any way: the list, direct connect, or the launcher's own Join. It needs the "address the player connected with", which spec item 8 is already settling for freelook. Vanilla instances fall back to option 3.
2. **The launcher records only the joins it starts.** Simple, but it misses every server joined from inside the game, which is most of them.
3. **Show the player's own list, in their order, titled "Servers".** No recency at all, but it is exactly what the game shows.

Recommendation: 1, with 3 as the fallback for vanilla instances and for an ash instance that has not joined anything yet.

## 2. Status and player count

The game's multiplayer screen asks each server for its status with the server-list ping. The launcher can send exactly that, so it sends nothing the game would not:

1. a handshake with a protocol version, the address, the port and "next state: status";
2. a status request, answered with JSON carrying the version, `players.online` and `players.max`, the description and an optional favicon;
3. a ping, answered with a pong.

The exchange is described by the community wiki **[COMMUNITY]**. The game's own pinger starts the same way. On 1.8.9, `MultiplayerServerListPinger` builds a `HandshakeC2SPacket` with protocol `47` and `NetworkState.STATUS` **[PRACTICE]**.

- **Protocol version.** By convention -1 asks for status without claiming a version **[COMMUNITY]**. **Judgement:** send the instance's own protocol, as the game does, so a server answers the launcher exactly as it would answer that game.
- **SRV records.** An address without a port may be redirected by a `_minecraft._tcp` SRV record, and the game resolves it **[COMMUNITY]**. The launcher must resolve it too, or a server reached through SRV shows as offline.
- **When.** Only while the Play page is showing, at most once a minute per server, with a short timeout. A ping tells the server the player's IP, as opening the game's server list does. **Judgement:** that is acceptable for servers the player has already played on, and never for anything else.
- **Offline** is a timeout or a refused connection, shown as "Offline", and Join is disabled.

## 3. Joining at launch

- **1.21.11**: `Main` takes `quickPlayMultiplayer`, alongside `quickPlaySingleplayer`, `quickPlayRealms` and `quickPlayPath` **[PRACTICE]**. Mojang's version file gates `--quickPlayMultiplayer ${quickPlayMultiplayer}` behind the `is_quick_play_multiplayer` feature.
- **1.8.9**: `Main` takes `server` and `port` **[PRACTICE]**.
- ash already appends `--width` and `--height` itself on both eras, rather than enabling Mojang's `has_custom_resolution` feature, so that one code path serves both (`launcher/core/src/launch.rs`) **[PRACTICE]**. Joining fits the same pattern:
  - **1.21.11:** `--quickPlayMultiplayer host:port`, which is what the feature would add.
  - **1.8.9:** `--server host --port port`.

  It is a launch-time choice, never stored on the instance.

## 4. Play time and the last session

The launcher starts the game through its process port and watches for it to exit (`ash.game_status`) **[PRACTICE]**. Recording a start and an end time per session in the instance's own metadata gives "Play time" (the sum) and "Last session" (the latest). A session that is still running counts up to now. **Judgement:** the launcher is the right writer, as it already owns `instance.json`. The client knows no more than the launcher does here.

## 5. The launcher's own title bar

- **Turning off the system bar.** `"decorations": false` on the window **[DOC]**.
- **Dragging.** `data-tauri-drag-region` makes an element drag the window. It only works on the element it is applied to, not its children **[DOC]**. Double-click to maximise is a manual `toggleMaximize()` when `e.detail === 2` **[DOC]**.
- **Permissions.** These are needed **[DOC]**:
  - `core:window:allow-close`;
  - `core:window:allow-minimize`;
  - `core:window:allow-toggle-maximize`;
  - `core:window:allow-start-dragging`.

  ash has no capabilities file today **[PRACTICE]**, so this adds the first one.
- **Edges.** `shadow: true` gives an undecorated window a 1px border on Windows, with rounded corners on Windows 11 **[DOC]**.
- **Snapping.** Dragging hands the move to Windows, so drag-to-edge snapping should keep working. A custom maximise button does not get Windows 11's snap-layouts flyout. Both are from issue reports **[COMMUNITY]**, so the ticket's manual test checks them.

*Amended 2026-10-04, while building #64:*
- **Double-click is Tauri's own.** In Tauri 2.11, the drag-region script maximises on a double click by itself (`internal_toggle_maximize`, allowed by `core:window:default`). No manual `toggleMaximize()` is needed, and adding one would maximise twice. `data-tauri-drag-region="deep"` makes a whole bar drag, its text included; its buttons never start a drag **[PRACTICE]**, checked in the real app.
- **The first capabilities file needs `core:default` too.** With no capabilities at all, Tauri rejects every plugin command, `event.listen` among them. So the Play page's progress and launch events never arrived in the real app before #64 **[PRACTICE]**, checked through WebView2 remote debugging with and without the file.

## 6. The launch sound

The approved mockup synthesises its click and start sounds in the browser with the Web Audio API. The launcher's web view is Chromium-based (Spec, *Testing*) and supports the same API. **Judgement:** keep it synthesised. There is no file to ship, nothing to licence, and the sound the product owner approved is exactly the sound players hear. It plays only after a click, which is when the browser allows audio anyway, and it is off when "Launch sounds" is off.

## Sources

- The mapped 1.21.11 and 1.8.9 game jars in Loom's cache, read with `javap`: `ServerList`, `ServerData`/`ServerInfo`, and `net.minecraft.client.main.Main` **[PRACTICE]**.
- `launcher/core/src/launch.rs` and `launcher/src-tauri/` in this repository **[PRACTICE]**.
- Tauri, *Window Customization*, https://v2.tauri.app/learn/window-customization/, and the `WindowConfig` reference, https://docs.rs/tauri-utils/latest/tauri_utils/config/struct.WindowConfig.html **[DOC]**.
- Minecraft Wiki, *Java Edition protocol/Server List Ping*, https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping **[COMMUNITY]**.
