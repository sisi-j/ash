# The game-side hooks Phase 3's features depend on, per version target, and how each was established

Research date: 2026-09-29. Answers item 1 of Phase 3's Further Notes ([#30](https://github.com/sisi-j/ash/issues/30)): the game-side hooks for the crosshair, the hit indicator, the ping readout, hit colour and freelook, on each target.

The method is 0003's. Unless a claim is marked otherwise it is **[PRACTICE]**: read with `javap -c -p` out of the jars Loom produced for this repository's own build, not from a wiki, a mapping browser or another mod. The jars:

- **1.21.11**, Mojang's names: `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2/minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2.jar`
- **1.8.9**, Legacy Yarn build 604: `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-legacy-intermediary-1-v2/1.8.9-net.legacyfabric.yarn.1_8_9.1.8.9+build.604-v2/minecraft-merged-legacy-intermediary-1-v2-1.8.9-net.legacyfabric.yarn.1_8_9.1.8.9+build.604-v2.jar`
- **Fabric API 0.141.6+1.21.11**: the aggregator's POM pins `fabric-rendering-v1` 16.2.10+0290ad933e and `fabric-events-interaction-v0` 4.1.1+3b89ecf63e. Both were read from Loom's Mojang-named copies under `client/.gradle/loom-cache/remapped_mods/remapped/net/fabricmc/fabric-api/`, as class files and from the `-sources.jar` Loom remapped beside them.
- **Legacy Fabric API 1.13.5+1.8.9**: the aggregator POM is at `~/.gradle/caches/fabric-loom/legacy-fabric-api/1.13.5+1.8.9.pom`. The module jars are under `~/.gradle/caches/modules-2/files-2.1/net.legacyfabric.legacy-fabric-api/`, and Loom's Legacy-Yarn-named copies are under `client/.gradle/loom-cache/remapped_mods/remapped/net/legacyfabric/`.

Bytecode is quoted as 0003 quotes it: offsets kept, packages dropped.

Nothing here was run. No mixin was written or built, so unlike 0003's section 7, no intermediary name was read back from a built jar.

---

## Summary

| Feature | Question | 1.21.11 | 1.8.9 |
| --- | --- | --- | --- |
| Crosshair | Where vanilla draws it | `Gui.renderCrosshair(GuiGraphics, DeltaTracker)`, private, called from `Gui.render` | one `drawTexture(IIIIII)` call in `InGameHud.render(float)`, gated by `InGameHud.showCrosshair()` |
| | What the API offers | `HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, Function<HudElement, HudElement>)`. It wraps the *call to* `renderCrosshair` | `HudRenderCallback`, which fires near the end of `InGameHud.render` and can only draw on top |
| | Hook that suppresses vanilla's and keeps its rules | **mixin**: `@WrapOperation` in `Gui.renderCrosshair` on the `GuiGraphics.blitSprite(RenderPipeline, Identifier, int, int, int, int)` whose identifier is `hud/crosshair` | **mixin**: `@WrapOperation` in `InGameHud.render(F)V` on `InGameHud.drawTexture(IIIIII)V`, the only such call in `render` |
| | Attack-cooldown indicator | **inside `renderCrosshair`**, drawn when `options.attackIndicator()` is `CROSSHAIR`, which is the default | does not exist |
| Hit indicator | Server's "it was hurt" | `ClientboundDamageEventPacket` → `ClientPacketListener.handleDamageEvent` → `LivingEntity.handleDamageEvent(DamageSource)` | `EntityStatusS2CPacket`, status 2 → `ClientPlayNetworkHandler.onEntityStatus` → `LivingEntity.handleStatus(byte)` |
| | Does it say who hit? | **yes**: `sourceCauseId()` is the attacker's entity id | **no**: entity id and a byte |
| | The player's own attack | `MultiPlayerGameMode.attack(Player, Entity)`; spear jabs use `piercingAttack(PiercingWeapon)`, which names no target. API: `AttackEntityCallback` (it also fires on the integrated server) | `ClientPlayerInteractionManager.attackEntity(PlayerEntity, Entity)`. No API |
| | Hook | **mixin** in `handleDamageEvent` at the call to `Entity.handleDamageEvent`, matching `sourceCauseId()` to the player | **mixins** in `onEntityStatus` at the call to `Entity.handleStatus(B)V`, and in `attackEntity`, correlated by entity id and time |
| Ping readout | Latency getter | `PlayerInfo.getLatency()` | `PlayerListEntry.getLatency()` |
| | Own entry | `Minecraft.getConnection().getPlayerInfo(player.getUUID())` | `MinecraftClient.getNetworkHandler().getPlayerListEntry(player.getUuid())` |
| | Singleplayer test | `Minecraft.hasSingleplayerServer()`. `isSingleplayer()` turns false once the world is opened to LAN | `MinecraftClient.isInSingleplayer()` |
| | Vanilla server cadence | latency pushed every 601 ticks (about 30 s); keepalive every 15 s; a moving average | latency pushed every 601 ticks; keepalive every 41 ticks (about 2 s); the same average |
| | Hook | none: read each frame, no mixin | none |
| Hit colour | Where the colour lives | a 16×16 `OverlayTexture`. Rows 0 to 7 are `0xB2FF0000`, and a draw passes only a row index | four `FloatBuffer.put` constants, `(1, 0, 0, 0.3)`, loaded as `GL_TEXTURE_ENV_COLOR` in `LivingEntityRenderer.method_10252` |
| | Hook, live | **accessor mixin** on `OverlayTexture.texture`: rewrite the pixels, then `DynamicTexture.upload()` | **mixin** in `method_10252` before its `FloatBuffer.flip()`: rewrite the buffer on every call |
| Freelook | Camera rotation from the player | `Camera.setup(...)` → `setRotation(entity.getViewYRot(pt), entity.getViewXRot(pt))` | `GameRenderer.transformCamera(float)` reads `Entity.yaw`, `pitch`, `prevYaw` and `prevPitch` directly, as fields |
| | Mouse to player | `MouseHandler.turnPlayer(double)` → `LocalPlayer.turn(DD)V`, which is `Entity.turn` | `GameRenderer.render(float, long)` → `ClientPlayerEntity.increaseTransforms(FF)V`, called twice, which is `Entity.increaseTransforms` |
| | Third person | `Options.getCameraType()` / `setCameraType(CameraType)`; F5 is handled in `Minecraft.handleKeybinds()` | `GameOptions.perspective`, an int (0, 1 or 2); F5 is handled in `MinecraftClient.tick()` |

