package com.mediavisualizer.client.gui;

import com.mediavisualizer.client.audio.AudioDevice;
import com.mediavisualizer.client.audio.DeviceManager;
import com.mediavisualizer.client.config.ColorMode;
import com.mediavisualizer.client.config.FrequencyScaling;
import com.mediavisualizer.client.config.VisualizerConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Hand-built, tabbed configuration screen offering every setting from {@link VisualizerConfig},
 * including a live output-device dropdown backed by {@link DeviceManager} and draggable RGB
 * sliders for the gradient colors, neither of which the auto-generated NeoForge config screen
 * can provide.
 * <p>
 * Every widget writes directly back into the corresponding {@code ForgeConfigSpec} value on
 * change, so settings apply live and are persisted automatically by NeoForge's config system
 * without needing a separate "Save" step.
 */
public final class ConfigScreen extends Screen {

    private enum Tab {
        GENERAL("screen.media_visualizer.config.tab.general"),
        COLORS("screen.media_visualizer.config.tab.colors"),
        AUDIO("screen.media_visualizer.config.tab.audio");

        final String translationKey;

        Tab(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private final Screen parent;
    private final VisualizerConfig config;
    private final DeviceManager deviceManager;

    private Tab currentTab = Tab.GENERAL;
    private final List<ColorSwatch> colorSwatches = new ArrayList<>();

    public ConfigScreen(Screen parent, VisualizerConfig config, DeviceManager deviceManager) {
        super(Component.translatable("screen.media_visualizer.config.title"));
        this.parent = parent;
        this.config = config;
        this.deviceManager = deviceManager;
    }

    @Override
    protected void init() {
        colorSwatches.clear();
        buildTabButtons();
        int contentStartY = 46;
        switch (currentTab) {
            case GENERAL -> buildGeneralTab(contentStartY);
            case COLORS -> buildColorsTab(contentStartY);
            case AUDIO -> buildAudioTab(contentStartY);
        }

        this.addRenderableWidget(Button.builder(Component.translatable("screen.media_visualizer.config.done"),
                        button -> onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20)
                .build());
    }

    private void buildTabButtons() {
        Tab[] tabs = Tab.values();
        int totalWidth = tabs.length * 100 + (tabs.length - 1) * 4;
        int startX = this.width / 2 - totalWidth / 2;
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            int x = startX + i * 104;
            Button button = Button.builder(Component.translatable(tab.translationKey), b -> switchTab(tab))
                    .bounds(x, 24, 100, 18)
                    .build();
            button.active = tab != currentTab;
            this.addRenderableWidget(button);
        }
    }

    private void switchTab(Tab tab) {
        this.currentTab = tab;
        this.clearWidgets();
        this.init();
    }

    // ------------------------------------------------------------------------------------
    // General tab: playback toggles, bar geometry, and misc behavior.
    // ------------------------------------------------------------------------------------
    private void buildGeneralTab(int startY) {
        int y = startY;
        int rowHeight = 24;
        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int widgetWidth = 150;

        y = addToggle(leftX, y, widgetWidth, "config.media_visualizer.modEnabled",
                config.modEnabled.get(), config.modEnabled::set);
        addToggle(rightX, y - rowHeight, widgetWidth, "config.media_visualizer.visualizerEnabled",
                config.visualizerEnabled.get(), config.visualizerEnabled::set);

        y = addIntSlider(leftX, y, widgetWidth, "config.media_visualizer.barCount",
                config.barCount.get(), 4, 256, config.barCount::set);
        addIntSlider(rightX, y - rowHeight, widgetWidth, "config.media_visualizer.barWidth",
                config.barWidth.get(), 1, 32, config.barWidth::set);

        y = addIntSlider(leftX, y, widgetWidth, "config.media_visualizer.barSpacing",
                config.barSpacing.get(), 0, 16, config.barSpacing::set);
        addIntSlider(rightX, y - rowHeight, widgetWidth, "config.media_visualizer.maxHeight",
                config.maxHeight.get(), 4, 200, config.maxHeight::set);

        y = addIntSlider(leftX, y, widgetWidth, "config.media_visualizer.yOffset",
                config.yOffset.get(), -50, 200, config.yOffset::set);
        addDoubleSlider(rightX, y - rowHeight, widgetWidth, "config.media_visualizer.opacity",
                config.opacity.get(), 0.0, 1.0, config.opacity::set);

        y = addEnumCycle(leftX, y, widgetWidth, "config.media_visualizer.frequencyScaling",
                FrequencyScaling.class, config.frequencyScaling.get(), config.frequencyScaling::set);
        addToggle(rightX, y - rowHeight, widgetWidth, "config.media_visualizer.mirrorMode",
                config.mirrorMode.get(), config.mirrorMode::set);

        y = addToggle(leftX, y, widgetWidth, "config.media_visualizer.easing",
                config.animationEasing.get(), config.animationEasing::set);
        addToggle(rightX, y - rowHeight, widgetWidth, "config.media_visualizer.silenceDetection",
                config.silenceDetection.get(), config.silenceDetection::set);
    }

