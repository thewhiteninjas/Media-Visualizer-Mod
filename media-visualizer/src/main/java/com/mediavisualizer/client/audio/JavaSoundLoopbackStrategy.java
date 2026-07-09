package com.mediavisualizer.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Portable audio-output capture built on the standard {@code javax.sound.sampled} API.
 * <p>
 * The JDK's Java Sound implementation has no cross-platform concept of "loopback capture".
 * Instead, this strategy enumerates every {@link TargetDataLine} (recording) mixer the OS
 * exposes and looks for ones that represent an output loopback/monitor source rather than a
 * physical microphone:
 * <ul>
 *     <li><b>Linux (PulseAudio / PipeWire-Pulse):</b> the sound server automatically exposes a
 *     {@code Monitor of <sink>} recording source for every output sink, so this "just works"
 *     out of the box with no user configuration.</li>
 *     <li><b>Windows:</b> there is no automatic monitor device. The user must enable the
 *     built-in <i>"Stereo Mix"</i> recording device (Control Panel &gt; Sound &gt; Recording &gt;
 *     right-click &gt; Show Disabled Devices), or install a virtual audio cable such as
 *     VB-CABLE and set it as their playback device. This is documented in the mod's README.
 *     Where possible, {@link DeviceManager} prefers {@link WasapiLoopbackStrategy} on Windows,
 *     which does not require this manual step.</li>
 *     <li><b>macOS:</b> similarly requires a virtual loopback driver (e.g. BlackHole) to be
 *     installed, since CoreAudio does not expose loopback capture through Java Sound.</li>
 * </ul>
 * This class performs no allocations on the hot path once capture has started: the read
 * buffer and the converted float buffer are both pre-allocated in {@link #start}.
 */
public final class JavaSoundLoopbackStrategy implements AudioCaptureStrategy {

    private static final int SAMPLE_RATE = 48000;
    private static final int BUFFER_FRAMES = 1024;

    private Thread captureThread;
    private volatile boolean running;
    private TargetDataLine line;

    @Override
    public List<AudioDevice> listDevices() {
        List<AudioDevice> devices = new ArrayList<>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info info : mixers) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.getTargetLineInfo(new DataLine.Info(TargetDataLine.class, defaultFormat())).length == 0) {
                continue;
            }
            String name = info.getName();
            boolean looksLikeLoopback = looksLikeLoopbackDevice(name);
            // We list every recording-capable mixer, but flag likely loopback/monitor
            // sources so DeviceManager/ConfigScreen can sort them to the top.
            devices.add(new AudioDevice(info.getName(), formatDisplayName(name, looksLikeLoopback), false));
        }
        return devices;
    }

    private static boolean looksLikeLoopbackDevice(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("monitor") || lower.contains("stereo mix")
                || lower.contains("loopback") || lower.contains("what u hear")
                || lower.contains("cable output");
    }

    private static String formatDisplayName(String rawName, boolean isLoopback) {
        return isLoopback ? rawName + "  (loopback)" : rawName;
    }

    private static AudioFormat defaultFormat() {
        return new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    }

    @Override
    public void start(AudioDevice device, AudioCaptureListener listener) throws AudioCaptureException {
        stop();

        AudioFormat format = defaultFormat();
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);

        try {
            if (device == null || device == AudioDevice.SYSTEM_DEFAULT) {
                line = (TargetDataLine) AudioSystem.getLine(lineInfo);
            } else {
                Mixer.Info target = findMixerByName(device.id());
                if (target == null) {
                    throw new AudioCaptureException("Configured device not found, falling back to default: " + device.id());
                }
                Mixer mixer = AudioSystem.getMixer(target);
                line = (TargetDataLine) mixer.getLine(lineInfo);
            }
            line.open(format, BUFFER_FRAMES * 4);
            line.start();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new AudioCaptureException("Failed to open capture line: " + e.getMessage(), e);
        }

        running = true;
        captureThread = new Thread(() -> captureLoop(listener, format), "MediaVisualizer-JavaSoundCapture");
        captureThread.setDaemon(true);
        captureThread.setPriority(Thread.NORM_PRIORITY + 1);
        captureThread.start();
    }

    private static Mixer.Info findMixerByName(String name) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equals(name)) {
                return info;
            }
        }
        return null;
    }

    private void captureLoop(AudioCaptureListener listener, AudioFormat format) {
        // Pre-allocated once; reused for the lifetime of the capture thread to avoid
        // generating garbage on the audio thread.
        byte[] byteBuffer = new byte[BUFFER_FRAMES * format.getFrameSize()];
        float[] floatBuffer = new float[BUFFER_FRAMES];

        while (running && line != null) {
            int bytesRead = line.read(byteBuffer, 0, byteBuffer.length);
            if (bytesRead <= 0) {
                continue;
            }
            int frames = bytesRead / format.getFrameSize();
            for (int i = 0; i < frames; i++) {
                int sampleIndex = i * format.getFrameSize();
                // 16-bit little-endian signed PCM, mono.
                short sample = (short) ((byteBuffer[sampleIndex] & 0xFF) | (byteBuffer[sampleIndex + 1] << 8));
                floatBuffer[i] = sample / 32768.0f;
            }
            if (frames == floatBuffer.length) {
                listener.onSamples(floatBuffer, SAMPLE_RATE);
            } else {
                float[] trimmed = new float[frames];
                System.arraycopy(floatBuffer, 0, trimmed, 0, frames);
                listener.onSamples(trimmed, SAMPLE_RATE);
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
    }

    @Override
    public int getSampleRate() {
        return SAMPLE_RATE;
    }
}
