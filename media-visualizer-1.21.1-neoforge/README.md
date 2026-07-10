# Media Visualizer

A client-side NeoForge mod for Minecraft 1.21.1 that renders a system-audio spectrum
visualizer behind the vanilla HUD (above the hotbar, behind hearts/hunger/armor/air/XP).

## Building

Requirements: JDK 21, an internet connection for Gradle to resolve NeoForge/JNA artifacts.

```bash
./gradlew build          # produces build/libs/media-visualizer-1.0.0.jar
./gradlew runClient       # launches a dev client with the mod loaded
```

This repository does not include the `gradlew`/`gradlew.bat` wrapper scripts or the wrapper
jar binary (binary files aren't practical to hand-author). Generate them once you have a local
Gradle install by running `gradle wrapper --gradle-version 8.8` from the project root, or simply
use your own Gradle 8.8+ installation directly (`gradle build`) instead of the wrapper.

Before building, double check `neo_version` in `gradle.properties` against the latest NeoForge
1.21.1 release at https://projects.neoforged.net/neoforged/neoforge — the value shipped here
(`21.1.87`) may have been superseded.

## Architecture

```
com.mediavisualizer
├── MediaVisualizer            – mod entry point, event registration
├── client.audio
│   ├── AudioCaptureService    – orchestrates capture lifecycle, reacts to config changes
│   ├── AudioCaptureStrategy   – platform-capture abstraction (interface)
│   ├── WasapiLoopbackStrategy – Windows: true WASAPI process-loopback capture (JNA/COM)
│   ├── JavaSoundLoopbackStrategy – portable fallback via javax.sound.sampled
│   ├── AudioCaptureListener   – functional callback for delivered PCM blocks
│   ├── DeviceManager          – device enumeration, selection persistence, fallback
│   ├── AudioDevice            – immutable device descriptor
│   ├── FFTProcessor           – allocation-free radix-2 Cooley-Tukey FFT + Hann window
│   ├── SpectrumAnalyzer       – ring buffer, bin→bar mapping, sensitivity/bass boost
│   └── VisualizerData         – thread-safe hand-off buffer (audio thread → render thread)
├── client.render
│   ├── VisualizerRenderer     – draws bars via the custom GUI layer
│   ├── GradientColor          – 2/3-stop gradient interpolation
│   └── RainbowColor           – HSB-based rainbow cycling
├── client.config
│   ├── VisualizerConfig       – NeoForge ModConfigSpec declaration (all settings)
│   ├── ColorMode / FrequencyScaling – config enums
├── client.gui
│   └── ConfigScreen           – tabbed screen: General / Colors / Audio, incl. device dropdown
└── client.util
    ├── MathUtil                – clamp/lerp/easing/log-scale helpers
    └── InterpolationUtil       – in-place array smoothing/decay, no per-frame allocation
```

## How system-output ("loopback") capture works here

Java has no built-in, cross-platform API for capturing what the OS is currently *playing*
(as opposed to a microphone). This mod handles that with two strategies behind the
`AudioCaptureStrategy` interface, chosen automatically by `DeviceManager` based on OS:

- **Windows — `WasapiLoopbackStrategy`.** Uses the real WASAPI loopback mechanism
  (`AUDCLNT_STREAMFLAGS_LOOPBACK` on `IAudioClient`) via raw COM interop through JNA —
  the same approach OBS Studio and other desktop-audio recorders use. This captures the
  actual system output with no extra user setup. Because raw COM vtable interop is
  inherently sensitive to JDK/JNA version drift, test this path on your target JDK/JNA
  versions before shipping, and watch the log line
  `[MediaVisualizer] WASAPI loopback capture stopped: ...` for diagnostics if it fails —
  the mod will keep running with decaying/silent bars rather than crashing.
- **Linux / macOS (and Windows fallback) — `JavaSoundLoopbackStrategy`.** Built on the
  standard `javax.sound.sampled` API. On Linux, PulseAudio/PipeWire-Pulse automatically
  expose a `Monitor of <sink>` recording source for every output device, so this works
  out of the box. On Windows, it requires enabling the built-in **"Stereo Mix"** device
  (Control Panel → Sound → Recording → right-click → *Show Disabled Devices*) or a virtual
  audio cable (e.g. VB-CABLE); on macOS it requires a loopback driver (e.g. BlackHole),
  since neither OS exposes loopback through Java Sound directly.

Both strategies deliver mono `float[]` PCM blocks to `AudioCaptureService` on a dedicated,
non-render thread, where `SpectrumAnalyzer` accumulates them into an FFT window, runs
`FFTProcessor`, maps magnitudes onto the configured bar count (linearly or logarithmically),
and publishes the result into `VisualizerData` for the render thread to pick up.

## External dependency

- **JNA / JNA-Platform (`net.java.dev.jna:jna`, `:jna-platform`)** — required only by
  `WasapiLoopbackStrategy` for Windows COM interop. Declared in `build.gradle`.

## Rendering approach

`VisualizerRenderer` is registered as a custom NeoForge GUI layer via
`RegisterGuiLayersEvent#registerBelow(VanillaGuiLayers.HOTBAR, ...)`. Because the hotbar
layer draws before the hearts/hunger/armor/air/XP layers, sitting immediately behind the
hotbar guarantees the visualizer is behind all of them too — it never overdraws vanilla HUD
elements or any GUI opened afterward (inventory, chat, pause menu, etc.), since those are
separate render passes entirely.

All per-frame buffers (`displayedValues`, `targetValues`) are allocated once
and only resized when the user changes the bar count, so steady-state rendering performs
zero heap allocation, and bars are drawn with the same cheap flat-quad `GuiGraphics#fill`
call vanilla itself uses. Bar movement uses an asymmetric fast-attack / slow-decay curve
(bars snap up quickly, fall back down smoothly), the same trick real spectrum analyzers use
to look musical rather than like an animated bar chart.

## Configuration

All settings are declared in `VisualizerConfig` using NeoForge's `ModConfigSpec`, which
gives automatic TOML persistence at `config/media_visualizer-client.toml`, load-on-startup,
and an entry in the Mods list's "Config" button. `ConfigScreen` additionally supplies a
richer, hand-built, tabbed settings screen with three categories:

- **General** — enable/disable, bar geometry, frequency scaling, mirror mode, easing, silence detection.
- **Colors** — color mode (Gradient/Rainbow), draggable 0-255 R/G/B sliders with a live swatch
  for each of the three gradient stops, and rainbow speed/saturation/brightness.
- **Audio** — sensitivity, smoothing, bass boost, FFT size, interpolation strength, target FPS,
  and the output-device dropdown (with a "Refresh Devices" button), which can't be generated
  automatically since device ids aren't known until runtime.

## Known limitations / follow-ups

- `WasapiLoopbackStrategy` always follows the current Windows *default* playback device
  rather than letting the user loopback-capture a specific non-default output device;
  doing so would require additionally enumerating `IMMDeviceCollection` and is left as a
  documented follow-up (see the comment in `listDevices()`).
- No mixer/output switching mid-stream reinitializes the WASAPI mix format if the sample
  rate changes (e.g. switching from a 48kHz device to a 44.1kHz one) — `AudioCaptureService`
  detects device-id changes and restarts capture, which re-queries the mix format correctly.
