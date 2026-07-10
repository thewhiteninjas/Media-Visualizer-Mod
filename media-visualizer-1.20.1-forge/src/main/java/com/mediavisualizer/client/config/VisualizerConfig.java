package com.mediavisualizer.client.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Declares every user-facing setting using Forge's {@link ForgeConfigSpec} builder.
 * The resulting spec is registered against {@code ModConfig.Type.CLIENT} in the main mod
 * class, which gives us automatic TOML persistence (load on startup, save on change) and
 * a generated entry in the Mods configuration screen.
 * <p>
 * Values are exposed as public final {@code ForgeConfigSpec.*Value} fields; callers read them
 * with {@code .get()}. Forge caches these reads efficiently, so it is safe to call
 * {@code .get()} every frame from the renderer without a measurable performance cost.
 */
public final class VisualizerConfig {

    public final ForgeConfigSpec.BooleanValue modEnabled;
    public final ForgeConfigSpec.BooleanValue visualizerEnabled;

    public final ForgeConfigSpec.IntValue barWidth;
    public final ForgeConfigSpec.IntValue barSpacing;
    public final ForgeConfigSpec.IntValue barCount;
    public final ForgeConfigSpec.IntValue maxHeight;
    public final ForgeConfigSpec.IntValue yOffset;
    public final ForgeConfigSpec.DoubleValue opacity;

    public final ForgeConfigSpec.EnumValue<ColorMode> colorMode;
    public final ForgeConfigSpec.IntValue color1;
    public final ForgeConfigSpec.IntValue color2;
    public final ForgeConfigSpec.IntValue color3;
    public final ForgeConfigSpec.BooleanValue useThirdColor;
    public final ForgeConfigSpec.DoubleValue rainbowSpeed;
    public final ForgeConfigSpec.DoubleValue rainbowSaturation;
    public final ForgeConfigSpec.DoubleValue rainbowBrightness;

    public final ForgeConfigSpec.ConfigValue<String> outputDeviceId;
    public final ForgeConfigSpec.DoubleValue sensitivity;
    public final ForgeConfigSpec.IntValue fftSize;
    public final ForgeConfigSpec.DoubleValue smoothing;

    public final ForgeConfigSpec.IntValue targetFps;
    public final ForgeConfigSpec.IntValue updateRateMs;
    public final ForgeConfigSpec.DoubleValue interpolationStrength;

    public final ForgeConfigSpec.BooleanValue mirrorMode;
    public final ForgeConfigSpec.DoubleValue bassBoost;
    public final ForgeConfigSpec.EnumValue<FrequencyScaling> frequencyScaling;
    public final ForgeConfigSpec.BooleanValue animationEasing;
    public final ForgeConfigSpec.BooleanValue silenceDetection;

    public final ForgeConfigSpec spec;

