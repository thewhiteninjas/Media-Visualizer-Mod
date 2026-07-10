package com.mediavisualizer;

import com.mediavisualizer.client.audio.AudioCaptureService;
import com.mediavisualizer.client.config.VisualizerConfig;
import com.mediavisualizer.client.gui.ConfigScreen;
import com.mediavisualizer.client.render.VisualizerRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Mod entry point for Media Visualizer (Forge 1.20.1 port).
 * <p>
 * This mod is entirely client-side: it captures system OUTPUT audio (never the microphone),
 * runs an FFT on a dedicated audio thread, and renders the resulting spectrum as a row of
 * bars drawn behind the vanilla hotbar and status bars via a Forge GUI overlay registered
 * through {@code RegisterGuiOverlaysEvent}. See
 * {@link com.mediavisualizer.client.audio.AudioCaptureService} for the audio pipeline and
 * {@link VisualizerRenderer} for the rendering logic — both are unchanged from the NeoForge
 * 1.21.1 version, since they only depend on plain Java and vanilla Minecraft classes.
 */
@Mod(MediaVisualizer.MOD_ID)
public final class MediaVisualizer {

    public static final String MOD_ID = "media_visualizer";

    private static VisualizerConfig config;
    private static AudioCaptureService audioCaptureService;
    private static VisualizerRenderer renderer;

    public MediaVisualizer() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        config = new VisualizerConfig(builder);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, config.spec);

        // Registers the classic Forge config-screen extension point so the mod appears with a
        // working "Config" button in the Mods list, opening our hand-built tabbed ConfigScreen
        // (which additionally provides the live device dropdown the auto-generated screen can't).
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parentScreen) ->
                        new ConfigScreen(parentScreen, config, audioCaptureService.getDeviceManager())));

        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterGuiOverlays);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            audioCaptureService = new AudioCaptureService(config);
            renderer = new VisualizerRenderer(audioCaptureService.getVisualizerData(), config);
            audioCaptureService.start();
        });
    }

    /**
     * Registers our custom GUI overlay positioned immediately below (i.e. drawn before, and
     * therefore visually behind) the vanilla hotbar overlay. Because the hotbar overlay is
     * drawn before the hearts/hunger/armor/air/xp overlays, sitting behind the hotbar
     * guarantees we also sit behind all of those, satisfying the "never covers HUD elements"
     * requirement.
     */
    private void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "spectrum_visualizer", this::renderVisualizerOverlay);
    }

    private void renderVisualizerOverlay(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics,
                                          float partialTick, int screenWidth, int screenHeight) {
        if (renderer != null) {
            renderer.render(graphics, partialTick);
        }
    }

    public static VisualizerConfig getConfig() {
        return config;
    }

    public static AudioCaptureService getAudioCaptureService() {
        return audioCaptureService;
    }
}
