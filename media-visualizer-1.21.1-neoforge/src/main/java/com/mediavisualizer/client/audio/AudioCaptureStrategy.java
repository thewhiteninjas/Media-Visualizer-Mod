package com.mediavisualizer.client.audio;

import java.util.List;

/**
 * Abstracts the platform-specific mechanism used to capture system OUTPUT audio
 * (i.e. "loopback" capture of whatever the OS is currently playing), as opposed to
 * capturing from a microphone input.
 * <p>
 * Two implementations are provided:
 * <ul>
 *     <li>{@link WasapiLoopbackStrategy} - true process/output loopback on Windows via
 *     the WASAPI {@code AUDCLNT_STREAMFLAGS_LOOPBACK} flag, accessed through JNA.</li>
 *     <li>{@link JavaSoundLoopbackStrategy} - a portable fallback built on the standard
 *     {@code javax.sound.sampled} API. It works wherever the OS exposes a loopback/monitor
 *     source as a normal recording device (e.g. PulseAudio/PipeWire "Monitor of ..." sources
 *     on Linux, or "Stereo Mix" on Windows once enabled in Sound Control Panel).</li>
 * </ul>
 * {@link DeviceManager} selects the best available strategy for the current platform.
 */
public interface AudioCaptureStrategy extends AutoCloseable {

    /**
     * Lists the output devices this strategy can capture from.
     */
    List<AudioDevice> listDevices();

    /**
     * Opens the given device (or the OS default if {@code null}) and begins delivering
     * PCM frames to {@code listener} on an internal capture thread. This call returns
     * immediately; capture happens asynchronously.
     *
     * @param device   device to capture, or {@code null} for the system default.
     * @param listener callback invoked with newly captured mono float samples in [-1, 1].
     * @throws AudioCaptureException if the device cannot be opened.
     */
    void start(AudioDevice device, AudioCaptureListener listener) throws AudioCaptureException;

    /**
     * Stops capture and releases any native/OS resources. Safe to call multiple times.
     */
    void stop();

    /**
     * The sample rate, in Hz, that this strategy delivers samples at once started.
     */
    int getSampleRate();

    @Override
    default void close() {
        stop();
    }

    /**
     * Thrown when a capture strategy fails to initialize or open a device.
     */
    class AudioCaptureException extends Exception {
        public AudioCaptureException(String message) {
            super(message);
        }

        public AudioCaptureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