    public VisualizerConfig(ForgeConfigSpec.Builder builder) {
        builder.push("general");
        modEnabled = builder
                .comment("Master switch for the entire mod, including audio capture.")
                .translation("config.media_visualizer.modEnabled")
                .define("modEnabled", true);
        visualizerEnabled = builder
                .comment("Toggles rendering of the spectrum bars without stopping audio capture.")
                .translation("config.media_visualizer.visualizerEnabled")
                .define("visualizerEnabled", true);
        builder.pop();

        builder.push("appearance");
        barWidth = builder
                .comment("Width of each bar in pixels.")
                .translation("config.media_visualizer.barWidth")
                .defineInRange("barWidth", 3, 1, 32);
        barSpacing = builder
                .comment("Horizontal gap between bars in pixels.")
                .translation("config.media_visualizer.barSpacing")
                .defineInRange("barSpacing", 1, 0, 16);
        barCount = builder
                .comment("Number of vertical bars in the spectrum display.")
                .translation("config.media_visualizer.barCount")
                .defineInRange("barCount", 47, 4, 256);
        maxHeight = builder
                .comment("Maximum pixel height a bar can reach at full amplitude.")
                .translation("config.media_visualizer.maxHeight")
                .defineInRange("maxHeight", 37, 4, 200);
        yOffset = builder
                .comment("Vertical offset in pixels above the hotbar where the visualizer baseline sits.")
                .translation("config.media_visualizer.yOffset")
                .defineInRange("yOffset", 19, -50, 200);
        opacity = builder
                .comment("Overall opacity of the bars, from 0 (invisible) to 1 (fully opaque).")
                .translation("config.media_visualizer.opacity")
                .defineInRange("opacity", 0.51, 0.0, 1.0);
        builder.pop();

        builder.push("colors");
        colorMode = builder
                .comment("GRADIENT interpolates between 2-3 fixed colors. RAINBOW cycles hue over time.")
                .translation("config.media_visualizer.colorMode")
                .defineEnum("colorMode", ColorMode.RAINBOW);
        color1 = builder
                .comment("First gradient color, as a 0xRRGGBB hex integer.")
                .translation("config.media_visualizer.color1")
                .defineInRange("color1", 0x00FFAA, 0x000000, 0xFFFFFF);
        color2 = builder
                .comment("Second gradient color, as a 0xRRGGBB hex integer.")
                .translation("config.media_visualizer.color2")
                .defineInRange("color2", 0x00AAFF, 0x000000, 0xFFFFFF);
        color3 = builder
                .comment("Third gradient color (only used if useThirdColor is true), as 0xRRGGBB.")
                .translation("config.media_visualizer.color3")
                .defineInRange("color3", 0xAA00FF, 0x000000, 0xFFFFFF);
        useThirdColor = builder
                .comment("Whether to blend through a third color for a 3-stop gradient.")
                .translation("config.media_visualizer.useThirdColor")
                .define("useThirdColor", true);
        rainbowSpeed = builder
                .comment("How fast the hue cycles in rainbow mode, in full cycles per minute.")
                .translation("config.media_visualizer.rainbowSpeed")
                .defineInRange("rainbowSpeed", 6.0, 0.1, 60.0);
        rainbowSaturation = builder
                .comment("HSB saturation used in rainbow mode.")
                .translation("config.media_visualizer.rainbowSaturation")
                .defineInRange("rainbowSaturation", 0.8, 0.0, 1.0);
        rainbowBrightness = builder
                .comment("HSB brightness used in rainbow mode.")
                .translation("config.media_visualizer.rainbowBrightness")
                .defineInRange("rainbowBrightness", 1.0, 0.0, 1.0);
        builder.pop();

        builder.push("audio");
        outputDeviceId = builder
                .comment("Id of the selected system output device. Leave as 'system_default' to always follow the OS default.")
                .translation("config.media_visualizer.outputDevice")
                .define("outputDeviceId", "system_default");
        sensitivity = builder
                .comment("Linear gain multiplier applied to captured audio before analysis.")
                .translation("config.media_visualizer.sensitivity")
                .defineInRange("sensitivity", 2.71, 0.1, 10.0);
        fftSize = builder
                .comment("FFT window size; must be a power of two. Larger values give more frequency detail but more latency.")
                .translation("config.media_visualizer.fftSize")
                .defineInRange("fftSize", 256, 256, 8192);
        smoothing = builder
                .comment("Temporal smoothing factor in [0, 1). Higher values reduce flicker but add lag.")
                .translation("config.media_visualizer.smoothing")
                .defineInRange("smoothing", 0.81, 0.0, 0.98);
        builder.pop();

        builder.push("performance");
        targetFps = builder
                .comment("Target render update rate for the visualizer overlay.")
                .translation("config.media_visualizer.targetFps")
                .defineInRange("targetFps", 45, 15, 240);
        updateRateMs = builder
                .comment("Minimum milliseconds between audio analysis updates handed to the renderer.")
                .translation("config.media_visualizer.updateRateMs")
                .defineInRange("updateRateMs", 16, 4, 100);
        interpolationStrength = builder
                .comment("How strongly displayed bars chase newly analyzed values each render frame, in [0, 1].")
                .translation("config.media_visualizer.interpolationStrength")
                .defineInRange("interpolationStrength", 0.35, 0.01, 1.0);
        builder.pop();

        builder.push("extra");
        mirrorMode = builder
                .comment("Mirrors bars symmetrically outward from the center instead of left-to-right.")
                .translation("config.media_visualizer.mirrorMode")
                .define("mirrorMode", true);
        bassBoost = builder
                .comment("Extra gain multiplier applied to the lowest quarter of bars.")
                .translation("config.media_visualizer.bassBoost")
                .defineInRange("bassBoost", 1.3, 1.0, 5.0);
        frequencyScaling = builder
                .comment("LINEAR spreads bars evenly across the spectrum. LOGARITHMIC gives bass more visual detail.")
                .translation("config.media_visualizer.frequencyScaling")
                .defineEnum("frequencyScaling", FrequencyScaling.LOGARITHMIC);
        animationEasing = builder
                .comment("Applies an ease-out curve to bar movement instead of linear interpolation.")
                .translation("config.media_visualizer.easing")
                .define("animationEasing", true);
        silenceDetection = builder
                .comment("Automatically decays bars to zero when the output audio is silent.")
                .translation("config.media_visualizer.silenceDetection")
                .define("silenceDetection", true);
        builder.pop();

        this.spec = builder.build();
    }
}