    // ------------------------------------------------------------------------------------
    // Colors tab: color mode, draggable RGB sliders for the gradient stops, and rainbow tuning.
    // ------------------------------------------------------------------------------------
    private void buildColorsTab(int startY) {
        int y = startY;
        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int fullWidth = 310;
        int widgetWidth = 150;

        y = addEnumCycle(leftX, y, fullWidth, "config.media_visualizer.colorMode",
                ColorMode.class, config.colorMode.get(), config.colorMode::set);
        y += 4;

        y = addColorPicker(leftX, y, fullWidth, "config.media_visualizer.color1",
                config.color1.get(), config.color1::set);
        y = addColorPicker(leftX, y, fullWidth, "config.media_visualizer.color2",
                config.color2.get(), config.color2::set);
        y = addColorPicker(leftX, y, fullWidth, "config.media_visualizer.color3",
                config.color3.get(), config.color3::set);

        y = addToggle(leftX, y, widgetWidth, "config.media_visualizer.useThirdColor",
                config.useThirdColor.get(), config.useThirdColor::set);
        y += 4;

        y = addDoubleSlider(leftX, y, widgetWidth, "config.media_visualizer.rainbowSpeed",
                config.rainbowSpeed.get(), 0.1, 60.0, config.rainbowSpeed::set);
        addDoubleSlider(rightX, y - 24, widgetWidth, "config.media_visualizer.rainbowSaturation",
                config.rainbowSaturation.get(), 0.0, 1.0, config.rainbowSaturation::set);

        addDoubleSlider(leftX, y, widgetWidth, "config.media_visualizer.rainbowBrightness",
                config.rainbowBrightness.get(), 0.0, 1.0, config.rainbowBrightness::set);
    }

    // ------------------------------------------------------------------------------------
    // Audio tab: capture tuning and the output-device selector.
    // ------------------------------------------------------------------------------------
    private void buildAudioTab(int startY) {
        int y = startY;
        int rowHeight = 24;
        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int widgetWidth = 150;

        y = addDoubleSlider(leftX, y, widgetWidth, "config.media_visualizer.sensitivity",
                config.sensitivity.get(), 0.1, 10.0, config.sensitivity::set);
        addDoubleSlider(rightX, y - rowHeight, widgetWidth, "config.media_visualizer.smoothing",
                config.smoothing.get(), 0.0, 0.98, config.smoothing::set);

        y = addDoubleSlider(leftX, y, widgetWidth, "config.media_visualizer.bassBoost",
                config.bassBoost.get(), 1.0, 5.0, config.bassBoost::set);
        addIntSlider(rightX, y - rowHeight, widgetWidth, "config.media_visualizer.fftSize",
                config.fftSize.get(), 256, 8192, config.fftSize::set);

        y = addDoubleSlider(leftX, y, widgetWidth, "config.media_visualizer.interpolationStrength",
                config.interpolationStrength.get(), 0.01, 1.0, config.interpolationStrength::set);
        addIntSlider(rightX, y - rowHeight, widgetWidth, "config.media_visualizer.targetFps",
                config.targetFps.get(), 15, 240, config.targetFps::set);

        y += 6;
        y = addDeviceSelector(leftX, y, widgetWidth * 2 + 10);
    }

