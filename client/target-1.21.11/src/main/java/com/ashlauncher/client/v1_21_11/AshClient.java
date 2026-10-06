package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.crosshair.Cross;
import com.ashlauncher.client.crosshair.Crosshair;
import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.freelook.BlockList;
import com.ashlauncher.client.freelook.Freelook;
import com.ashlauncher.client.freelook.FreelookHook;
import com.ashlauncher.client.hit.HitHook;
import com.ashlauncher.client.hit.HitIndicator;
import com.ashlauncher.client.hud.Marker;
import com.ashlauncher.client.mixin.MixinFeature;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.report.ModOrigins;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.sprint.ToggleSprint;
import com.ashlauncher.client.sprint.ToggleSprintHook;
import com.ashlauncher.client.ui.draw.Ink;
import com.mojang.blaze3d.platform.InputConstants;
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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Where ash's client starts on 1.21.11.
 *
 * <p>Named in {@code fabric.mod.json} as the client entrypoint, which is also
 * what puts ash in the game's own mod list.
 */
public final class AshClient implements ClientModInitializer {

    /** Log4j rather than SLF4J so that both targets log the same way. */
    private static final Logger LOG = LogManager.getLogger("ash");

    /**
     * Modern Fabric API names every HUD element, and ours are no exception -
     * a named element can be reordered or removed later without anyone having
     * to find it first. The 1.8.9 API has no such thing, which is why the
     * identifiers stop here and never reach the shared module.
     */
    private static final Identifier MARKER = Identifier.fromNamespaceAndPath("ash", "marker");

    private static final Identifier FPS_READOUT = Identifier.fromNamespaceAndPath("ash", "fps_readout");

    private static final Identifier HIT_INDICATOR = Identifier.fromNamespaceAndPath("ash", "hit_indicator");

    /** By name: a mixin class cannot be named by a class literal, because loading one directly is an error. */
    private static final String TOGGLE_SPRINT_MIXIN = "com.ashlauncher.client.v1_21_11.mixin.KeyboardInputMixin";

    private static final String CROSSHAIR_MIXIN = "com.ashlauncher.client.v1_21_11.mixin.GuiCrosshairMixin";

    private static final String HIT_INDICATOR_MIXIN = "com.ashlauncher.client.v1_21_11.mixin.ClientPacketListenerMixin";

    private static final String FREELOOK_CAMERA_MIXIN = "com.ashlauncher.client.v1_21_11.mixin.CameraMixin";

    private static final String FREELOOK_MOUSE_MIXIN = "com.ashlauncher.client.v1_21_11.mixin.MouseHandlerMixin";

    /**
     * The FPS readout this session draws, so the real-game test can ask it -
     * not the screenshot - whether it draws. Package-private and set once.
     */
    static FpsReadout fpsReadout;

    /** The hit indicator this session draws, if its mixin landed, for the real-game test to ask. */
    static HitIndicator hitIndicator;

    /** Freelook and its key, if its mixins landed, for the real-game test to drive and ask. */
    static Freelook<CameraType> freelook;

    static KeyMapping freelookKey;

