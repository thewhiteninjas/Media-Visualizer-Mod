package com.mediavisualizer.client.audio;

import com.mediavisualizer.client.config.VisualizerConfig;
import com.mediavisualizer.client.util.InterpolationUtil;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Top-level orchestrator that owns the audio capture lifecycle: it wires together
 * {@link DeviceManager}, {@link SpectrumAnalyzer}, and {@link VisualizerData}, and reacts to
 * relevant config changes (device, FFT size, bar count) by restarting capture as needed.
 * <p>
 * All capture and FFT work happens on the strategy's dedicated capture thread (see
 * {@link AudioCaptureStrategy#start}); this class itself does not spin up an additional
 * thread, it simply supplies the {@link AudioCaptureListener} callback that runs there.
 * The render thread only ever touches {@link #getVisualizerData()}, which is internally
 * synchronized, so there is no contention or blocking between the two threads.
 */
public final class AudioCaptureService {

    private final DeviceManager deviceManager;
    private final VisualizerConfig config;
    private final VisualizerData visualizerData;

    private final AtomicReference<SpectrumAnalyzer> analyzerRef = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile int lastKnownBarCount;
    private volatile int lastKnownFftSize;
    private volatile String lastKnownDeviceId = "";

    public AudioCaptureService(VisualizerConfig config) {
        this.config = config;
        this.deviceManager = new DeviceManager();
        this.lastKnownBarCount = config.barCount.get();
        this.lastKnownFftSize = config.fftSize.get();
        this.visualizerData = new VisualizerData(lastKnownBarCount);
        this.analyzerRef.set(new SpectrumAnalyzer(nextPowerOfTwo(lastKnownFftSize), lastKnownBarCount));
    }

    /**
     * Starts audio capture using the currently configured output device. Safe to call again
     * after {@link #stop()}; a no-op if already running.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        AudioDevice device = deviceManager.selectDeviceById(config.outputDeviceId.get());
        lastKnownDeviceId = device.id();
        beginCapture(device);
    }

    private void beginCapture(AudioDevice device) {
        AudioCaptureStrategy strategy = deviceManager.getStrategyFor(device);
        try {
            strategy.start(device, this::onSamplesCaptured);
            deviceManager.setActiveStrategy(strategy);
            System.out.println("[MediaVisualizer] Audio capture started using "
                    + strategy.getClass().getSimpleName() + " for device '" + device.displayName() + "'.");
        } catch (AudioCaptureStrategy.AudioCaptureException e) {
            System.err.println("[MediaVisualizer] Failed to start audio capture using "
                    + strategy.getClass().getSimpleName() + ": " + e.getMessage());
            attemptFallback(device, strategy);
        }
    }

    /**
     * Called when the resolved strategy fails to start. If that strategy wasn't already the
     * portable one, retries once with {@link JavaSoundLoopbackStrategy}; if that also fails,
     * gives up and leaves {@link #running} false so the mod doesn't spin retrying forever.
     */
    private void attemptFallback(AudioDevice device, AudioCaptureStrategy failedStrategy) {
        if (failedStrategy instanceof JavaSoundLoopbackStrategy) {
            // The portable fallback itself failed; nothing else to fall back to.
            running.set(false);
            return;
        }
        AudioCaptureStrategy fallback = deviceManager.fallbackToPortableStrategy();
        try {
            fallback.start(device, this::onSamplesCaptured);
            System.out.println("[MediaVisualizer] Audio capture started using fallback "
                    + fallback.getClass().getSimpleName() + ". On Windows this strategy requires "
                    + "enabling the 'Stereo Mix' recording device (Control Panel > Sound > Recording > "
                    + "right-click > Show Disabled Devices) or installing a virtual audio cable, "
                    + "otherwise no output audio will be captured.");
        } catch (AudioCaptureStrategy.AudioCaptureException e) {
            System.err.println("[MediaVisualizer] Fallback audio capture also failed: " + e.getMessage());
            running.set(false);
        }
    }

    /**
     * Invoked on the capture thread for every block of newly captured PCM samples.
     */
    private void onSamplesCaptured(float[] samples, int sampleRate) {
        if (!config.modEnabled.get()) {
            return;
        }

        reconfigureIfSettingsChanged();

        SpectrumAnalyzer analyzer = analyzerRef.get();
        boolean silentBlock = config.silenceDetection.get() && SpectrumAnalyzer.isSilent(samples);

        boolean produced = analyzer.ingest(
                samples,
                config.sensitivity.get().floatValue(),
                config.bassBoost.get().floatValue(),
                config.frequencyScaling.get());

        if (produced) {
            float[] target = analyzer.getBarOutput();
            if (silentBlock) {
                // Decay towards silence rather than snapping, so the visualizer settles smoothly.
                float[] silence = new float[target.length];
                InterpolationUtil.lerpInPlace(target, silence, 0.2f);
            }
            visualizerData.publish(target, silentBlock);
        }
    }

    /**
     * Detects config changes that require reallocating the analyzer or restarting the device,
     * and applies them. Called from the capture thread, which is the only thread that touches
     * the analyzer's internal buffers, so no additional locking is required here.
     */
    private void reconfigureIfSettingsChanged() {
        int configuredBarCount = config.barCount.get();
        int configuredFftSize = nextPowerOfTwo(config.fftSize.get());
        String configuredDeviceId = config.outputDeviceId.get();

        SpectrumAnalyzer analyzer = analyzerRef.get();

        if (configuredBarCount != lastKnownBarCount) {
            analyzer.setBarCount(configuredBarCount);
            visualizerData.resize(configuredBarCount);
            lastKnownBarCount = configuredBarCount;
        }

        if (configuredFftSize != lastKnownFftSize) {
            analyzer.setFftSize(configuredFftSize);
            lastKnownFftSize = configuredFftSize;
        }

        if (!configuredDeviceId.equals(lastKnownDeviceId)) {
            lastKnownDeviceId = configuredDeviceId;
            // Restart capture asynchronously; onSamplesCaptured itself must return quickly,
            // so hand off the restart rather than blocking the current capture thread.
            Thread restartThread = new Thread(this::restartWithConfiguredDevice, "MediaVisualizer-DeviceSwitch");
            restartThread.setDaemon(true);
            restartThread.start();
        }
    }

    private void restartWithConfiguredDevice() {
        AudioDevice device = deviceManager.selectDeviceById(config.outputDeviceId.get());
        deviceManager.getActiveStrategy().stop();
        beginCapture(device);
    }

    private static int nextPowerOfTwo(int value) {
        int power = Integer.highestOneBit(Math.max(1, value - 1)) << 1;
        return Math.max(256, power);
    }

    /**
     * Stops audio capture and releases OS resources. Safe to call multiple times.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            deviceManager.getActiveStrategy().stop();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public VisualizerData getVisualizerData() {
        return visualizerData;
    }

    public DeviceManager getDeviceManager() {
        return deviceManager;
    }
}
