package com.ashlauncher.client.report;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where this session's mods came from, as far as the load report needs:
 * whether any of the player's own loaded, and whose copy of each of ash's
 * bundled mods won.
 *
 * <p>Decided from paths alone, so it is tested here without a game. Each
 * target reads the paths from the loader, which records the file every mod
 * was loaded from.
 */
public final class ModOrigins {

    /** ash's own client: in the player's folder only if they put it there, and never theirs. */
    private static final String ASH = "ash";

    private final Path modsFolder;
    private final Map<String, List<Path>> origins;

    private ModOrigins(Path modsFolder, Map<String, List<Path>> origins) {
        this.modsFolder = normal(modsFolder);
        this.origins = origins;
    }

    /**
     * @param modsFolder the folder the loader read the player's mods from:
     *     the instance's own {@code mods} with them on, or ash's empty one
     * @param origins each loaded mod's id and the files it came from: its own
     *     jar, or for a mod nested inside another, the outermost jar it is in
     */
    public static ModOrigins of(Path modsFolder, Map<String, List<Path>> origins) {
        return new ModOrigins(modsFolder, new LinkedHashMap<>(origins));
    }

    /** Whether any mod but ash's own was loaded from the player's folder. */
    public boolean thirdPartyModsLoaded() {
        for (Map.Entry<String, List<Path>> mod : origins.entrySet()) {
            if (!mod.getKey().equals(ASH) && fromModsFolder(mod.getValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whose copy of each of these loaded, in the order given. A mod that did
     * not load at all is left out: there is no copy to speak of.
     */
    public Map<String, BundledCopy> bundled(List<String> ids) {
        Map<String, BundledCopy> copies = new LinkedHashMap<>();
        for (String id : ids) {
            List<Path> paths = origins.get(id);
            if (paths != null) {
                copies.put(id, fromModsFolder(paths) ? BundledCopy.PLAYER : BundledCopy.ASH);
            }
        }
        return Collections.unmodifiableMap(copies);
    }

    private boolean fromModsFolder(List<Path> paths) {
        for (Path path : paths) {
            if (normal(path).startsWith(modsFolder)) {
                return true;
            }
        }
        return false;
    }

    private static Path normal(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