---

## 1. Custom crosshair

### 1.1 1.21.11: where the crosshair is drawn

`Gui.render(GuiGraphics, DeltaTracker)` calls `renderCrosshair` inside the F1 check:

```
 18: getfield      Minecraft.options:Options
 21: getfield      Options.hideGui:Z
 24: ifne          61                // F1: skip overlays, crosshair, hotbar, effects, boss bar
 30: invokevirtual renderCameraOverlays:(GuiGraphics;DeltaTracker)V
 36: invokevirtual renderCrosshair:(GuiGraphics;DeltaTracker)V
```

Before that, `Gui.render` returns at offset 13 if the screen is a `LevelLoadingScreen`. `GameRenderer.render(DeltaTracker, boolean)` calls `Gui.render` only when `Minecraft.isGameLoadFinished()` is true, its own boolean argument is true, and `minecraft.level != null`. A screen being open does not stop the HUD, so the crosshair still draws beneath chat or an inventory.

`Gui.renderCrosshair` draws **both the crosshair and the attack-cooldown indicator**:

```
   9: invokevirtual Options.getCameraType:()CameraType
  12: invokevirtual CameraType.isFirstPerson:()Z
  15: ifne          19
  18: return                                   // either third-person view
  26: invokevirtual MultiPlayerGameMode.getPlayerMode:()GameType
  29: getstatic     GameType.SPECTATOR
  43: invokevirtual canRenderCrosshairForSpectator:(HitResult)Z
  49: return                                   // spectator, not looking at something with a menu
  57: getstatic     DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR
  60: invokevirtual DebugScreenEntryList.isCurrentlyEnabled:(Identifier)Z
  63: ifne          376                      // to return: no crosshair and no indicator
  78: getstatic     CROSSHAIR_SPRITE           // "hud/crosshair"
 103: invokevirtual GuiGraphics.blitSprite:(RenderPipeline;Identifier;IIII)V       // the crosshair, 15x15
 113: invokevirtual Options.attackIndicator:()OptionInstance
 119: getstatic     AttackIndicatorStatus.CROSSHAIR
 122: if_acmpne     376
 133: invokevirtual LocalPlayer.getAttackStrengthScale:(F)F
 304: getstatic     CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE
 315: invokevirtual GuiGraphics.blitSprite:(RenderPipeline;Identifier;IIII)V
 341: getstatic     CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE
 351: invokevirtual GuiGraphics.blitSprite:(RenderPipeline;Identifier;IIII)V
 358: getstatic     CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE
 373: invokevirtual GuiGraphics.blitSprite:(RenderPipeline;Identifier;IIIIIIII)V
 376: return
```

Every call passes `RenderPipelines.CROSSHAIR`, which `RenderPipelines` builds `.withBlend(BlendFunction.INVERT)`. Vanilla's crosshair and indicator invert what is behind them rather than drawing a colour.

`AttackIndicatorStatus` has `OFF`, `CROSSHAIR` and `HOTBAR`. `Options` constructs `options.attackIndicator` with **`CROSSHAIR` as its default**. The `HOTBAR` variant is drawn elsewhere, in `renderItemHotbar`, with `HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE` and `_PROGRESS_SPRITE`. So for a player on default options, the cooldown indicator is part of the crosshair method.

### 1.2 1.21.11: Fabric API's element registry

`net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry`, in `fabric-rendering-v1` 16.2.10:

```
public static void replaceElement(Identifier, Function<HudElement, HudElement>);
public static void removeElement(Identifier);
public static void attachElementBefore(Identifier, Identifier, HudElement);
public static void attachElementAfter(Identifier, Identifier, HudElement);
public static void addFirst(Identifier, HudElement);
public static void addLast(Identifier, HudElement);
```

`HudElement` is `void render(GuiGraphics, DeltaTracker)`. The crosshair's identifier is `VanillaHudElements.CROSSHAIR`, which is `Identifier.withDefaultNamespace("crosshair")`.

The registry reaches the crosshair through Fabric's `GuiMixin`, which wraps the call site in `Gui.render`, not the method body:

```java
@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
private void wrapCrosshair(Gui instance, GuiGraphics context, DeltaTracker tickCounter, Operation<Void> renderVanilla) {
    HudElementRegistryImpl.getRoot(VanillaHudElements.CROSSHAIR).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
}
```

`replaceElement` swaps in `replacer.compose(l::element)`. The replacer is handed the vanilla element, and calling that element runs the whole of `renderCrosshair`. Three things follow:

- **A replacement removes the attack indicator.** The indicator is drawn inside `renderCrosshair`. Delegating to the vanilla element brings back vanilla's crosshair along with it, so a replacement cannot keep one without the other.
- **A replacement inherits only the conditions outside `renderCrosshair`**: F1, the loading screen, and the game being loaded. First person, the spectator rule and the 3D crosshair are all checked inside `renderCrosshair`, so a replacement would have to repeat them.
- To redraw the indicator, a replacement would have to repeat offsets 106 to 373. Everything that needs is public:
  - `options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR`;
  - `player.getAttackStrengthScale(0.0F)`;
  - the "full" test: `minecraft.crosshairPickEntity instanceof LivingEntity`, strength `>= 1.0F`, `player.getCurrentItemAttackStrengthDelay() > 5.0F`, `crosshairPickEntity.isAlive()`, and, if `player.getActiveItem().get(DataComponents.ATTACK_RANGE)` is non-null, `AttackRange.isInRange(player, minecraft.hitResult.getLocation())`;
  - the position: `x = guiWidth()/2 - 8`, `y = guiHeight()/2 - 7 + 16`;
  - the three sprites `hud/crosshair_attack_indicator_full` (16×16), `_background` (16×4) and `_progress`, drawn as `blitSprite(CROSSHAIR, progress, 16, 4, 0, 0, x, y, (int)(strength * 17.0F), 4)`.

  The sprite fields in `Gui` are private, but the identifiers are plain strings, recoverable with `Identifier.withDefaultNamespace`.

**The hook that avoids both problems** is a mixin *inside* `Gui.renderCrosshair`. It is a `@WrapOperation` on `GuiGraphics.blitSprite(RenderPipeline, Identifier, int, int, int, int)`, which draws ash's crosshair in place of vanilla's when the identifier is `hud/crosshair`, and otherwise calls the original. The rest of the method runs as before: every early return, and the whole indicator.

