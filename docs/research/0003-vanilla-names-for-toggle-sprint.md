# The vanilla names toggle sprint depends on, per version target, and how each was established

Research date: 2026-09-24. Established while implementing #24.

Every claim below is **[PRACTICE]**: read out of the mapped game jars Loom produces for this repository's own build, with `javap -c -p`, not recalled from a wiki, a mapping browser or another mod. The jars:

- **1.21.11**, Mojang's names: `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2/minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2.jar`
- **1.8.9**, Legacy Yarn build 604: `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-legacy-intermediary-1-v2/1.8.9-net.legacyfabric.yarn.1_8_9.1.8.9+build.604-v2/minecraft-merged-legacy-intermediary-1-v2-1.8.9-net.legacyfabric.yarn.1_8_9.1.8.9+build.604-v2.jar`

The ticket asked for this pass because *"a plausible-looking wrong name is worse than a gap."* Two of the names below would have been guessed wrong.

---

## Summary

| | 1.21.11 | 1.8.9 |
| --- | --- | --- |
| Where the game reads the sprint key | `net.minecraft.client.player.KeyboardInput.tick()` | `net.minecraft.entity.player.ClientPlayerEntity.tickMovement()` |
| How many times, per tick | once, as the 7th argument to `new Input(ZZZZZZZ)` | **twice** - once per way the player can start sprinting |
| The key read | `Options.keySprint` | `GameOptions.sprintKey` |
| "Is it held" | `KeyMapping.isDown()` | `KeyBinding.isPressed()` |
| "Consume one press" | `KeyMapping.consumeClick()` | `KeyBinding.wasPressed()` |
| A key repeat counts as a press | **yes** | no key repeats in game |
| Registering a binding | `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(KeyMapping)` | `net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(KeyBinding)` |
| Binding category | `KeyMapping.Category.MOVEMENT` | the string `"key.categories.movement"` |
| Default key chosen | R (`GLFW_KEY_R`, 82) | R (`Keyboard.KEY_R`, 19) |

## 1. Where the sprint key is read

The mixin has to change the one answer the game uses to decide whether to sprint, so the first question is where that answer is asked. Guessing "the player's tick" is not enough on either target.

**1.21.11.** A scan of every class under `net/minecraft/client/` (2,689 of them) for a read of `Options.keySprint` finds exactly one: `KeyboardInput.tick()`. The bytecode builds the tick's movement input as a record:

```
69: getfield      Options.keySprint:KeyMapping
72: invokevirtual KeyMapping.isDown:()Z
75: invokespecial Input."<init>":(ZZZZZZZ)V
```

`Input`'s components, in order, are `forward, backward, left, right, jump, shift, sprint`, so the sprint key is the seventh argument. `tick()` calls `isDown()` **seven times**, once per key.

**1.8.9.** The same scan across all 1,628 classes in the jar finds one method: `ClientPlayerEntity.tickMovement()`. The class is in `net.minecraft.entity.player`, not `net.minecraft.client.network` where modern Yarn has it - the first guess, and wrong. It reads `GameOptions.sprintKey` **twice**:

```
653: getfield      GameOptions.sprintKey:KeyBinding
656: invokevirtual KeyBinding.isPressed:()Z
659: ifeq          670        // not held: arm the double-tap-W timer instead
...
724: getfield      GameOptions.sprintKey:KeyBinding
727: invokevirtual KeyBinding.isPressed:()Z
730: ifeq          738        // held: setSprinting(true)
```

The first read is inside the double-tap-W branch, the second is the held-key branch. A mixin that caught only the second would still start a sprint, but the first read would still say "not held" and arm the double-tap timer, which is not what holding the key does. There are only these two `isPressed()` calls in `tickMovement()`.

**What that decides.** Neither target can be targeted by call position without being fragile - "the seventh `isDown()`" on one and "both `isPressed()`s" on the other. So ash's mixin wraps the call and matches the *receiver*: if the key being asked about is the game's own sprint key, the answer is replaced; any other key is untouched. That is MixinExtras' `@WrapOperation`, which Fabric Loader bundles and initialises on both targets (0.5.x on the loader's classpath).

## 2. Which method means "held"

**1.8.9, Legacy Yarn.** The names read backwards from what they suggest, and the bytecode is the only thing to trust:

- `KeyBinding.isPressed()` returns the `pressed` field. It means **held**.
- `KeyBinding.wasPressed()` returns `timesPressed > 0` and decrements it. It **consumes one press**.

