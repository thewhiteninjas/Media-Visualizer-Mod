package com.mediavisualizer.client.audio;

/**
 * Immutable description of an audio output device that can be captured for visualization.
 *
 * @param id          a stable identifier used for persisting the user's selection in config.
 * @param displayName a human-friendly name shown in the config GUI dropdown.
 * @param isDefault   whether this device is the operating system's current default output.
 */
public record AudioDevice(String id, String displayName, boolean isDefault) {

    /**
     * Sentinel device representing "use whatever the OS reports as default", used when
     * the previously selected device is no longer present.
     */
    public static final AudioDevice SYSTEM_DEFAULT = new AudioDevice("system_default", "System Default", true);

    @Override
    public String toString() {
        return displayName;
    }
}
