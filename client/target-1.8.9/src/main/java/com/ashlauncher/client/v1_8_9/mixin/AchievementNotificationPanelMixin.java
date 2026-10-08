package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.AshSettingsScreen;
import net.minecraft.advancement.Achievement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.AchievementNotification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.8.9 draws its achievement notification - for a new player, the lasting
 * "Press 'E' to open your inventory" - over every screen, ash's panel
 * included, where it covers the panel's category tabs. While the panel is
 * open, it is not drawn; it comes back when the panel closes.
 *
 * <p>Only how the panel looks rests on this, so it is no feature of its own
 * and is not reported: if it does not land, the notification is drawn over
 * the panel as the game always has.
 */
@Mixin(AchievementNotification.class)
abstract class AchievementNotificationPanelMixin {

    @Shadow
    private MinecraftClient client;

    @Shadow
    private Achievement achievement;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ash$notOverThePanel(CallbackInfo info) {
        if (AshSettingsScreen.hidesNotification(client.currentScreen, achievement != null)) {
            info.cancel();
        }
    }
}