- Match on the identifier, not the ordinal. The same six-argument overload is also used for the "full" and "background" indicator sprites, at ordinals 1 and 2.
- It composes with Fabric's `GuiMixin`, which wraps the call *to* `renderCrosshair`, not anything inside it.
- If the mixin does not land, vanilla draws. That is the "never none, never two" degradation the spec asks for.
- Vanilla places the indicator 16 GUI pixels below its 15-pixel crosshair. A larger ash crosshair can overlap it. That is a layout decision, not a hook question.

### 1.3 1.21.11: when vanilla hides the crosshair

Each of these was read from the code above:

1. **Not drawing a level at all.** `GameRenderer.render` skips `Gui.render` unless the game has finished loading, its boolean argument is true and `minecraft.level != null`. `Gui.render` returns while the screen is a `LevelLoadingScreen`.
2. **F1**: `Options.hideGui` is true.
3. **Either third-person view**: `!options.getCameraType().isFirstPerson()`. The `CameraType` values are `FIRST_PERSON`, `THIRD_PERSON_BACK` and `THIRD_PERSON_FRONT`.
4. **Spectator** (`gameMode.getPlayerMode() == GameType.SPECTATOR`), unless `canRenderCrosshairForSpectator(minecraft.hitResult)` holds. It holds when the hit is an entity that is a `MenuProvider`, or a block whose `BlockState.getMenuProvider(level, pos)` is non-null.
5. **The 3D debug crosshair**: `minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)`. This removes the 2D crosshair *and* the crosshair-mode indicator. `GameRenderer.renderLevel` then calls `DebugScreenOverlay.render3dCrosshair(Camera)`, if the view is first person and `!hideGui`. What "currently enabled" means, from `DebugScreenEntryList.rebuildCurrentList()`:
   - the entry's status is `ALWAYS_ON`, or it is `IN_OVERLAY` while the F3 overlay is visible;
   - and the entry is allowed. `3d_crosshair` is a `DebugEntryNoop()`, whose `isAllowed(reduced)` is `!reduced`, so reduced debug info (`Minecraft.showOnlyReducedInfo()`) keeps the 2D crosshair even with F3 open;
   - `DebugScreenEntries.PROFILES` sets `3d_crosshair` to `IN_OVERLAY` in the `DEFAULT` profile and leaves it out of `PERFORMANCE`, and a player can change it per entry.

   With defaults, then, F3 swaps the crosshair for axes unless the server reduces debug info.

### 1.4 1.8.9: where the crosshair is drawn, and when

`InGameHud.render(float)` draws the crosshair with one call:

```
236: getstatic     GUI_ICONS_TEXTURE:Identifier
239: invokevirtual TextureManager.bindTexture:(Identifier)V
246: invokevirtual showCrosshair:()Z
249: ifeq          289
252: sipush        775                   // GL_ONE_MINUS_DST_COLOR
255: sipush        769                   // GL_ONE_MINUS_SRC_COLOR
260: invokestatic  GlStateManager.blendFuncSeparate:(IIII)V
286: invokevirtual drawTexture:(IIIIII)V   // (w/2-7, h/2-7, 0, 0, 16, 16)
289: sipush        770                   // blend back to SRC_ALPHA, ONE_MINUS_SRC_ALPHA
```

The call's owner in the constant pool is `net/minecraft/client/gui/hud/InGameHud.drawTexture:(IIIIII)V`, inherited from `DrawableHelper`, and it is the only `drawTexture` call in `render`. The blend values were read from LWJGL 2's `GL11` in the Gradle cache (`lwjgl-2.9.4+legacyfabric.15`): 775 and 769 invert, as on 1.21.11.

`InGameHud.showCrosshair()` is protected:

```
  7: getfield      GameOptions.debugEnabled:Z
 10: ifeq          41
 20: invokevirtual ClientPlayerEntity.getReducedDebugInfo:()Z
 23: ifne          41
 33: getfield      GameOptions.reducedDebugInfo:Z
 36: ifne          41
 39: iconst_0                                // F3 open, debug info not reduced: no crosshair
 40: ireturn
 48: invokevirtual ClientPlayerInteractionManager.isSpectator:()Z
 51: ifeq          124                     // not spectator: crosshair
 58: getfield      MinecraftClient.targetedEntity:Entity
 61: ifnull        66
 64: iconst_1                                // spectator looking at any entity
 83: getfield      BlockHitResult.type
 86: getstatic     BlockHitResult$Type.BLOCK
114: instanceof    Inventory                 // spectator looking at a block entity that is an Inventory
124: iconst_1
```

`GameRenderer.render(float, long)` decides whether `InGameHud.render` runs at all:

```
640: getfield      GameOptions.hudHidden:Z
643: ifeq          656
650: getfield      MinecraftClient.currentScreen:Screen
653: ifnull        676                     // F1 and no screen: no HUD
673: invokevirtual InGameHud.render:(F)V
```

So on 1.8.9 vanilla hides the crosshair:

1. **with F1 and no screen open.** With F1 on and *any* screen open, the whole HUD draws, the crosshair included;
2. **with F3 open**, unless debug info is reduced by the server (`ClientPlayerEntity.getReducedDebugInfo()`) or the option (`GameOptions.reducedDebugInfo`). `GameRenderer.renderDebugCrosshair(float)` then draws axes, under `debugEnabled && !hudHidden && !getReducedDebugInfo() && !reducedDebugInfo`;
3. **in spectator**, unless it is looking at any entity, or at a block whose block entity is an `Inventory`.

**It does not hide in third person.** Neither `showCrosshair()` nor the draw reads `GameOptions.perspective`. The only read of it in `InGameHud` is the pumpkin-blur check at offset 95. The 1.8.9 crosshair shows in both third-person views.

### 1.5 1.8.9: suppressing vanilla's crosshair

Legacy Fabric's `HudRenderCallback` is fired by `net.legacyfabric.fabric.mixin.client.rendering.InGameHudMixin`, in `legacy-fabric-rendering-api-v1` 1.0.1+1.8.9:

```java
@Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;color(FFFF)V"),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;render(I)V")))
```

It fires once, after chat, and can only draw on top. The mixin target that suppresses vanilla's crosshair is `net.minecraft.client.gui.hud.InGameHud.render(F)V`. Either of these works:

- `@WrapOperation` on `InGameHud.drawTexture(IIIIII)V`. This is the counterpart of the 1.21.11 hook: ash draws in its place, so it inherits `showCrosshair()` and the F1-or-screen rule. Inside the wrap, the icons texture is bound and the blend is the inverting one. ash's surface has to set its own blend state, and vanilla resets it at offset 289.
- `@WrapOperation` on the `showCrosshair()Z` call: call the original to learn vanilla's answer, keep it for ash's own draw, and return `false`.

### 1.6 1.8.9: attack indicator

There is none. `GameOptions` has no attack-indicator field, and `PlayerEntity` has no attack-strength method. A field named `MinecraftClient.attackCooldown` exists, but it is something else: `doAttack()` sets it to 10 when a click hits nothing and `ClientPlayerInteractionManager.hasLimitedAttackSpeed()` is true, and it blocks the next click. Nothing draws it.

---

## 2. Hit indicator

### 2.1 1.21.11: the server's confirmation

**`ClientboundDamageEventPacket`** is a record: `int entityId`, `Holder<DamageType> sourceType`, `int sourceCauseId`, `int sourceDirectId`, `Optional<Vec3> sourcePosition`. Its server-side constructor names the attacker:

```
  2: invokevirtual Entity.getId:()I                       // entityId: who was hurt
 10: invokevirtual DamageSource.getEntity:()Entity        // sourceCauseId: getEntity().getId(), or -1
 28: invokevirtual DamageSource.getDirectEntity:()Entity  // sourceDirectId: e.g. the arrow, or -1
```

On the wire, each id is written as a VarInt of `id + 1`.

*Who sends it.* It is sent only by `ServerLevel.broadcastDamageEvent(Entity, DamageSource)`, which goes through `ServerChunkCache.sendToTrackingPlayersAndSelf`. Its only caller in `LivingEntity` is `hurtServer`, which reaches it on a fresh hit only:

- If `invulnerableTime > 10` and the damage does not bypass cooldown, a hit either returns `false`, or applies the difference with the flag cleared, so no event is sent.
- A fresh hit sets `invulnerableTime = 20` and `hurtTime = 10`, then sends the event.
- It is not sent when a `BlocksAttacks` item such as a shield blocked the hit. Offset 319 calls `BlocksAttacks.onBlocked` instead.

*Who handles it.* `ClientPacketListener.handleDamageEvent(ClientboundDamageEventPacket)`:

```
  9: invokestatic  PacketUtils.ensureRunningOnSameThread:(Packet;PacketListener;PacketProcessor;)V
 20: invokevirtual ClientLevel.getEntity:(I)Entity
 25: ifnonnull     29
 28: return
 35: invokevirtual ClientboundDamageEventPacket.getSource:(Level)DamageSource
 38: invokevirtual Entity.handleDamageEvent:(DamageSource)V
```

`LivingEntity.handleDamageEvent(DamageSource)` sets `invulnerableTime = 20` and `hurtDuration = hurtTime = 10`, and plays the hurt sound. That `hurtTime` is what turns the entity red (section 4). `ClientPacketListener` is the only caller of `handleDamageEvent`, and `Entity`'s own version is empty.

**So on 1.21.11 the client can match a hurt to its own attack directly.** The test is `packet.sourceCauseId() == minecraft.player.getId()` with `packet.entityId() != player.getId()`. No timing window is needed, and it also covers attacks the client never records itself, such as spear jabs and arrows.

The hook is a mixin in `ClientPacketListener.handleDamageEvent` at `INVOKE Entity.handleDamageEvent(DamageSource)V`: after the thread hop, and with the entity non-null. Not at `HEAD`. `ensureRunningOnSameThread` schedules the packet and throws `RunningOnDifferentThreadException` when it is called off the game thread, so a `HEAD` injection runs once on the network thread and again on the game thread. An `@Inject` at `HEAD` of `LivingEntity.handleDamageEvent`, reading `source.getEntity()`, would also work.

**`ClientboundHurtAnimationPacket`** (`int id, float yaw`) is no use for this. Its only sender is `ServerPlayer.indicateDamage(double, double)`, which sends it to that player's own connection. It tells a player they were hurt, never that someone else was. Its handler calls `Entity.animateHurt(float)`.

**Entity events.** On 1.21.11, event byte 2 is **`EntityEvent.KINETIC_HIT`**, not "hurt". `LivingEntity.handleEntityEvent` sends 2 to `onKineticHit()`. No entity event means "hurt" on this target.

**Shooter-only arrow confirmation.** `AbstractArrow.onHitEntity` sends `ClientboundGameEventPacket.PLAY_ARROW_HIT_SOUND` to the owning `ServerPlayer`. It does so only after `hurtOrSimulate` returns true and the target is a `Player`. The damage event already covers this case.

### 2.2 1.21.11: the player's own attack, and what fires on the click

`MultiPlayerGameMode.attack(Player, Entity)`, whose only caller is `Minecraft.startAttack()`:

```
 13: invokestatic  ServerboundInteractPacket.createAttackPacket:(Entity;Z)ServerboundInteractPacket
 16: invokevirtual ClientPacketListener.send:(Packet)V
 31: invokevirtual Player.attack:(Entity)V                // run on the client too
 35: invokevirtual Player.resetAttackStrengthTicker:()V
```

`Minecraft.startAttack()` also calls **`MultiPlayerGameMode.piercingAttack(PiercingWeapon)`** when the held item has `DataComponents.PIERCING_WEAPON`. It sends `ServerboundPlayerActionPacket(Action.STAB, BlockPos.ZERO, Direction.DOWN)` with **no target entity**; the server decides what was hit.

**The click-time trap.** `Player.attack` calls `Entity.hurtOrSimulate`, which on a client level calls `hurtClient`. `RemotePlayer.hurtClient` returns `true`, so when the target is another player, `Player.attack`'s success path runs on the click. That includes `getKnockback`, `doSweepAttack`, `attackVisualEffects` and `setLastHurtMob`. `LivingEntity` does not override `hurtClient` (it stays `Entity`'s `return false`), so the click never sets `hurtTime`. Any hook on `Player.attack` or its success path is a click signal, not a confirmation.

Fabric API offers two events here, both in `fabric-events-interaction-v0` 4.1.1:

- **`AttackEntityCallback.EVENT`**, `(Player, Level, InteractionHand, Entity, EntityHitResult) -> InteractionResult`. On the client it fires from `MultiPlayerGameModeMixin` before `attack`'s first `ClientPacketListener.send`. **It also fires on the server**, from `ServerPlayerMixin`. In singleplayer the integrated server runs in the same JVM, so a client listener has to check `world.isClientSide()`. It does not fire for `piercingAttack`.
- **`ClientPreAttackCallback.EVENT`** fires every tick the attack key is held. That is a click signal.

### 2.3 1.8.9: the server's confirmation

**`EntityStatusS2CPacket`** carries `int id` and `byte status`, and nothing else. `LivingEntity.damage(DamageSource, float)` sends status 2 on a fresh hit only:

```
121: getfield      timeUntilRegen:I            // inside the invulnerability window?
137: ...                                      //   yes: return false, or apply the difference with the flag cleared
293: ifeq          449
302: invokevirtual World.sendEntityStatus:(Entity;B)V     // (this, 2)
```

`ServerWorld.sendEntityStatus` sends it through `EntityTracker.sendToAllTrackingEntities`. The client handles it in `ClientPlayNetworkHandler.onEntityStatus(EntityStatusS2CPacket)`:

```
  6: invokestatic  NetworkThreadUtils.forceMainThread:(Packet;PacketListener;ThreadExecutor;)V
 14: invokevirtual EntityStatusS2CPacket.getEntity:(World)Entity
 26: bipush        21                        // 21: guardian sound, handled here
 60: invokevirtual Entity.handleStatus:(B)V
```

`LivingEntity.handleStatus(2)` sets `hurtTime = maxHurtTime = 10` and plays the hurt sound. It also calls `damage(DamageSource.GENERIC, 0.0F)`, which returns `false` on the client at its `world.isClient` check. `forceMainThread` throws `OffThreadException` off the main thread, as on 1.21.11, so the hook belongs at `INVOKE Entity.handleStatus(B)V`, not `HEAD`. Many `LivingEntity` subclasses override `handleStatus`, which is a second reason to hook the handler rather than the entity.

**The packet does not say who hit.** Matching a status 2 to the player's own attack has to be done by correlation. Record the target's entity id and the time at `ClientPlayerInteractionManager.attackEntity(PlayerEntity, Entity)`, then accept a status 2 for that id within a window. `MinecraftClient.doAttack()` is `attackEntity`'s only caller:

```
  8: new           PlayerInteractEntityC2SPacket
 13: getstatic     PlayerInteractEntityC2SPacket$Type.ATTACK
 19: invokevirtual ClientPlayNetworkHandler.sendPacket:(Packet)V
 34: invokevirtual PlayerEntity.attack:(Entity)V
```

**No Legacy Fabric API attack event exists.** Every module jar named in the 1.13.5+1.8.9 aggregator's POM was listed, 43 modules in all, and none has a class whose name contains Attack, Interact, Hit or Damage. The only combat event is `ServerEntityCombatEvents`, in `legacy-fabric-entity-events-v1-common`, and it is server-side.

**The same click-time trap.** `PlayerEntity.attack(Entity)` calls `Entity.damage(DamageSource.player(this), f)` at offset 247. **`OtherClientPlayerEntity.damage` returns `true`**, so the success branch runs on the click when the target is another player. That branch includes `addVelocity` on the target and `setSprinting(false)`.

**Animation id 1 is dead.** `onEntityAnimation` maps animation id 1 to `Entity.animateDamage()`, but no server class sends id 1: `LivingEntity` constructs `EntityAnimationS2CPacket` only with 0, and `ServerPlayerEntity` only with 2, 3, 4 and 5.

**Shooter-only arrow confirmation.** `AbstractArrowEntity` sends `GameStateChangeS2CPacket(6, 0.0F)` to the owning `ServerPlayerEntity` inside the branch where `Entity.damage` returned true (offsets 954–957) and the target is a `PlayerEntity`. On 1.8.9 this is the only hurt signal that names the shooter.

---

## 3. Ping readout

### 3.1 1.21.11

- **Entry and getter**: `net.minecraft.client.multiplayer.PlayerInfo.getLatency()`, an `int` in milliseconds. The server sets it through `PlayerInfo.setLatency(int)`, which is protected.
- **The player's own entry**: `Minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID())`. `ClientPacketListener.getPlayerInfo(UUID)` is a plain `playerInfoMap.get`, and it returns null until the server has listed the player. `AbstractClientPlayer.getPlayerInfo()` does the same lookup with a cache, but it is **protected**.
- **What the tab list shows**: `PlayerTabOverlay.renderPingIcon(GuiGraphics, int, int, int, PlayerInfo)` reads `getLatency()` and draws bars. It never draws a number. The thresholds are `< 0` unknown, `< 150` five bars, `< 300` four, `< 600` three, `< 1000` two, and otherwise one.
- **Singleplayer**:
  - `Minecraft.isLocalServer()` returns the `isLocalServer` field;
  - `hasSingleplayerServer()` is `isLocalServer && singleplayerServer != null`;
  - `isSingleplayer()` is `getSingleplayerServer() != null && !IntegratedServer.isPublished()`, so **it turns false when the host opens the world to LAN.**

  To hide the readout for whoever hosts the integrated server, LAN or not, test `hasSingleplayerServer()`.
- **How often the server updates it** (vanilla server):
  - `PlayerList.tick()` broadcasts `ClientboundPlayerInfoUpdatePacket` with `Action.UPDATE_LATENCY` when `++sendAllPlayerInfoIn > 600`, which is every 601 ticks, about 30 s.
  - `ServerCommonPacketListenerImpl.keepConnectionAlive()` sends a keepalive when 15 000 ms have passed, and never to the singleplayer owner.
  - `handleKeepAlive` then sets `latency = (latency * 3 + rtt) / 4`.

  So the number is a smoothed average, refreshed about twice a minute. In singleplayer no keepalive is ever sent to the owner.

### 3.2 1.8.9