    // ------------------------------------------------------------------------------------
    // Shared widget builders
    // ------------------------------------------------------------------------------------

    private int addToggle(int x, int y, int width, String translationKey, boolean initial, java.util.function.Consumer<Boolean> onChange) {
        this.addRenderableWidget(CycleButton.onOffBuilder(initial)
                .create(x, y, width, 20, Component.translatable(translationKey),
                        (button, value) -> onChange.accept(value)));
        return y + 24;
    }

    private <E extends Enum<E>> int addEnumCycle(int x, int y, int width, String translationKey,
                                                   Class<E> enumClass, E initial, java.util.function.Consumer<E> onChange) {
        this.addRenderableWidget(CycleButton.builder((E value) -> Component.literal(value.name()))
                .withValues(enumClass.getEnumConstants())
                .withInitialValue(initial)
                .create(x, y, width, 20, Component.translatable(translationKey),
                        (button, value) -> onChange.accept(value)));
        return y + 24;
    }

    private int addIntSlider(int x, int y, int width, String translationKey, int initial, int min, int max,
                              java.util.function.IntConsumer onChange) {
        this.addRenderableWidget(new IntSliderWidget(x, y, width, 20,
                Component.translatable(translationKey), initial, min, max, onChange));
        return y + 24;
    }

    private int addDoubleSlider(int x, int y, int width, String translationKey, double initial, double min, double max,
                                 java.util.function.DoubleConsumer onChange) {
        this.addRenderableWidget(new DoubleSliderWidget(x, y, width, 20,
                Component.translatable(translationKey), initial, min, max, onChange));
        return y + 24;
    }

    /**
     * Adds a labeled row of three draggable 0-255 sliders (R, G, B) plus a live color swatch,
     * fulfilling the "draggable buttons to change bar colors" request. The three channels are
     * tracked in a small mutable array captured by the slider callbacks so each drag recombines
     * and pushes a single 0xRRGGBB value to the config, rather than needing three separate
     * config fields per channel.
     */
    private int addColorPicker(int x, int y, int width, String translationKey, int initialColor,
                                java.util.function.IntConsumer onChange) {
        this.addRenderableWidget(new StringWidget(x, y, width, 10,
                Component.translatable(translationKey), this.font));

        int[] channels = {
                (initialColor >> 16) & 0xFF,
                (initialColor >> 8) & 0xFF,
                initialColor & 0xFF
        };

        int swatchSize = 18;
        int sliderRowY = y + 12;
        int gap = 4;
        int sliderAreaWidth = width - swatchSize - gap;
        int sliderWidth = (sliderAreaWidth - 2 * gap) / 3;
        String[] channelLabels = {"R", "G", "B"};

        for (int i = 0; i < 3; i++) {
            final int channelIndex = i;
            int sliderX = x + i * (sliderWidth + gap);
            this.addRenderableWidget(new IntSliderWidget(sliderX, sliderRowY, sliderWidth, 18,
                    Component.literal(channelLabels[i]), channels[i], 0, 255, value -> {
                        channels[channelIndex] = value;
                        int combined = (channels[0] << 16) | (channels[1] << 8) | channels[2];
                        onChange.accept(combined);
                    }));
        }

        int swatchX = x + 3 * (sliderWidth + gap);
        colorSwatches.add(new ColorSwatch(swatchX, sliderRowY,
                () -> (channels[0] << 16) | (channels[1] << 8) | channels[2]));

        return sliderRowY + 18 + 6;
    }

