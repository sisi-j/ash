package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.crosshair.Cross;
import com.ashlauncher.client.crosshair.Crosshair;
import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.freelook.BlockList;
import com.ashlauncher.client.freelook.Freelook;
import com.ashlauncher.client.freelook.FreelookHook;
import com.ashlauncher.client.hit.HitColour;
import com.ashlauncher.client.hit.HitColourHook;
import com.ashlauncher.client.hit.HitHook;
import com.ashlauncher.client.hit.HitIndicator;
import com.ashlauncher.client.hit.RecentAttacks;
import com.ashlauncher.client.hud.HudLayout;
import com.ashlauncher.client.hud.Marker;
import com.ashlauncher.client.mixin.MixinFeature;
import com.ashlauncher.client.ping.PingReadout;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.report.ModOrigins;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.snaplook.Snaplook;
import com.ashlauncher.client.sprint.ToggleSprint;
import com.ashlauncher.client.sprint.ToggleSprintHook;
import com.ashlauncher.client.ui.draw.Ink;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * Where ash's client starts on 1.8.9.
 *
 * <p>Named in {@code fabric.mod.json} as the client entrypoint, which is also
 * what puts ash in the game's own mod list.
 *
 * <p>What it draws is {@link Marker} and {@link FpsReadout}, unchanged and
 * shared with the modern target. Everything that differs between the two is in
 * this package, and the whole of the difference is worth seeing: modern Fabric
 * API takes named elements into an ordered registry, and this takes a callback
 * that fires once after the vanilla HUD and can only draw on top. There is no
 * identifier here, no ordering and no removal - which is why the shared
 * surface is "draw this here" and never "replace element X".
 */
public final class AshClient implements ClientModInitializer {

    private static final Logger LOG = LogManager.getLogger("ash");

    /** By name: a mixin class cannot be named by a class literal, because loading one directly is an error. */
    private static final String TOGGLE_SPRINT_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.ClientPlayerEntityMixin";

    private static final String SETTINGS_KEY_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.MinecraftClientMixin";

    private static final String FREELOOK_RENDER_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.GameRendererFreelookMixin";

    private static final String FREELOOK_TICK_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.MinecraftClientFreelookMixin";

    private static final String SNAPLOOK_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.MinecraftClientSnaplookMixin";

    private static final String HIT_COLOUR_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.LivingEntityRendererHitColourMixin";

    private static final String CROSSHAIR_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.InGameHudMixin";

    private static final String HIT_ATTACK_MIXIN =
            "com.ashlauncher.client.v1_8_9.mixin.ClientPlayerInteractionManagerMixin";

    private static final String HIT_HURT_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.ClientPlayNetworkHandlerMixin";

    /**
     * The FPS readout this session draws, so the real-game test can ask it -
     * not the screenshot - whether it draws. Package-private and set once.
     */
    static FpsReadout fpsReadout;

    /** The ping readout this session draws, for the smoke test to ask. */
    static PingReadout pingReadout;

    /** The hit indicator this session draws, if both its mixins landed, for the smoke test to ask. */
    static HitIndicator hitIndicator;