**1.21.11, Mojang's names.** `KeyMapping.isDown()` is held; `KeyMapping.consumeClick()` consumes one press.

## 3. Key repeats

**1.21.11 counts a key repeat as a press.** In `KeyboardHandler.keyPress`, a release takes one path (`KeyMapping.set(key, false)` and return) and everything else - a press *and* a repeat - takes the other: `KeyMapping.set(key, true)` then `KeyMapping.click(key)`. Repeats come at the operating system's rate - on Windows' defaults about half a second's delay, then around thirty a second - so a held key reports one or two presses every tick.

That is why ash's latch cannot simply flip on every press: it would flap for as long as the key is held. A press only counts if the key was up at the previous tick, and the latch is unit-tested against exactly this (`holding_the_key_while_the_game_repeats_it_leaves_sprint_on`).

## 4. Registering the binding

The binding helper has a different class on each target - the reason the shared module can name neither:

- **1.21.11**: `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper`, from `fabric-key-binding-api-v1` inside the bundled Fabric API. `KeyMapping(String, InputConstants.Type, int, KeyMapping.Category)`; since 1.21.9 a category is a `KeyMapping.Category` record, and `KeyMapping.Category.MOVEMENT` exists.
- **1.8.9**: `net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper`, from **`legacy-fabric-keybindings-api-v1-common` 1.2.0** - the version the aggregator's POM names for Legacy Fabric API 1.13.5+1.8.9. `KeyBinding(String, int, String)`.

The Legacy Fabric module needed care:

- Two similarly named modules exist, `legacy-fabric-keybinding-api-v1` (singular) and `legacy-fabric-keybindings-api-v1`. The aggregator's POM names only the second; the first stops at `1.0.0+` builds with commit hashes and nothing for 1.8.9.
- Unlike the rendering API, the split is the other way round. `legacy-fabric-keybindings-api-v1-1.2.0+1.8.9.jar` holds **no classes at all**. `-common-1.2.0.jar` holds `KeyBindingHelper`, its implementation and both mixins (`GameOptionsMixin`, `MinecraftClientMixin`).
- `-common` references no other Legacy Fabric package, so nothing transitive is missing. It is the one artifact ash pins, mirrors and ships for this feature.
- Its sha1, `768a633f036d6fc0a49ad653a4172b679fa32d78`, matches the `.sha1` sidecar Legacy Fabric publishes.
- **Its mod id is singular** - `legacy-fabric-keybinding-api-v1-common`, read from its own `fabric.mod.json` - while its Maven artifact is plural. So ash's 1.8.9 `fabric.mod.json` depends on the singular name, and that is correct: a `depends` names a mod id, not an artifact. It is the mismatch between the two that explains why the Maven listing has both spellings, and a reader who "fixes" the `depends` to match the artifact breaks the client.

## 5. The default key

The ticket requires a binding that "does not collide with existing binds". The defaults were read from each game's options constructor, not from a list:

- **1.21.11** binds A B C D E F G H I L N P Q S T V W X by default - including the F3-combination keys that became real key mappings in 1.21.9 - plus the digits, Space, Tab, `/`, the function keys and the mouse.
- **1.8.9** binds W A S D E Q T, the digits, Space, Tab, `/`, LShift, LCtrl, F2, F5, F6, F7, F11 and the mouse.

Free on both: J K M O R U Y Z. ash uses **R**. Both runtime tests check again in a running game, against every binding the game actually has, that no other binding shares its default - `AshLoadsGameTest` on 1.21.11 and `AshSmokeTest` on 1.8.9.

## 6. The name the binding is shown under

1.8.9 loads no mod assets without another Legacy Fabric module (the resource loader), so a translation key would appear in Controls as the raw key. The binding is named with its display text, `Toggle Sprint`, on both targets: the game translates a name it has no translation for as the name itself, so it reads the same on both, and one fewer module is shipped.

## 7. How the mixins were checked to survive production

A mixin that compiles against Mojang's or Legacy Yarn's names still has to find its target under the names the game uses at runtime, which on both targets are an intermediary's. Neither jar ships a refmap; Loom rewrites the names in the mixin's own bytecode when it remaps the jar. Read back from the built jars:

- **1.21.11**: `@Mixin(class_743)`, both injectors on `method=["method_3129"]`, and the wrapped call `Lnet/minecraft/class_304;method_1434()Z`.
- **1.8.9**: `@Mixin(class_518)`, both injectors on `method=["method_2651"]`.

The first CI run with toggle sprint then exercised both, in a world, with a key press.
