package com.mediavisualizer.client.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Owns device enumeration/selection and decides which {@link AudioCaptureStrategy} should
 * actually be used for a given selection.
 * <p>
 * Two strategy instances are kept:
 * <ul>
 *     <li>{@code platformStrategy} — the best available strategy for "System Default"
 *     capture: true WASAPI loopback on Windows (see {@link WasapiLoopbackStrategy}), or the
 *     portable strategy elsewhere.</li>
 *     <li>{@code portableStrategy} — the portable {@link JavaSoundLoopbackStrategy}, always
 *     available. It is used both to enumerate real device names for the config screen's
 *     dropdown (a plain {@code javax.sound.sampled} call with no native/COM risk, even on
 *     Windows) and to capture from any specifically-named device the user picks.</li>
 * </ul>
 * On Windows, WASAPI loopback only ever mirrors the OS's current default playback device —
 * there is no per-device WASAPI loopback implemented here (see the README). So picking a
 * specific named device (anything other than "System Default") automatically switches capture
 * to the portable strategy targeting that exact device, while "System Default" keeps using
 * the platform-preferred strategy. This is what makes the device dropdown always have more
 * than one usable entry, and makes every entry actually do something when selected.
 */
public final class DeviceManager {

    private final AudioCaptureStrategy platformStrategy;
    private final JavaSoundLoopbackStrategy portableStrategy;
    private volatile AudioCaptureStrategy activeStrategy;
    private volatile AudioDevice selectedDevice = AudioDevice.SYSTEM_DEFAULT;

    public DeviceManager() {
        AudioCaptureStrategy preferred = createStrategyForPlatform();
        this.platformStrategy = preferred;
        this.portableStrategy = (preferred instanceof JavaSoundLoopbackStrategy portable)
                ? portable
                : new JavaSoundLoopbackStrategy();
        this.activeStrategy = preferred;
    }

    private static AudioCaptureStrategy createStrategyForPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return new WasapiLoopbackStrategy();
        }
        // Linux (PulseAudio/PipeWire monitor sources) and macOS (with a loopback driver
        // installed) both work through the portable Java Sound path.
        return new JavaSoundLoopbackStrategy();
    }

    /**
     * Lists every selectable output. Always starts with {@link AudioDevice#SYSTEM_DEFAULT},
     * followed by every individually named device the portable Java Sound enumeration can see.
     * Never throws; returns just the default entry if enumeration itself fails.
     */
    public List<AudioDevice> listDevices() {
        List<AudioDevice> result = new ArrayList<>();
        result.add(AudioDevice.SYSTEM_DEFAULT);
        try {
            for (AudioDevice device : portableStrategy.listDevices()) {
                if (!device.id().equals(AudioDevice.SYSTEM_DEFAULT.id())) {
                    result.add(device);
                }
            }
        } catch (Exception ignored) {
            // Enumeration failure just means only "System Default" is offered.
        }
        return result;
    }

    /**
     * Selects a device by its persisted id, falling back to {@link AudioDevice#SYSTEM_DEFAULT}
     * if the id is unknown or no longer present among the enumerated devices.
     *
     * @param deviceId id previously returned by {@link AudioDevice#id()}.
     * @return the resolved device that will actually be used.
     */
    public AudioDevice selectDeviceById(String deviceId) {
        if (deviceId == null || deviceId.isBlank() || deviceId.equals(AudioDevice.SYSTEM_DEFAULT.id())) {
            selectedDevice = AudioDevice.SYSTEM_DEFAULT;
            return selectedDevice;
        }
        for (AudioDevice device : listDevices()) {
            if (device.id().equals(deviceId)) {
                selectedDevice = device;
                return selectedDevice;
            }
        }
        // Requested device is gone (unplugged, driver changed) - fall back gracefully.
        selectedDevice = AudioDevice.SYSTEM_DEFAULT;
        return selectedDevice;
    }

    public AudioDevice getSelectedDevice() {
        return selectedDevice;
    }

    /**
     * Resolves which capture strategy instance should be used to capture {@code device}.
     * "System Default" uses the platform-preferred strategy; any specifically named device
     * uses the portable strategy targeting that exact mixer.
     */
    public AudioCaptureStrategy getStrategyFor(AudioDevice device) {
        if (device == null || device.id().equals(AudioDevice.SYSTEM_DEFAULT.id())) {
            return platformStrategy;
        }
        return portableStrategy;
    }

    /**
     * @return whichever strategy instance is currently the active one, i.e. the one that was
     * last successfully started (or is presumed active) by {@link AudioCaptureService}.
     */
    public AudioCaptureStrategy getActiveStrategy() {
        return activeStrategy;
    }

    /**
     * Records which strategy instance is now active, so future {@link #getActiveStrategy()}
     * calls (e.g. to stop it before switching) refer to the right one.
     */
    public void setActiveStrategy(AudioCaptureStrategy strategy) {
        this.activeStrategy = strategy;
    }

    /**
     * Forces capture onto the portable strategy outright, used when the platform-preferred
     * strategy fails to initialize entirely (see {@link AudioCaptureService}), so the mod still
     * has a working capture path rather than silently capturing nothing.
     */
    public AudioCaptureStrategy fallbackToPortableStrategy() {
        activeStrategy = portableStrategy;
        return portableStrategy;
    }
}
