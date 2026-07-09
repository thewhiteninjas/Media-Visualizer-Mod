package com.mediavisualizer.client.config;

/**
 * Determines how bar colors are computed each frame.
 */
public enum ColorMode {
    /** Interpolates between two or three configured colors based on bar height/index. */
    GRADIENT,
    /** Cycles hue continuously over time using {@code RainbowSpeed}/{@code Saturation}/{@code Brightness}. */
    RAINBOW
}