- **Entry and getter**: `net.minecraft.client.network.PlayerListEntry.getLatency()`.
- **The player's own entry**: `MinecraftClient.getNetworkHandler().getPlayerListEntry(player.getUuid())` (`Entity.getUuid()`). `AbstractClientPlayerEntity.getPlayerListEntry()` is **protected**. `getPlayerListEntry(String)` also exists and walks every entry comparing names.
- **What the tab list shows**: `PlayerListHud.renderLatencyIcon(int, int, int, PlayerListEntry)`, with bars at the same thresholds as 1.21.11.
- **Singleplayer**: `MinecraftClient.isIntegratedServerRunning()` returns its field. `isInSingleplayer()` is `isIntegratedServerRunning && server != null`. Neither changes when the world is opened to LAN.
- **How often the server updates it** (vanilla server):
  - `PlayerManager.updatePlayerLatency()` sends `PlayerListS2CPacket(UPDATE_LATENCY, players)` when `++latencyUpdateTimer > 600`.
  - `ServerPlayNetworkHandler.tick()` sends a keepalive when the tick counter is more than 40 past the last one, about every 2 s. There is no singleplayer exception.
  - `onKeepAlive` then sets `player.ping = (ping * 3 + rtt) / 4`.

  Legacy Yarn's names here mislead: the tick counter is called `lastTickMovePacketsCount` and the last-keepalive tick `keepAliveId`.

Neither target needs a mixin or an API for the number itself.

---

## 4. Hit colour

### 4.1 1.21.11: the colour is baked into a texture

`OverlayTexture`'s constructor fills a 16×16 `DynamicTexture` named "Entity Color Overlay":

```
 46: iload_2                         // y
 47: bipush        8
 49: if_icmpge     63
 55: ldc           -1291911168        // 0xB2FF0000: rows 0-7, red, alpha 178
 57: invokevirtual NativeImage.setPixel:(III)V
 63: ...                             // rows 8-15: ARGB.white((1 - x/15 * 0.75) * 255)
106: invokevirtual DynamicTexture.upload:()V
```

`NativeImage.setPixel(int, int, int)` takes ARGB: it calls `ARGB.toABGR`, then `setPixelABGR`. A draw passes only coordinates into this texture:

- `OverlayTexture.v(boolean)` returns 3 for red and 10 otherwise;
- `u(float)` is `(int)(f * 15)`;
- `pack(u, v)` is `u | v << 16`;
- `NO_OVERLAY` is `pack(0, 10)`.

The per-draw value is chosen in `LivingEntityRenderer`:

```
public static int getOverlayCoords(LivingEntityRenderState, float);
  1: invokestatic  OverlayTexture.u:(F)I
  5: getfield      LivingEntityRenderState.hasRedOverlay:Z
  8: invokestatic  OverlayTexture.v:(Z)I
 11: invokestatic  OverlayTexture.pack:(II)I
```

`extractRenderState(T, S, float)` sets `hasRedOverlay = hurtTime > 0 || deathTime > 0` at offsets 280–300.

The texture is applied by `assets/minecraft/shaders/core/entity.vsh`, which does `overlayColor = texelFetch(Sampler1, UV1, 0);`, and `entity.fsh`:

```glsl
color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
```

The texel's **alpha is how much of the original colour survives**. Vanilla's 178/255 ≈ 0.698 leaves about 30 % red. A strength setting `s` therefore maps to alpha `(1 − s) × 255`, inverted.

The red rows are sampled wherever a renderer passes `hasRedOverlay` through:

- `LivingEntityRenderer.submit` itself;
- the layers that call `getOverlayCoords`: `RenderLayer`, `Deadmau5EarsLayer`, `HorseMarkingLayer`, `LivingEntityEmissiveLayer`, `MushroomCowMushroomLayer`, `SheepWoolLayer`, `SlimeOuterLayer` and `SnowGolemHeadLayer`;
- `EnderDragonRenderer`, through `pack(0.0F, hasRedOverlay)`.

`HumanoidArmorLayer` uses `OverlayTexture.NO_OVERLAY`, so **armour never flashes**. `TntMinecartRenderer` uses only the white rows.

**Changing it live is feasible.** The instance is `Minecraft.gameRenderer.overlayTexture()`, which is public, but its `texture` field is `private final DynamicTexture`. An accessor mixin on it is the one mixin needed. Then:

1. `getPixels()`, which returns the `NativeImage` the texture keeps;
2. `setPixel(x, y, argb)` for x in 0–15 and y in 0–7, with alpha `(1 − strength) × 255`;
3. `upload()`, which re-copies the pixels to the GPU texture through `CommandEncoder`.

These three are 256 pixel writes and one upload, and they must run on the render thread. The colour is global: every living entity's hurt flash changes together. The texture has one red row set, so the colour cannot differ by entity type.

### 4.2 1.8.9: the colour is a constant passed per draw

`LivingEntityRenderer.method_10252(LivingEntity, float, boolean)` is unnamed in Legacy Yarn 604; MCP calls it `setBrightness`. It is called through `method_10258(T, float)`, which passes `true`, before the body is drawn in `render`. `renderFeatures` calls it before each feature layer, passing `feature.combineTextures()`. It sets up texture-environment combiners, then:

```
 36: getfield      LivingEntity.hurtTime:I
 44: getfield      LivingEntity.deathTime:I      // hurt = hurtTime > 0 || deathTime > 0
345: iload         7                             // hurt?
347: ifeq          391
354: fconst_1   -> FloatBuffer.put               // R
363: fconst_0   -> FloatBuffer.put               // G
372: fconst_0   -> FloatBuffer.put               // B
381: ldc 0.3f   -> FloatBuffer.put               // A
391: ...                                         // else: the creeper-style overlay colour, 4 more puts
498: invokevirtual FloatBuffer.flip:()Ljava/nio/Buffer;
512: invokestatic  GL11.glTexEnv:(IILjava/nio/FloatBuffer;)V   // (8960, 8705, buffer)
```

