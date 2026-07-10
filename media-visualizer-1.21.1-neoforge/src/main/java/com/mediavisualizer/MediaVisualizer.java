package com.mediavisualizer;

import com.mediavisualizer.client.audio.AudioCaptureService;
import com.mediavisualizer.client.config.VisualizerConfig;
import com.mediavisualizer.client.gui.ConfigScreen;
import com.mediavisualizer.client.render.VisualizerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;

/**
 * Mod entry point for Media Visualizer.
 * <p>
 * This mod is entirely client-side: it captures system OUTPUT audio (never the microphone),
 * runs an FFT on a dedicated audio thread, and renders the resulting spectrum as a row of
 * bars drawn behind the vanilla hotbar and status bars via a custom {@code RenderGuiLayerEvent}
 * layer. See {@link com.mediavisualizer.client.audio.AudioCaptureService} for the audio pipeline
 * and {@link VisualizerRenderer} for the rendering logic.
 */
@Mod(MediaVisualizer.MOD_ID)
public final class MediaVisualizer {

    public static final String MOD_ID = "media_visualizer";
    private static final ResourceLocation VISUALIZER_LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "spectrum_visualizer");

    private static VisualizerConfig config;
    private static AudioCaptureService audioCaptureService;
    private static VisualizerRenderer renderer;

    public MediaVisualizer(IEventBus modEventBus, ModContainer modContainer) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        config = new VisualizerConfig(builder);
        modContainer.registerConfig(ModConfig.Type.CLIENT, config.spec);

        // Registers a config screen factory so the mod shows a working "Config" button in the
        // Mods list. ConfigScreen is our richer, hand-built screen (including the live device
        // dropdown), used directly rather than NeoForge's generic ConfigurationScreen so we can
        // populate device options that aren't known until runtime.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new ConfigScreen(parent, config, getOrCreateAudioCaptureService().getDeviceManager()));

        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterGuiLayers);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            audioCaptureService = new AudioCaptureService(config);
            renderer = new VisualizerRenderer(audioCaptureService.getVisualizerData(), config);
            audioCaptureService.start();
        });
    }

    /**
     * Registers our custom GUI layer positioned immediately below (i.e. rendered before, and
     * therefore visually behind) the vanilla hotbar layer. Because the hotbar layer is drawn
     * before the hearts/hunger/armor/air/xp layers, sitting behind the hotbar guarantees we
     * also sit behind all of those, satisfying the "never covers HUD elements" requirement.
     */
    private void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.HOTBAR, VISUALIZER_LAYER_ID, this::renderVisualizerLayer);
    }

    private void renderVisualizerLayer(net.minecraft.client.gui.GuiGraphics graphics,
                                        net.minecraft.client.DeltaTracker deltaTracker) {
        if (renderer != null) {
            renderer.render(graphics, deltaTracker);
        }
    }

    public static VisualizerConfig getConfig() {
        return config;
    }

    public static AudioCaptureService getAudioCaptureService() {
        return audioCaptureService;
    }

    /**
     * FMLClientSetupEvent (which creates {@link #audioCaptureService}) always runs before the
     * player can reach any screen, but this lazy accessor guards against the theoretical case
     * of the config screen factory being invoked earlier, e.g. by another mod.
     */
    private static AudioCaptureService getOrCreateAudioCaptureService() {
        if (audioCaptureService == null) {
            audioCaptureService = new AudioCaptureService(config);
        }
        return audioCaptureService;
    }
}