    /**
     * Adds the output-device dropdown plus a "Refresh Devices" button next to it, so the user
     * has one clear place to pick which system output gets captured.
     */
    private int addDeviceSelector(int x, int y, int width) {
        int refreshWidth = 90;
        int dropdownWidth = width - refreshWidth - 6;

        List<AudioDevice> devices = deviceManager.listDevices();
        AudioDevice[] deviceArray = devices.isEmpty()
                ? new AudioDevice[]{AudioDevice.SYSTEM_DEFAULT}
                : devices.toArray(new AudioDevice[0]);

        AudioDevice current = deviceManager.selectDeviceById(config.outputDeviceId.get());
        AudioDevice initial = contains(deviceArray, current) ? current : deviceArray[0];

        this.addRenderableWidget(CycleButton.<AudioDevice>builder(device -> Component.literal(device.displayName()))
                .withValues(deviceArray)
                .withInitialValue(initial)
                .create(x, y, dropdownWidth, 20, Component.translatable("config.media_visualizer.outputDevice"),
                        (button, value) -> config.outputDeviceId.set(value.id())));

        this.addRenderableWidget(Button.builder(Component.translatable("screen.media_visualizer.config.device.refresh"),
                        button -> switchTab(currentTab))
                .bounds(x + dropdownWidth + 6, y, refreshWidth, 20)
                .build());

        return y + 24;
    }

    private static boolean contains(AudioDevice[] devices, AudioDevice target) {
        for (AudioDevice device : devices) {
            if (device.id().equals(target.id())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        for (ColorSwatch swatch : colorSwatches) {
            swatch.render(graphics);
        }
    }

    @Override
    public void onClose() {
        // ForgeConfigSpec.set() already writes through to the autosaving backing file, but we
        // force an explicit save here too so every change made in this screen is guaranteed
        // to be on disk the moment the screen closes, regardless of timing.
        config.spec.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    /**
     * A small live-updating filled square showing the currently selected color for a picker row.
     * Rendered manually (rather than as a widget) since it has no interaction of its own.
     */
    private record ColorSwatch(int x, int y, IntSupplier colorSupplier) {
        private static final int SIZE = 18;

        void render(GuiGraphics graphics) {
            int rgb = colorSupplier.getAsInt() & 0xFFFFFF;
            graphics.fill(x, y, x + SIZE, y + SIZE, 0xFF000000 | 0x808080);
            graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0xFF000000 | rgb);
        }
    }

    /**
     * Minimal integer-valued slider widget that writes back to a config value on release.
     */
    private static final class IntSliderWidget extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final java.util.function.IntConsumer onChange;
        private final String translationKey;
        private int intValue;

        IntSliderWidget(int x, int y, int width, int height, Component label, int initial, int min, int max,
                         java.util.function.IntConsumer onChange) {
            super(x, y, width, height, label, normalize(initial, min, max));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            this.translationKey = label.getString();
            this.intValue = initial;
            updateMessage();
        }

        private static double normalize(int value, int min, int max) {
            return (value - min) / (double) (max - min);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(translationKey + ": " + intValue));
        }

        @Override
        protected void applyValue() {
            this.intValue = (int) Math.round(min + value * (max - min));
            onChange.accept(intValue);
        }
    }

    /**
     * Minimal double-valued slider widget that writes back to a config value on release.
     */
    private static final class DoubleSliderWidget extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final java.util.function.DoubleConsumer onChange;
        private final String translationKey;
        private double doubleValue;

        DoubleSliderWidget(int x, int y, int width, int height, Component label, double initial, double min, double max,
                            java.util.function.DoubleConsumer onChange) {
            super(x, y, width, height, label, normalize(initial, min, max));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            this.translationKey = label.getString();
            this.doubleValue = initial;
            updateMessage();
        }

        private static double normalize(double value, double min, double max) {
            return (value - min) / (max - min);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format("%s: %.2f", translationKey, doubleValue)));
        }

        @Override
        protected void applyValue() {
            this.doubleValue = min + value * (max - min);
            onChange.accept(doubleValue);
        }
    }
}
