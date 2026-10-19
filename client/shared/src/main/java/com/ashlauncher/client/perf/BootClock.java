package com.ashlauncher.client.perf;

import java.util.function.LongSupplier;

/**
 * How long the game took to reach its title screen, from the moment its
 * process started: written once to the game's log, as
 * {@code ash: title screen N ms after the game started}.
 *
 * <p>Said to every player, since it costs nothing and answers "is it ash or
 * the game that's slow to start". The boot measurement also sets
 * {@code ash.boot.exitAtTitle}, so each run stops as soon as it has said it.
 */
public final class BootClock {

    public static final String EXIT_AT_TITLE = "ash.boot.exitAtTitle";

    private static boolean said;

    private BootClock() {}

    /**
     * Called every client tick with whether the title screen is up.
     *
     * @param processStartMillis when the game's process started, or a
     *     negative number when the platform can't say
     * @param stop asks the game to quit, for the boot measurement
     */
    public static void tick(boolean titleScreen, LongSupplier processStartMillis, Runnable stop) {
        if (said || !titleScreen) {
            return;
        }
        said = true;
        long started = processStartMillis.getAsLong();
        if (started > 0) {
            System.out.println("ash: title screen " + (System.currentTimeMillis() - started)
                    + " ms after the game started");
        }
        if (Boolean.getBoolean(EXIT_AT_TITLE)) {
            stop.run();
        }
    }
}
