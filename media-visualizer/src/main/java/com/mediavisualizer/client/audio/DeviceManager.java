package com.mediavisualizer.client.audio;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Owns the set of available {@link AudioDevice}s and decides which {@link AudioCaptureStrategy}
 * implementation is appropriate for the current operating system.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Enumerate output/loopback devices for display in the config GUI dropdown.</li>
 *     <li>Remember the user's selected device by id.</li>
 *     <li>Fall back to {@link AudioDevice#SYSTEM_DEFAULT} if the previously selected device
 *     is no longer present (e.g. USB headset unplugged).</li>
 * </ul>
 */
public final class DeviceManager {

    private volatile AudioCaptureStrategy strategy;
    private volatile AudioDevice selectedDevice = AudioDevice.SYSTEM_DEFAULT;

    public DeviceManager() {
        this.strategy = createStrategyForPlatform();
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
     * @return the active capture strategy. Starts as the platform-preferred strategy
     * (WASAPI on Windows) and may switch to {@link JavaSoundLoopbackStrategy} if that
     * fails to initialize; see {@link #fallbackToPortableStrategy()}.
     */
    public AudioCaptureStrategy getStrategy() {
        return strategy;
    }

    /**
     * Switches to the portable {@link JavaSoundLoopbackStrategy}, used automatically by
     * {@link AudioCaptureService} when the platform-preferred strategy (e.g. WASAPI on
     * Windows) fails to initialize, so the mod still has a working capture path rather
     * than silently capturing nothing.
     *
     * @return the strategy now in effect.
     */
    public synchronized AudioCaptureStrategy fallbackToPortableStrategy() {
        if (!(strategy instanceof JavaSoundLoopbackStrategy)) {
            strategy.stop();
            strategy = new JavaSoundLoopbackStrategy();
        }
        return strategy;
    }

    /**
     * Lists devices available for capture. Never throws; returns an empty list if enumeration
     * itself fails (e.g. no audio subsystem available), so the config screen can show a
     * friendly "No devices found" message instead of crashing.
     */
    public List<AudioDevice> listDevices() {
        try {
            return strategy.listDevices();
        } catch (Exception e) {
            return Collections.singletonList(AudioDevice.SYSTEM_DEFAULT);
        }
    }

    /**
     * Selects a device by its persisted id, falling back to the system default if the id is
     * unknown or no longer present among the enumerated devices.
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
}