    @Override
    public void onInitializeClient() {
        // Inter for ash's settings panel, loaded now on a thread of its own so
        // the first time the panel opens it is ready.
        Ink.preload(LOG::warn);
        Settings settings = Settings.load(FabricLoader.getInstance().getConfigDir());
        settings.problems().forEach(LOG::warn);

        Marker marker = new Marker();
        FpsReadout fpsReadout = new FpsReadout(
                () -> Minecraft.getInstance().getFps(), () -> settings.get(Settings.FPS_READOUT));
        AshClient.fpsReadout = fpsReadout;

        // Last, so nothing vanilla draws over them. That is a decision about
        // this target's element registry rather than about either feature, so
        // it lives here. It is also why both check `hudHidden()` themselves:
        // Fabric documents that `addLast` inherits no render condition, so
        // unlike every vanilla element but the sleep overlay, these would
        // otherwise draw through F1.
        HudElementRegistry.addLast(MARKER, (graphics, tickCounter) ->
                marker.draw(new GuiGraphicsHudSurface(graphics)));
        HudElementRegistry.addLast(FPS_READOUT, (graphics, tickCounter) ->
                fpsReadout.draw(new GuiGraphicsHudSurface(graphics)));

        // Every feature, less any whose mixin did not land. The settings
        // screen and the load report both read this one set.
        Set<Feature> landed = EnumSet.allOf(Feature.class);
        boolean toggleSprintLanded = MixinFeature.landed(() -> KeyboardInput.class, TOGGLE_SPRINT_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.TOGGLE_SPRINT.displayName(), why)));
        if (!toggleSprintLanded) {
            landed.remove(Feature.TOGGLE_SPRINT);
        }

        // ash's crosshair, drawn from inside the game's own crosshair drawing
        // so that every rule the game has about when to show one still holds.
        boolean crosshairLanded = MixinFeature.landed(() -> Gui.class, CROSSHAIR_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.CROSSHAIR.displayName(), why)));
        if (crosshairLanded) {
            // Built from the settings every frame, so a change on the settings
            // screen shows on the next one.
            CrosshairHook.install(new Crosshair(() -> settings.get(Settings.CROSSHAIR), () -> Cross.of(settings)));
        } else {
            landed.remove(Feature.CROSSHAIR);
        }

        // The hit indicator: the server's damage event names who caused it, so
        // a hit is the player's exactly, with no guessing from their clicks.
        boolean hitIndicatorLanded = MixinFeature.landed(() -> ClientPacketListener.class, HIT_INDICATOR_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.HIT_INDICATOR.displayName(), why)));
        if (hitIndicatorLanded) {
            hitIndicator = HitIndicator.from(settings, HitHook::clockMillis);
            HitHook.install(hitIndicator, null);
            // Around the middle pixel of the game's crosshair, which it draws
            // at ((width - 15) / 2, (height - 15) / 2) with its centre on pixel 7.
            HudElementRegistry.addLast(HIT_INDICATOR, (graphics, tickCounter) ->
                    HitHook.draw(new GuiGraphicsHudSurface(graphics), (graphics.guiWidth() - 15) / 2 + 7,
                            (graphics.guiHeight() - 15) / 2 + 7));
        } else {
            landed.remove(Feature.HIT_INDICATOR);
        }

        // R, under Movement beside the game's own Sprint. Free by default on
        // both targets - read from each game's options, not from a list - and
        // rebindable in Controls. Registered whenever the mixin landed, on or
        // off: bindings can only be registered now, at startup, and the player
        // can switch the feature on from the settings screen mid-session.
        // While it is off, the key does nothing.
        if (toggleSprintLanded) {
            KeyMapping toggleSprintKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    ToggleSprint.BINDING_NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
                    KeyMapping.Category.MOVEMENT));
            ToggleSprintHook.install(new ToggleSprint(new KeyMappingToggleKey(toggleSprintKey),
                    () -> settings.get(Settings.TOGGLE_SPRINT)));
        }

        // Freelook: the mouse's turn diverted from the player, and the camera's
        // angles answered from freelook, while its key is held. Both mixins or
        // neither: a camera that turns with nothing turning it, or a mouse that
        // turns nothing, is not freelook.
        boolean freelookLanded = MixinFeature.landed(() -> Camera.class, FREELOOK_CAMERA_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.FREELOOK.displayName(), why)))
                && MixinFeature.landed(() -> MouseHandler.class, FREELOOK_MOUSE_MIXIN,
                        why -> LOG.warn(MixinFeature.didNotLoad(Feature.FREELOOK.displayName(), why)));
        CurrentServer currentServer = new CurrentServer(LOG::info);
        if (freelookLanded) {
            // Left Alt, which the game binds to nothing. Under Misc, beside
            // ash's settings key; rebindable in Controls.
            KeyMapping freelookKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    Freelook.BINDING_NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
                    KeyMapping.Category.MISC));
            Freelook<CameraType> freelook = new Freelook<>(new OptionsCameraModes(),
                    () -> settings.get(Settings.FREELOOK), CurrentServer::blockedHere,
                    why -> Minecraft.getInstance().gui.setOverlayMessage(Component.literal(why), false));
            FreelookHook.install(freelook);
            AshClient.freelook = freelook;
            AshClient.freelookKey = freelookKey;
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.player != null) {
                    freelook.tick(freelookKey.isDown(), client.screen != null, client.player.getYRot(),
                            client.player.getXRot());
                }
            });
        } else {
            landed.remove(Feature.FREELOOK);
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> currentServer.tick());

        Runnable writeReport = () -> writeLoadReport(settings, landed);
        // Freelook's row says why it is off on a listed server, and cannot be
        // switched there, as the spec's settings screen asks.
        SettingsScreen settingsScreen = new SettingsScreen(settings, feature -> true, landed::contains,
                feature -> {
                    BlockList.Server here = feature == Feature.FREELOOK ? CurrentServer.blockedHere() : null;
                    return here == null ? "" : here.whyOff();
                },
                writeReport);

        // Right Shift, which neither target binds by default. Polled on Fabric
        // API's client tick, which is inside the Fabric API ash already ships.
        // A binding gets no presses while a screen is open, so this only ever
        // opens the screen; the screen closes itself on the same key.
        KeyMapping settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                SettingsScreen.BINDING_NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new AshSettingsScreen(settingsScreen, settingsKey));
                }
            }
        });

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
    private static final List<String> BUNDLED = Arrays.asList("fabric-api");

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

    private static String clientVersion() {
        return FabricLoader.getInstance().getModContainer("ash")
                .map(ash -> ash.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
