package com.mediavisualizer.client.render;

/**
 * Stateless helper that interpolates smoothly across a 2-stop or 3-stop RGB gradient.
 * Used to color each bar based on its normalized position (0 = bottom/first bar, 1 = top/last bar).
 */
public final class GradientColor {

    private GradientColor() {
        // Utility class - no instances.
    }

    /**
     * Interpolates a color along the gradient defined by {@code color1}/{@code color2}
     * (and optionally {@code color3}), at position {@code t} in [0, 1].
     *
     * @param color1       first stop, as 0xRRGGBB.
     * @param color2       second stop, as 0xRRGGBB.
     * @param color3       third stop, as 0xRRGGBB (only used if {@code useThirdColor} is true).
     * @param useThirdColor whether to blend through {@code color3} for a 3-stop gradient.
     * @param t            position along the gradient, in [0, 1].
     * @return interpolated color as 0xRRGGBB.
     */
    public static int interpolate(int color1, int color2, int color3, boolean useThirdColor, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));

        if (!useThirdColor) {
            return lerpRgb(color1, color2, clamped);
        }

        if (clamped < 0.5f) {
            return lerpRgb(color1, color2, clamped * 2.0f);
        } else {
            return lerpRgb(color2, color3, (clamped - 0.5f) * 2.0f);
        }
    }

    private static int lerpRgb(int from, int to, float t) {
        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;

        int r = Math.round(fromR + (toR - fromR) * t);
        int g = Math.round(fromG + (toG - fromG) * t);
        int b = Math.round(fromB + (toB - fromB) * t);

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Packs an ARGB int from a 0xRRGGBB color and a separate alpha channel in [0, 1].
     */
    public static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
