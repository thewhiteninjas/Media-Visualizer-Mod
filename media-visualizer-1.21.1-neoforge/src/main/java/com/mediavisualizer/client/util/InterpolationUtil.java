package com.mediavisualizer.client.util;

/**
 * Provides in-place, allocation-free interpolation over float arrays representing
 * per-bar magnitude values. All methods mutate the {@code current} array directly so
 * that the renderer and analyzer can reuse the same buffers frame after frame.
 */
public final class InterpolationUtil {

    private InterpolationUtil() {
        // Utility class - no instances.
    }

    /**
     * Interpolates every element of {@code current} towards the matching element of {@code target},
     * writing the result back into {@code current}. Arrays must be the same length.
     *
     * @param current mutable buffer holding the currently displayed values.
     * @param target  buffer holding the newest analyzed values.
     * @param factor  interpolation strength in [0, 1], where 1 snaps instantly to target.
     */
    public static void lerpInPlace(float[] current, float[] target, float factor) {
        int length = Math.min(current.length, target.length);
        float clampedFactor = MathUtil.clamp(factor, 0.0f, 1.0f);
        for (int i = 0; i < length; i++) {
            current[i] = MathUtil.lerp(current[i], target[i], clampedFactor);
        }
    }

    /**
     * Applies exponential decay towards {@code target}, frame-rate independent.
     *
     * @param current      mutable buffer holding currently displayed values.
     * @param target       buffer holding newest analyzed values.
     * @param smoothing    smoothing factor in [0, 1).
     * @param deltaSeconds elapsed time in seconds since last update.
     */
    public static void smoothInPlace(float[] current, float[] target, float smoothing, float deltaSeconds) {
        int length = Math.min(current.length, target.length);
        for (int i = 0; i < length; i++) {
            current[i] = MathUtil.smoothTowards(current[i], target[i], smoothing, deltaSeconds);
        }
    }

    /**
     * Decays a peak-hold buffer towards the current values, only ever moving downward
     * unless the current value exceeds the held peak, in which case it snaps up instantly.
     *
     * @param peaks        mutable buffer of peak values.
     * @param current      buffer of current bar values.
     * @param decayPerSec  how fast peaks fall per second, in the same units as the bar values.
     * @param deltaSeconds elapsed time in seconds since last update.
     */
    public static void decayPeaks(float[] peaks, float[] current, float decayPerSec, float deltaSeconds) {
        int length = Math.min(peaks.length, current.length);
        float decayAmount = decayPerSec * deltaSeconds;
        for (int i = 0; i < length; i++) {
            if (current[i] >= peaks[i]) {
                peaks[i] = current[i];
            } else {
                peaks[i] = Math.max(current[i], peaks[i] - decayAmount);
            }
        }
    }
}
