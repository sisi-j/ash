package com.ashlauncher.client.report;

/** A version of the game ash's client is built for. */
public enum Target {
    V1_8_9("1.8.9"),
    V1_21_11("1.21.11");

    private final String version;

    Target(String version) {
        this.version = version;
    }

    /** The game's version, as the launcher and the benchmark's file names write it. */
    public String version() {
        return version;
    }
}
