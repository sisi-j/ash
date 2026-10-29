package com.ashlauncher.client.game;

/**
 * A game that answers whatever the test filled in, the way
 * {@link com.ashlauncher.client.hud.FakeHudSurface} stands in for the HUD.
 *
 * <p>Starts as a player in a world with nothing open, in singleplayer. A test
 * that needs a fact this can't give widens the fake; it never reaches around
 * it.
 */
public final class FakeGameView implements GameView {

    private int framesPerSecond = 60;
    private Integer latency;
    private boolean inWorld = true;
    private boolean debugScreenShown;
    private boolean chatOpen;
    private String serverAddress;

    /** The same game, counting this many frames a second. */
    public FakeGameView withFramesPerSecond(int framesPerSecond) {
        this.framesPerSecond = framesPerSecond;
        return this;
    }

    /** The same game, with the server reporting this latency; {@code null} for none, as in singleplayer. */
    public FakeGameView withLatency(Integer latency) {
        this.latency = latency;
        return this;
    }

    /** The same game, with the player not in a world yet: joining, say. */
    public FakeGameView outOfWorld() {
        inWorld = false;
        return this;
    }

    /** The same game, with the player in the world again. */
    public FakeGameView backInWorld() {
        inWorld = true;
        return this;
    }

    /** The same game with F3's debug text on screen. */
    public FakeGameView withDebugScreenShown() {
        debugScreenShown = true;
        return this;
    }

    /** The same game with chat open. */
    public FakeGameView withChatOpen() {
        chatOpen = true;
        return this;
    }

    /** The same game, on the server the player joined as {@code address}. */
    public FakeGameView onServer(String address) {
        serverAddress = address;
        return this;
    }

    @Override
    public boolean inWorld() {
        return inWorld;
    }

    @Override
    public boolean debugScreenShown() {
        return debugScreenShown;
    }

    @Override
    public int framesPerSecond() {
        return framesPerSecond;
    }

    @Override
    public Integer latency() {
        return latency;
    }

    @Override
    public boolean chatOpen() {
        return chatOpen;
    }

    @Override
    public String serverAddress() {
        return serverAddress;
    }
}
