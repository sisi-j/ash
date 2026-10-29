package com.ashlauncher.client.settings;

import com.ashlauncher.client.report.Feature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Features in an order the player chose: the panel's favourites. Written as
 * their ids, separated by commas: {@code freelook,crosshair}.
 *
 * <p>Set from the panel, by starring a tile, rather than on an options page.
 */
public final class FeatureList extends Setting<List<Feature>> {

    FeatureList(Feature feature, String key, String label, String comment) {
        super(feature, key, label, Collections.<Feature>emptyList(), comment);
    }

    /**
     * Ids ash knows, each once, in the order written. An id it doesn't know -
     * one a newer ash on another computer synced here - is left out rather
     * than costing the player the whole list.
     */
    @Override
    List<Feature> parse(String raw) {
        List<Feature> features = new ArrayList<>();
        for (String id : raw.split(",")) {
            for (Feature feature : Feature.values()) {
                if (feature.id().equals(id.trim()) && !features.contains(feature)) {
                    features.add(feature);
                }
            }
        }
        return Collections.unmodifiableList(features);
    }

    @Override
    String format(List<Feature> value) {
        StringBuilder written = new StringBuilder();
        for (Feature feature : value) {
            if (written.length() > 0) {
                written.append(',');
            }
            written.append(feature.id());
        }
        return written.toString();
    }

    @Override
    String expected() {
        return "feature ids, separated by commas";
    }

    @Override
    List<Feature> normalise(List<Feature> value) {
        return parse(format(value));
    }
}
