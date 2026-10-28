package com.ashlauncher.client;

/**
 * Whether ash's beta work runs in this game: the beta switch (spec 0006,
 * ADR-0029).
 *
 * <p>The client has no switch of its own. Its jar is the same in a test build
 * and a release, and the launcher that started the game says which it was:
 * {@code -Dash.beta=true} from a test build, nothing from a release. A beta
 * feature asks {@link #on()} before it does anything, and does nothing when
 * the answer is no.
 *
 * <p>Every Gradle run of the game here - the game tests, the benchmarks, a
 * developer's own - says yes, so beta work is tested on every pull request.
 */
public final class Beta {

    /** The JVM property the launcher sets. */
    public static final String PROPERTY = "ash.beta";

    private Beta() {
    }

    /** Whether the launcher that started this game was a test build. */
    public static boolean on() {
        return on(System.getProperty(PROPERTY));
    }

    /** Only the launcher's own word, exactly: anything else is a release. */
    static boolean on(String property) {
        return "true".equals(property);
    }
}
