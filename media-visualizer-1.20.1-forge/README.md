# Media Visualizer — Forge 1.20.1 port

This is a port of the NeoForge 1.21.1 Media Visualizer mod to **classic Minecraft Forge on
1.20.1**. It is a genuine cross-modloader, cross-version port, not just a version bump: Forge
and NeoForge diverged after 1.20.1, and 1.20.1's rendering/config APIs differ from 1.21.1's.

## What changed vs. the NeoForge 1.21.1 version

Only the Minecraft/Forge integration layer changed. All audio processing, FFT, coloring, and
math logic is plain Java (plus JNA for WASAPI) and was copied over **unmodified**:

| File | Status |
|---|---|
| `client.audio.*` (all 9 files, incl. WASAPI/JNA interop) | **Unchanged** |
| `client.util.*` (MathUtil, InterpolationUtil) | **Unchanged** |
| `client.render.GradientColor`, `RainbowColor` | **Unchanged** |
| `client.config.ColorMode`, `FrequencyScaling` | **Unchanged** |
| `client.gui.ConfigScreen` | **Unchanged** (only uses vanilla widget classes present in both versions) |
| `client.config.VisualizerConfig` | Changed: `net.neoforged.neoforge.common.ModConfigSpec` → `net.minecraftforge.common.ForgeConfigSpec`. Same API shape, just a different package/class name. |
| `client.render.VisualizerRenderer` | Changed: render signature takes `float partialTick` instead of NeoForge's `DeltaTracker`, which doesn't exist in 1.20.1. |
| `MediaVisualizer` (main class) | Rewritten: uses `RegisterGuiOverlaysEvent`/`IGuiOverlay`/`VanillaGuiOverlay` instead of NeoForge's `RegisterGuiLayersEvent`/`VanillaGuiLayers`, and `ConfigScreenHandler.ConfigScreenFactory` instead of `IConfigScreenFactory` for the config-button registration. |
| `build.gradle`, `gradle.properties`, `settings.gradle`, `mods.toml` | Rewritten for ForgeGradle 6.x / Forge 47.x instead of NeoForge's ModDevGradle. |

The HUD-layering behavior is identical in spirit: the overlay is registered *below* the vanilla
hotbar overlay (`event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), ...)`), so it draws before —
and therefore visually behind — the hotbar, hearts, hunger, armor, air, and XP bar, exactly like
the NeoForge version.

## Building

Requirements: **JDK 17** (Forge 1.20.1 does not support JDK 21), an internet connection.

```bash
./gradlew build          # produces build/libs/media-visualizer-1.0.0.jar
./gradlew runClient       # launches a dev client with the mod loaded
```

As with the NeoForge version, this repo doesn't include the `gradlew`/`gradlew.bat` binaries —
run `gradle wrapper --gradle-version 8.1.1` once locally, or use your own Gradle 8.1.1 install.

Before building, double-check `forge_version` in `gradle.properties` against the latest 1.20.1
recommended build at https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html
— the value shipped here (`47.3.0`) may have been superseded.

## Mappings note

This port uses **official Mojang mappings** (`mapping_channel=official`) rather than Parchment,
to avoid pinning another external mapping version that could go stale. If you want de-obfuscated
parameter names in your IDE, add the
[Parchment/Librarian plugin](https://parchmentmc.org/docs/getting-started) and switch
`mapping_channel` to `parchment` with a valid version for 1.20.1.

## Known limitation carried over

Same as the NeoForge version: `WasapiLoopbackStrategy` always follows the current Windows
*default* playback device rather than a specific non-default output, and hand-written COM
vtable interop is inherently version-sensitive — test it on your target JDK/JNA combo. See the
main `README.md` from the NeoForge version for the full audio-capture architecture writeup
(it applies here unchanged).
