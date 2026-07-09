package com.mediavisualizer.client.config;

/**
 * Determines how the linear FFT frequency bins are mapped onto the visual bars.
 */
public enum FrequencyScaling {
    /** Each bar covers an equal-width slice of the FFT spectrum. */
    LINEAR,
    /** Bars are distributed logarithmically, giving bass frequencies more visual detail,
     *  which matches how the ear perceives pitch and looks closer to commercial visualizers. */
    LOGARITHMIC
}
