package com.mediavisualizer.client.util;

/**
 * Stateless numeric helpers shared across the audio and rendering pipelines.
 * <p>
 * All methods are pure functions with no allocations, so they are safe to call
 * every frame or every audio buffer without contributing to garbage collection pressure.
 */
public final class MathUtil {

    private MathUtil() {
        // Utility class - no instances.
    }

    /**
     * Clamps {@code value} into the inclusive range {@code [min, max]}.
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps {@code value} into the inclusive range {@code [min, max]}.
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Linear interpolation between {@code from} and {@code to} by factor {@code t} in [0, 1].
     */
    public static float lerp(float from, float to, float t) {
        return from + (to - from) * clamp(t, 0.0f, 1.0f);
    }

    /**
     * Frame-rate independent exponential smoothing, commonly used for "decay towards target" behavior.
     * {@code smoothing} of 0 means instant snap to target, 1 means the value never moves.
     *
     * @param current       current value.
     * @param target        target value being approached.
     * @param smoothing     smoothing factor in [0, 1).
     * @param deltaSeconds  elapsed time in seconds since the last update.
     * @return the new smoothed value.
     */
    public static float smoothTowards(float current, float target, float smoothing, float deltaSeconds) {
        float clampedSmoothing = clamp(smoothing, 0.0f, 0.999f);
        // Convert the per-frame smoothing factor into a frame-rate independent decay rate.
        float decay = (float) Math.pow(clampedSmoothing, deltaSeconds * 60.0);
        return lerp(target, current, decay);
    }

    /**
     * Maps a linear frequency bin index onto a logarithmic scale, useful for making bass detail
     * visible without the treble range collapsing to nothing.
     *
     * @param linearIndex  index in [0, totalBins)
     * @param totalBins    total number of source bins
     * @param outputBins   number of destination (visual) bins
     * @return an index in [0, totalBins) to sample from, for output bin {@code linearIndex}.
     */
    public static int logScaleIndex(int linearIndex, int totalBins, int outputBins) {
        if (outputBins <= 1) {
            return 0;
        }
        double t = (double) linearIndex / (outputBins - 1);
        // Exponential curve keeps low frequencies spread out and compresses the highs.
        double logT = (Math.pow(totalBins, t) - 1.0) / (totalBins - 1.0);
        int index = (int) Math.round(logT * (totalBins - 1));
        return clamp(index, 0, totalBins - 1);
    }

    /**
     * Cubic ease-out curve, used for smooth deceleration on bar movement.
     */
    public static float easeOutCubic(float t) {
        float clamped = clamp(t, 0.0f, 1.0f);
        float f = clamped - 1.0f;
        return f * f * f + 1.0f;
    }

    /**
     * Smoothstep curve for gentle acceleration/deceleration.
     */
    public static float smoothstep(float t) {
        float clamped = clamp(t, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }
}