The buffer is `protected FloatBuffer buffer`. 8960 is `GL_TEXTURE_ENV` and 8705 is `GL_TEXTURE_ENV_COLOR`, read from LWJGL 2's `GL11`. The combiner on the lightmap unit is `GLX.interpolate`, with source 0 the constant, source 1 the previous result and source 2 the constant's `GL_SRC_ALPHA` (770). **[DOCS]** In [the OpenGL 2.1 `glTexEnv` reference](https://registry.khronos.org/OpenGL-Refpages/gl2.1/xhtml/glTexEnv.xml), `GL_INTERPOLATE` is `Arg0 × Arg2 + Arg1 × (1 − Arg2)`. Here the **alpha is the red's strength**: 0.3 gives 30 % red. That is the opposite sense to 1.21.11's texel alpha, and the same visible result.

The static `TEX` is a 16×16 texture filled with `-1`, all white, so the colour is not in a texture on this target. `method_10260()` undoes the state.

**Which layers take the tint.** The method returns early at offsets 67–77 when the entity is hurt but `combineTextures` is false. `ArmorFeatureRenderer.combineTextures()` returns `false`, so **vanilla 1.8.9 armour does not flash red**. The feature renderers whose `combineTextures()` returns `true` are `Deadmau5FeatureRenderer`, `HeadFeatureRenderer`, `MooshroomMushroomFeatureRenderer`, `SheepWoolFeatureRenderer`, `SlimeFeatureRenderer`, `SnowGolemPumpkinFeatureRenderer` and `WolfCollarFeatureRenderer`.

**Changing it live is trivial.** The constants are read on every draw, so a mixin that reads ash's current setting each time changes the colour at once. The hook is an `@Inject` in `method_10252` at `INVOKE FloatBuffer.flip()` (there is one). When `hurtTime > 0 || deathTime > 0`, the hurt branch was taken, because vanilla checks hurt before the creeper-style colour; the mixin then does `buffer.clear()` and puts ash's r, g, b and strength. Otherwise the buffer is left alone. The alternative is four `@ModifyArg`s on `FloatBuffer.put(F)` at ordinals 0–3.

---

## 5. Freelook camera

### 5.1 1.21.11

**The camera's rotation.** `GameRenderer.render(DeltaTracker, boolean)` calls `updateCamera(DeltaTracker)`, which calls:

```
146: invokevirtual Camera.setup:(Level;Entity;ZZF)V
     // (level, cameraEntity, !cameraType.isFirstPerson(), cameraType.isMirrored(), partialTick)
```

`Camera.setup` takes its rotation from the entity at two sites: offset 148 in the minecart-lerp branch and offset 183 otherwise.

```
174: invokevirtual Entity.getViewYRot:(F)F
180: invokevirtual Entity.getViewXRot:(F)F
183: invokevirtual setRotation:(FF)V
247: iload_3                                    // detached, i.e. third person
256: ... setRotation(yRot + 180, -xRot)           // THIRD_PERSON_FRONT, from the rotation just set
385: invokevirtual move:(FFF)V                   // back off by -getMaxZoom(distance)
436: invokevirtual setRotation:(FF)V             // sleeping: bed orientation
```

`setRotation(float, float)` is protected. Substituting the freelook yaw and pitch at the two entity-derived `setRotation` calls, or at the four `getViewYRot`/`getViewXRot` calls, rotates the camera. Because the front-view flip and the third-person `move` run afterwards and read the rotation already set, they follow it.

`LevelRenderer` calls `Camera.xRot()` and `yRot()`, and `SectionOcclusionGraph` only the camera's position. Neither calls `Entity.getXRot`, `getYRot`, `getViewXRot`, `getViewYRot` or `getLookAngle`, so nothing in vanilla's level rendering reads the player's own rotation. Targeting does read it. `GameRenderer.pick(float)` → `LocalPlayer.raycastHitResult(float, Entity)` → `pick(Entity, double, double, float)`, which uses `Entity.getViewVector(float)`. Under freelook, `Minecraft.hitResult` and the crosshair's target stay where the player faces.

**The mouse.** `Minecraft.runTick(boolean)` calls `MouseHandler.handleAccumulatedMovement()`, which calls `turnPlayer(double)` (private) when `isMouseGrabbed()` and `minecraft.player != null`. `turnPlayer` applies sensitivity, smoothing and the invert options, then:

```
292: invokevirtual LocalPlayer.turn:(DD)V
```

`LocalPlayer` does not override `turn`. It is `Entity.turn(double, double)`:

- `xRot += dy * 0.15`, clamped to ±90;
- `yRot += dx * 0.15`;
- the same deltas are added to `xRotO` and `yRotO`;
- `vehicle.onPassengerTurned(this)` is called.

A `@WrapOperation` on that `LocalPlayer.turn(DD)V` call can send the deltas to ash's camera, using the same 0.15 factor and pitch clamp, instead of to the player.

**Third person.** `Options.getCameraType()` and `setCameraType(CameraType)` are public. F5 is handled in `Minecraft.handleKeybinds()`: `keyTogglePerspective.consumeClick()` → `setCameraType(getCameraType().cycle())`, then `gameRenderer.checkEntityPostEffect(...)` when first-person-ness changed, and `levelRenderer.needsUpdate()`.

### 5.2 1.8.9

**The camera's rotation.** `GameRenderer.renderWorld(int, float, long)` calls `setupCamera(float, int)`, which calls `transformCamera(float)`. There is no camera object with a rotation. `transformCamera` rotates the GL matrix straight from the camera entity's **fields**:

```
250: getfield      GameOptions.perspective:I
253: ifle          700                        // first person: translate(0, 0, -0.1)
301: getfield      Entity.yaw:F                 // third person: yaw, pitch (+180 for perspective 2)
307: getfield      Entity.pitch:F
562: invokevirtual ClientWorld.rayTrace(...)     // shorten the distance on collision
637: getfield      Entity.pitch:F  ...  GlStateManager.rotate
650: getfield      Entity.yaw:F    ...  GlStateManager.rotate
722: getfield      Entity.prevPitch:F  /  726: Entity.pitch:F   -> rotate   // the view itself
789: getfield      Entity.prevYaw:F    /  793: Entity.yaw:F     -> rotate
```

The third-person branch and the final view rotation are skipped when `GameOptions.field_955` is true; this field is unnamed in Legacy Yarn 604, and MCP calls it `debugCamEnable`. `AnimalEntity` camera entities use `headYaw` instead of `yaw`.

Two more places read the view entity's rotation during a frame. A freelook that changes only `transformCamera` misses both:

- `Camera.update(PlayerEntity, boolean)`, a static, called from `renderWorld` after `setupCamera`, reads `pitch` and `yaw` for the particle-facing vectors.
- **`WorldRenderer.setupTerrain(Entity, double, CameraView, int, boolean)`** decides whether to recompute the visible chunk set by comparing the entity's `x`, `y`, `z`, `pitch` and `yaw` with `lastCameraX` … `lastCameraYaw` (offsets 440–507). `getFacing(Entity, double)` also starts from `pitch` and `yaw`. If the camera turns while the player's own rotation stays still, the chunk set is not re-evaluated for the new view.

**The mouse.** `GameRenderer.render(float, long)` does the mouse work when `MinecraftClient.focused` is true, calling `MouseInput.updateMouse()`, then:

```
327: invokevirtual ClientPlayerEntity.increaseTransforms:(FF)V   // smooth-camera branch
358: invokevirtual ClientPlayerEntity.increaseTransforms:(FF)V   // normal branch
```

`ClientPlayerEntity` does not override it. It is `Entity.increaseTransforms(float, float)`:

- `yaw += dx * 0.15`;
- `pitch -= dy * 0.15`, clamped to ±90. The sign is the reverse of 1.21.11's `turn`, because the invert option is applied before the call;
- `prevPitch` and `prevYaw` are moved by the same change.

Nothing else calls it. Both call sites need wrapping.

**Third person.** `GameOptions.perspective`: 0 is first person, 1 behind, 2 in front. F5 is handled in `MinecraftClient.tick()`: `togglePerspectiveKey.wasPressed()` → `++perspective`, wrapping past 2 to 0, then `gameRenderer.onCameraEntitySet(...)` and `worldRenderer.scheduleTerrainUpdate()`.

---

## Confidence and gaps

**Established here.** Every name, signature, call site and constant above was read from this build's own jars with `javap`. The Fabric and Legacy Fabric mixin sources were read from the sources jars Loom remapped for this build, and the key Fabric one, `GuiMixin.wrapCrosshair`, was checked against its compiled annotation. The GL enum values come from the LWJGL 2 jar in the Gradle cache. One claim is **[DOCS]**: the `GL_INTERPOLATE` formula.

### What contradicts or sharpens the spec

1. **The 1.21.11 cooldown indicator *is* inside the crosshair element.** It is drawn in `Gui.renderCrosshair` when `attackIndicator` is `CROSSHAIR`, the default. Replacing `VanillaHudElements.CROSSHAIR` removes it. The spec's wording, "On 1.21.11 the element registry can replace the game's crosshair element", is true, but that replacement loses the indicator (user story 6) and inherits only the F1 rule, not third person, spectator or F3 (user story 5). Wrapping the one `blitSprite` for `hud/crosshair` inside `renderCrosshair` keeps both, and degrades to vanilla. The crosshair is therefore a mixin on both targets, not registry on one and mixin on the other.
2. **On 1.8.9 vanilla shows its crosshair in third person.** User story 5's example, "such as in third person", is true of 1.21.11 only. 1.8.9 also draws the whole HUD, crosshair included, with F1 on whenever a screen is open. "Follows the game's own rules on each target" means the two targets behave differently here.
3. **The tab list shows bars, never a number.** `getLatency()` is the value the bars are drawn from, so a readout of it cannot disagree with the tab list (user story 15). But the vanilla server refreshes it only every 601 ticks, as an average of keepalive round trips. The readout will move about twice a minute, not with each lag spike. Whether that serves user story 14 is a product question.
4. **Hurt-to-attack matching is exact on 1.21.11 and a guess on 1.8.9.** The damage event carries `sourceCauseId`. The 1.8.9 status packet carries no attacker, so a status 2 has to be tied to the player's own `attackEntity` by entity id and time. Someone else hitting the same target inside the window would light the indicator. The right window was not established.
5. **Entity event 2 is not "hurt" on 1.21.11.** It is `KINETIC_HIT`. A ticket that reuses the 1.8.9 status number on 1.21.11 would be wrong.
6. **The hurt-animation packet is about the player themselves.** It is not a signal for hitting others.
7. **`Player.attack`'s success path runs on the click, on both targets, when the target is a player.** `RemotePlayer.hurtClient` and `OtherClientPlayerEntity.damage` both return `true`. It must not be the hit indicator's trigger.
8. **1.21.11 spear jabs name no target on the client.** `piercingAttack` sends `STAB` with no entity, so only the damage event's cause id catches them. `AttackEntityCallback` does not fire for them.
9. **"Hidden in singleplayer" needs the right call on 1.21.11.** `isSingleplayer()` turns false when the world is opened to LAN; `hasSingleplayerServer()` does not.
10. **Hit colour on 1.21.11 is global.** It is one texture, so one colour for every living entity's flash, including the ender dragon. Strength is stored as the inverse of the texel's alpha. Armour takes no flash on either target, so hit colour cannot tint armour without a further change to which layers are tinted.
11. **1.8.9 freelook needs more than the camera transform.** Terrain visibility (`WorldRenderer.setupTerrain`, `getFacing`) and particle facing (`Camera.update`) read the entity's own `yaw` and `pitch`. 1.21.11's `LevelRenderer` reads the camera instead.

### What was not established

- **Nothing was run.** No mixin was written, built or loaded, so no intermediary name was read back as 0003's section 7 did. `AshMixinsLandTest` and the real-game tests remain the proof.
- **Sodium was not checked.** Nobody has checked whether Sodium on 1.21.11 overwrites `Gui.renderCrosshair`, `LivingEntityRenderer`, the entity shader's use of `Sampler1`, or the section culling that follows `Camera`. Spec item 3's research, or the Sodium test run, has to settle it.
- **Server behaviour is vanilla's.** The latency cadence, the damage-event conditions and the arrow event were read from the vanilla server in these jars. Proxies and forks such as BungeeCord, Velocity and Paper, and the 1.8.9 PvP networks, may rewrite latency, push it at other intervals, or send hurt signals differently. That is unverified.
- **Cause ids per weapon** on 1.21.11: the damage event's cause is whatever `DamageSource.getEntity()` returns. That it is the player for melee follows from `hurtServer`'s path. It was not traced for the spear's kinetic charge, tridents, fishing rods, TNT the player lit, or thorns.
- **Unnamed 1.8.9 members.** `LivingEntityRenderer.method_10252`, `method_10258` and `method_10260`, and `GameOptions.field_955`, keep their intermediary names in Legacy Yarn 604. A later Legacy Yarn build may name them, and a mixin written against these strings would then need updating when Yarn moves.
- **MixinExtras on field reads.** A 1.8.9 freelook that substitutes `Entity.yaw`/`pitch` at `getfield` sites, rather than swapping the fields for the length of the frame, depends on MixinExtras' `@WrapOperation` supporting field access, or on `@Redirect`. The bundled MixinExtras (0.5.x, per 0003) was not checked for this.
- **Freelook's standing with servers** is spec item 2, and it was not looked at here.