    @Override
    public void onInitializeClient() {
        // Inter for ash's settings panel, loaded now on a thread of its own so
        // the first time the panel opens it is ready.
        Ink.preload(LOG::warn);
        Settings settings = Settings.load(FabricLoader.getInstance().getConfigDir());
        for (String problem : settings.problems()) {
            LOG.warn(problem);
        }

        Marker marker = new Marker();
        // One layout for the readouts and the settings screen's Edit HUD, so
        // the box the player drags is where the readout draws.
        HudLayout hudLayout = new HudLayout(settings);
        FpsReadout fpsReadout = new FpsReadout(MinecraftClient::getCurrentFps, hudLayout);
        AshClient.fpsReadout = fpsReadout;
        PingReadout pingReadout = new PingReadout(AshClient::latency, hudLayout);
        AshClient.pingReadout = pingReadout;

        HudRenderCallback.EVENT.register((minecraft, tickDelta) -> {
            // One surface for both: constructing it reads the window size and
            // GUI scale, which is once a frame's work rather than once a feature's.
            LegacyHudSurface surface = new LegacyHudSurface(minecraft);
            marker.draw(surface);
            fpsReadout.draw(surface);
            pingReadout.draw(surface);
            // Around the middle pixel of the game's crosshair, which it draws
            // at (width / 2 - 7, height / 2 - 7) with its centre on pixel 7.
            HitHook.draw(surface, surface.width() / 2, surface.height() / 2);
        });

        // Every feature, less any whose mixin did not land. The settings
        // screen and the load report both read this one set.
        Set<Feature> landed = EnumSet.allOf(Feature.class);
        boolean toggleSprintLanded = MixinFeature.landed(() -> ClientPlayerEntity.class, TOGGLE_SPRINT_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.TOGGLE_SPRINT.displayName(), why)));
        if (!toggleSprintLanded) {
            landed.remove(Feature.TOGGLE_SPRINT);
        }

        // ash's crosshair, drawn from inside the game's own crosshair drawing
        // so that every rule the game has about when to show one still holds.
        boolean crosshairLanded = MixinFeature.landed(() -> InGameHud.class, CROSSHAIR_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.CROSSHAIR.displayName(), why)));
        if (crosshairLanded) {
            // Built from the settings every frame, so a change on the settings
            // screen shows on the next one.
            CrosshairHook.install(new Crosshair(() -> settings.get(Settings.CROSSHAIR), () -> Cross.of(settings)));
        } else {
            landed.remove(Feature.CROSSHAIR);
        }

        // The hit indicator. 1.8.9's "this entity was hurt" names no attacker,
        // so it takes both halves: the player's attack, and the server's
        // hurt matched to it. Either missing, and it degrades alone.
        boolean hitAttackLanded = MixinFeature.landed(() -> ClientPlayerInteractionManager.class, HIT_ATTACK_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.HIT_INDICATOR.displayName(), why)));
        boolean hitHurtLanded = MixinFeature.landed(() -> ClientPlayNetworkHandler.class, HIT_HURT_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.HIT_INDICATOR.displayName(), why)));
        if (hitAttackLanded && hitHurtLanded) {
            hitIndicator = HitIndicator.from(settings, HitHook::clockMillis);
            HitHook.install(hitIndicator, new RecentAttacks(HitHook::clockMillis));
        } else {
            landed.remove(Feature.HIT_INDICATOR);
        }

        // R, in the game's own Movement category beside Sprint - free by
        // default on both targets, and rebindable in Controls. Registered
        // whenever the mixin landed, on or off: bindings can only be
        // registered now, at startup, and the player can switch the feature
        // on from the settings screen mid-session. While it is off, the key
        // does nothing.
        if (toggleSprintLanded) {
            KeyBinding toggleSprintKey = KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(ToggleSprint.BINDING_NAME, Keyboard.KEY_R, "key.categories.movement"));
            ToggleSprintHook.install(new ToggleSprint(new KeyBindingToggleKey(toggleSprintKey),
                    () -> settings.get(Settings.TOGGLE_SPRINT)));
        }

        // Right Shift, which neither target binds by default, read by ash's
        // own tick mixin. MinecraftClient is loaded long before this runs, so
        // asking whether that mixin landed loads nothing new.
        boolean settingsKeyLanded = MixinFeature.landed(() -> MinecraftClient.class, SETTINGS_KEY_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.SETTINGS_SCREEN.displayName(), why)));
        if (!settingsKeyLanded) {
            landed.remove(Feature.SETTINGS_SCREEN);
        }

        // Freelook: the mouse diverted and the view turned in the game
        // renderer, and its key read on the client's tick. Both mixins or
        // neither, as on 1.21.11.
        boolean freelookLanded = MixinFeature.landed(() -> GameRenderer.class, FREELOOK_RENDER_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.FREELOOK.displayName(), why)))
                && MixinFeature.landed(() -> MinecraftClient.class, FREELOOK_TICK_MIXIN,
                        why -> LOG.warn(MixinFeature.didNotLoad(Feature.FREELOOK.displayName(), why)));
        if (freelookLanded) {
            // Left Alt, which the game binds to nothing. Under Misc, beside
            // ash's settings key; rebindable in Controls.
            KeyBinding freelookKey = KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(Freelook.BINDING_NAME, Keyboard.KEY_LMENU, "key.categories.misc"));
            Freelook<Integer> freelook = new Freelook<>(new Perspective(),
                    () -> settings.get(Settings.FREELOOK), CurrentServer::blockedHere,
                    why -> MinecraftClient.getInstance().inGameHud.setOverlayMessage(why, false));
            FreelookHook.install(freelook);
            FreelookKey.install(freelookKey, freelook);
        } else {
            landed.remove(Feature.FREELOOK);
        }

        // Hit colour: the renderer's hurt flash, refilled per draw from the
        // settings, so a change shows on the next frame.
        boolean hitColourLanded = MixinFeature.landed(() -> LivingEntityRenderer.class, HIT_COLOUR_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.HIT_COLOUR.displayName(), why)));
        if (hitColourLanded) {
            HitColourHook.install(new HitColour(settings));
        } else {
            landed.remove(Feature.HIT_COLOUR);
        }

        // Snaplook: the game's own front view while Z is held, Z being a key
        // the game binds to nothing; read on the client tick by its own mixin.
        boolean snaplookLanded = MixinFeature.landed(() -> MinecraftClient.class, SNAPLOOK_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.SNAPLOOK.displayName(), why)));
        if (snaplookLanded) {
            KeyBinding snaplookKey = KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(Snaplook.BINDING_NAME, Keyboard.KEY_Z, "key.categories.misc"));
            SnaplookKey.install(snaplookKey,
                    new Snaplook<>(new Perspective(), () -> settings.get(Settings.SNAPLOOK)));
        } else {
            landed.remove(Feature.SNAPLOOK);
        }

        Runnable writeReport = () -> writeLoadReport(settings, landed);
        if (settingsKeyLanded) {
            // Freelook's row says why it is off on a listed server, and cannot
            // be switched there, as on 1.21.11.
            SettingsScreen settingsScreen = new SettingsScreen(settings, feature -> true, landed::contains,
                    feature -> {
                        BlockList.Server here = feature == Feature.FREELOOK ? CurrentServer.blockedHere() : null;
                        return here == null ? "" : here.whyOff();
                    },
                    writeReport, hudLayout);
            KeyBinding settingsKey = KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(SettingsScreen.BINDING_NAME, Keyboard.KEY_RSHIFT, "key.categories.misc"));
            SettingsKey.install(settingsKey, settingsScreen);
        }

        writeReport.run();
    }

    /**
     * What this session is running, for the launcher - written at startup and
     * again whenever a setting changes, so the report says what the session
     * ended with.
     */
    private static void writeLoadReport(Settings settings, Set<Feature> landed) {
        try {
            LoadReport.forSession(clientVersion(), landed::contains, settings::on)
                    .withOrigins(modOrigins(), BUNDLED)
                    .writeTo(FabricLoader.getInstance().getGameDir());
        } catch (IOException unwritable) {
            LOG.warn("ash: could not write the load report for the launcher: " + unwritable);
        }
    }

    /**
     * ash's bundled mods on this target, by mod id: the copies the loader
     * could have swapped for a player's own. Ids, not file names - one of
     * Legacy Fabric's differs from its jar's name.
     */
    private static final List<String> BUNDLED = Arrays.asList("legacy-fabric-api", "legacy-fabric-api-base-common", "legacy-fabric-keybinding-api-v1-common", "legacy-fabric-rendering-api-v1", "legacy-fabric-rendering-api-v1-common");

    /**
     * Where each loaded mod came from, read from the loader, for the report to
     * say whether the player's own mods were involved.
     */
    private static ModOrigins modOrigins() {
        FabricLoader loader = FabricLoader.getInstance();
        // ash moves the folder while the player's mods are off; unset, it is the instance's own.
        String moved = System.getProperty("fabric.modsFolder");
        Path modsFolder = moved != null ? Paths.get(moved) : loader.getGameDir().resolve("mods");
        Map<String, List<Path>> files = new HashMap<>();
        for (ModContainer mod : loader.getAllMods()) {
            List<Path> from = filesOf(loader, mod, 0);
            if (!from.isEmpty()) {
                files.put(mod.getMetadata().getId(), from);
            }
        }
        return ModOrigins.of(modsFolder, files);
    }

    /** The jar a mod came in: its own, or for one nested in another, the outermost. */
    private static List<Path> filesOf(FabricLoader loader, ModContainer mod, int depth) {
        ModOrigin origin = mod.getOrigin();
        if (origin.getKind() == ModOrigin.Kind.PATH) {
            return origin.getPaths();
        }
        if (origin.getKind() == ModOrigin.Kind.NESTED && depth < 8) {
            return loader.getModContainer(origin.getParentModId())
                    .map(parent -> filesOf(loader, parent, depth + 1))
                    .orElse(Collections.<Path>emptyList());
        }
        return Collections.emptyList();
    }

    /**
     * The server's latency for the player's own tab-list entry, the number
     * behind the tab list's bars; null in singleplayer, which on 1.8.9 stays
     * true after opening to LAN (research 0004, 3.2), or before the server has
     * listed the player. Read, never measured.
     */
    static Integer latency() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isInSingleplayer() || client.player == null || client.getNetworkHandler() == null) {
            return null;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? null : entry.getLatency();
    }

    private static String clientVersion() {
        return FabricLoader.getInstance().getModContainer("ash")
                .map(ash -> ash.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
