package com.ashlauncher.client.v1_8_9;

import java.util.Locale;

/**
 * A key's name as a player says it. LWJGL 2 names keys as constants -
 * "RSHIFT" - where 1.21.11 says "Right Shift", and ash's panel should read the
 * same on both. The modifier keys are spelled out; any other name is made
 * sentence case, so "GRAVE" reads "Grave" and "R" stays "R".
 */
final class KeyNames {

    private KeyNames() {
    }

    static String readable(String lwjglName) {
        switch (lwjglName) {
            case "RSHIFT": return "Right Shift";
            case "LSHIFT": return "Left Shift";
            case "RCONTROL": return "Right Control";
            case "LCONTROL": return "Left Control";
            case "RMENU": return "Right Alt";
            case "LMENU": return "Left Alt";
            case "RETURN": return "Enter";
            default:
                return lwjglName.length() <= 1 ? lwjglName
                        : lwjglName.charAt(0) + lwjglName.substring(1).toLowerCase(Locale.ROOT);
        }
    }
}
