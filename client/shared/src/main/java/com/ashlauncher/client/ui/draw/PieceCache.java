package com.ashlauncher.client.ui.draw;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which rasters a target keeps uploaded, decided once, here, for both
 * targets: each target only says how to upload one and how to free one.
 *
 * <p>A raster is uploaded the first time it is drawn and kept while it is in
 * use. Past a cap on uploaded pixels, the least recently drawn are freed -
 * but never one drawn this frame: 1.21.11 renders its GUI after the screen
 * submits it, and a texture freed before then would draw as missing.
 *
 * @param <T> the target's handle for an uploaded raster
 */
public final class PieceCache<T> {

    /** How a target uploads a raster and frees it. */
    public interface Uploader<T> {
        T upload(Raster raster);

        void release(T handle);
    }

    private static final class Uploaded<T> {
        final T handle;
        long frame;

        Uploaded(T handle, long frame) {
            this.handle = handle;
            this.frame = frame;
        }
    }

    private final long maxPixels;
    private final Uploader<T> uploader;
    private final LinkedHashMap<Raster, Uploaded<T>> uploaded = new LinkedHashMap<>(256, 0.75f, true);
    private long pixels;
    private long frame;

    public PieceCache(long maxPixels, Uploader<T> uploader) {
        this.maxPixels = maxPixels;
        this.uploader = uploader;
    }

    /** A new frame has begun: what was drawn in the last one may now be freed. */
    public void nextFrame() {
        frame++;
    }

    /** The raster's handle, uploading it first if it is not uploaded. */
    public T get(Raster raster) {
        Uploaded<T> known = uploaded.get(raster);
        if (known != null) {
            known.frame = frame;
            return known.handle;
        }
        Uploaded<T> fresh = new Uploaded<>(uploader.upload(raster), frame);
        uploaded.put(raster, fresh);
        pixels += (long) raster.width() * raster.height();
        evict();
        return fresh.handle;
    }

    /** How many rasters are uploaded: for tests. */
    int size() {
        return uploaded.size();
    }

    private void evict() {
        Iterator<Map.Entry<Raster, Uploaded<T>>> oldest = uploaded.entrySet().iterator();
        while (pixels > maxPixels && oldest.hasNext()) {
            Map.Entry<Raster, Uploaded<T>> entry = oldest.next();
            if (entry.getValue().frame == frame) {
                // Everything after it was drawn this frame too.
                return;
            }
            uploader.release(entry.getValue().handle);
            pixels -= (long) entry.getKey().width() * entry.getKey().height();
            oldest.remove();
        }
    }
}
