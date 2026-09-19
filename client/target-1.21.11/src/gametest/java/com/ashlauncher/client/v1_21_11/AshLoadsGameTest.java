package com.ashlauncher.client.v1_21_11;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * A real Minecraft client, launched, with ash in it.
 *
 * <p>Every other tier stops short of this one. The unit tests never start the
 * game; the manifest tests only read what the build wrote. This is the tier
 * that would catch a mixin which has stopped matching its target, and
 * ADR-0017's choice to let a feature degrade rather than crash is safe only
 * because it exists — a degradation nothing detects is indistinguishable from
 * a feature that was never there.
 *
 * <p>It asserts little, deliberately. What is being proved is that the chain
 * reaches a running game at all; asserting on what is drawn comes with the
 * first feature that draws something worth asserting on.
 */
public class AshLoadsGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        // Far enough in that the client is ticking rather than merely started.
        context.waitTicks(20);

        if (!FabricLoader.getInstance().isModLoaded("ash")) {
            throw new AssertionError(
                    "the client started without ash in it, which is the one thing this tier is for");
        }

        // Kept as a CI artifact. When something does go wrong in here, the
        // difference between a log line and a picture of the screen is most of
        // the diagnosis.
        context.takeScreenshot("ash-loaded");
    }
}
