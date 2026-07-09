package com.mediavisualizer.client.render;

import com.mediavisualizer.client.audio.VisualizerData;
import com.mediavisualizer.client.config.ColorMode;
import com.mediavisualizer.client.config.VisualizerConfig;
import com.mediavisualizer.client.util.MathUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the audio-reactive spectrum bars as vertical rectangles centered above the hotbar.
 * <p>
 * This class is registered as a custom NeoForge GUI layer positioned <i>below</i> (i.e. drawn
 * before, and therefore visually behind) the vanilla hotbar and status bar layers, so the bars
 * never obscure hearts, hunger, armor, air, or experience.
 * <p>
 * Movement uses asymmetric attack/decay smoothing, the same trick real spectrum analyzers
 * (Winamp, foobar2000, OBS's audio meter, etc.) use: bars snap upward quickly when a frequency
 * gets louder, then fall back down more slowly, which reads as much more "musical" and alive
 * than a single symmetric interpolation factor.
 * <p>
 * Performance notes:
 * <ul>
 *     <li>{@link #displayedValues} and {@link #targetValues} are allocated once and only
 *     resized when the bar count actually changes, so steady-state rendering performs zero
 *     heap allocation.</li>
 *     <li>Bars are drawn with {@link GuiGraphics#fill(int, int, int, int, int)}, the same cheap
 *     flat-quad primitive vanilla uses for the hotbar's own selection outline, avoiding any
 *     extra shader or texture binds.</li>
 * </ul>
 */
public final class VisualizerRenderer {

    /**
     * Attack (rising) responsiveness is scaled well above the configured interpolation
     * strength so bars snap upward almost instantly on a transient/beat.
     */
    private static final float ATTACK_MULTIPLIER = 3.0f;
    /**
     * Decay (falling) responsiveness is scaled well below the configured interpolation
     * strength so bars settle back down smoothly instead of snapping to silence.
     */
    private static final float DECAY_MULTIPLIER = 0.30f;

    private final VisualizerData visualizerData;
    private final VisualizerConfig config;

    private float[] displayedValues = new float[0];
    private float[] targetValues = new float[0];

    private float elapsedSeconds;
    private long lastFrameNanos = -1;

    public VisualizerRenderer(VisualizerData visualizerData, VisualizerConfig config) {
        this.visualizerData = visualizerData;
        this.config = config;
    }

    /**
     * Invoked once per frame from the {@code RenderGuiLayerEvent} handler registered in the
     * main mod class.
     */
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!config.modEnabled.get() || !config.visualizerEnabled.get()) {
            return;
        }
        if (Minecraft.getInstance().options.hideGui) {
            return;
        }

        float deltaSeconds = computeDeltaSeconds();
        elapsedSeconds += deltaSeconds;

        int barCount = Math.max(1, config.barCount.get());
        ensureCapacity(barCount);

        int written = visualizerData.copyInto(targetValues);
        if (written < targetValues.length) {
            // Bar count just changed and VisualizerData hasn't caught up yet this frame; skip.
            return;
        }

        advanceMovement(barCount, deltaSeconds);
        drawBars(graphics, barCount);
    }

    /**
     * Advances {@link #displayedValues} towards {@link #targetValues} using a fast attack /
     * slow decay curve, which is what gives the bars their punchy, musical movement instead
     * of looking like a generic animated bar chart.
     */
    private void advanceMovement(int barCount, float deltaSeconds) {
        boolean easing = config.animationEasing.get();
        float baseStrength = config.interpolationStrength.get().floatValue();

        for (int i = 0; i < barCount; i++) {
            float current = displayedValues[i];
            float target = targetValues[i];
            boolean rising = target > current;

            float strength = rising
                    ? MathUtil.clamp(baseStrength * ATTACK_MULTIPLIER, 0.0f, 1.0f)
                    : MathUtil.clamp(baseStrength * DECAY_MULTIPLIER, 0.0f, 1.0f);

            // Make the factor frame-rate independent so movement looks the same at 60 or 240 FPS.
            float frameFactor = 1.0f - (float) Math.pow(1.0 - strength, deltaSeconds * 60.0);
            if (easing) {
                frameFactor = MathUtil.easeOutCubic(frameFactor);
            }

            displayedValues[i] = MathUtil.lerp(current, target, frameFactor);
        }
    }

    private float computeDeltaSeconds() {
        long now = System.nanoTime();
        if (lastFrameNanos < 0) {
            lastFrameNanos = now;
            return 1.0f / 60.0f;
        }
        float delta = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        return MathUtil.clamp(delta, 0.0f, 0.25f);
    }

    private void ensureCapacity(int barCount) {
        if (displayedValues.length != barCount) {
            displayedValues = growPreservingPrefix(displayedValues, barCount);
            targetValues = new float[barCount];
        }
    }

    private static float[] growPreservingPrefix(float[] old, int newSize) {
        float[] resized = new float[newSize];
        System.arraycopy(old, 0, resized, 0, Math.min(old.length, newSize));
        return resized;
    }

    private void drawBars(GuiGraphics graphics, int barCount) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int barWidth = config.barWidth.get();
        int barSpacing = config.barSpacing.get();
        int maxHeight = config.maxHeight.get();
        int yOffset = config.yOffset.get();
        float opacity = config.opacity.get().floatValue();
        boolean mirror = config.mirrorMode.get();
        ColorMode colorMode = config.colorMode.get();

        int color1 = config.color1.get();
        int color2 = config.color2.get();
        int color3 = config.color3.get();
        boolean useThirdColor = config.useThirdColor.get();
        float rainbowSpeed = config.rainbowSpeed.get().floatValue();
        float rainbowSaturation = config.rainbowSaturation.get().floatValue();
        float rainbowBrightness = config.rainbowBrightness.get().floatValue();

        int totalWidth = barCount * barWidth + Math.max(0, barCount - 1) * barSpacing;
        int startX = (screenWidth - totalWidth) / 2;
        // Baseline sits `yOffset` pixels above where the hotbar is drawn (22px tall, anchored
        // to the bottom of the screen), matching the "just above the hotbar" requirement.
        int baseline = screenHeight - 22 - yOffset;

        for (int i = 0; i < barCount; i++) {
            float value = mirror ? mirroredValue(i, barCount) : displayedValues[i];
            int barHeight = Math.round(MathUtil.clamp(value, 0.0f, 1.0f) * maxHeight);
            if (barHeight <= 0) {
                continue;
            }

            int x = startX + i * (barWidth + barSpacing);
            int top = baseline - barHeight;

            int rgb = switch (colorMode) {
                case GRADIENT -> GradientColor.interpolate(color1, color2, color3, useThirdColor,
                        (float) i / Math.max(1, barCount - 1));
                case RAINBOW -> RainbowColor.forBar(i, barCount, elapsedSeconds, rainbowSpeed,
                        rainbowSaturation, rainbowBrightness);
            };
            int argb = GradientColor.withAlpha(rgb, opacity);

            graphics.fill(x, top, x + barWidth, baseline, argb);
        }
    }

    private float mirroredValue(int index, int barCount) {
        int half = barCount / 2;
        int mirroredIndex = index < half ? index : barCount - 1 - index;
        int sourceIndex = Math.min(mirroredIndex, targetValues.length - 1);
        return displayedValues.length > sourceIndex ? displayedValues[sourceIndex] : 0.0f;
    }
}
