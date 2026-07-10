package com.mediavisualizer.client.render;

/**
 * Stateless helper producing rainbow-cycling colors driven by elapsed time and bar index,
 * using HSB color space so saturation/brightness stay constant while hue rotates.
 */
public final class RainbowColor {

    private RainbowColor() {
        // Utility class - no instances.
    }

    /**
     * Computes a rainbow color for a given bar.
     *
     * @param barIndex        index of the bar, used to offset hue across the bar row.
     * @param totalBars       total number of bars, used to normalize the hue offset.
     * @param elapsedSeconds  total elapsed time, drives hue rotation over time.
     * @param cyclesPerMinute how many full hue rotations occur per minute.
     * @param saturation      HSB saturation in [0, 1].
     * @param brightness      HSB brightness in [0, 1].
     * @return color as 0xRRGGBB.
     */
    public static int forBar(int barIndex, int totalBars, float elapsedSeconds,
                              float cyclesPerMinute, float saturation, float brightness) {
        float timeHue = (elapsedSeconds * cyclesPerMinute / 60.0f) % 1.0f;
        float indexOffset = totalBars <= 1 ? 0.0f : (float) barIndex / totalBars;
        float hue = (timeHue + indexOffset) % 1.0f;
        return java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
    }
}
