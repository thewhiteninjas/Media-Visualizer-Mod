package com.mediavisualizer.client.audio;

import com.mediavisualizer.client.config.FrequencyScaling;
import com.mediavisualizer.client.util.MathUtil;

/**
 * Bridges {@link FFTProcessor} output to display-ready bar values.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Accumulates incoming PCM samples into a ring buffer until a full FFT window is ready.</li>
 *     <li>Runs the FFT and maps its magnitude bins onto the configured number of visual bars,
 *     using either linear or logarithmic frequency scaling.</li>
 *     <li>Applies sensitivity scaling, optional bass boost, and silence detection.</li>
 * </ul>
 * All buffers are pre-allocated at construction time and reused every call; the only exception is
 * {@link #setBarCount(int)} / {@link #setFftSize(int)}, which reallocate on demand when the user
 * changes those settings in the config screen (an infrequent, non-hot-path event).
 */
public final class SpectrumAnalyzer {

    private static final float SILENCE_RMS_THRESHOLD = 0.0015f;

    private FFTProcessor fftProcessor;
    private float[] ringBuffer;
    private int ringWritePos;
    private int samplesAccumulated;

    private float[] barOutput;
    private int barCount;

    public SpectrumAnalyzer(int fftSize, int barCount) {
        this.fftProcessor = new FFTProcessor(fftSize);
        this.ringBuffer = new float[fftSize];
        this.orderedSamplesScratch = new float[fftSize];
        this.barCount = barCount;
        this.barOutput = new float[barCount];
    }

    /**
     * Feeds newly captured PCM samples into the ring buffer. When enough samples have
     * accumulated to fill a full FFT window, this triggers analysis and returns {@code true}.
     * The caller (typically {@link AudioCaptureService}) should then read {@link #getBarOutput()}.
     *
     * @param samples      newly captured mono samples.
     * @param sensitivity  linear gain multiplier applied before FFT.
     * @param bassBoost    additional gain multiplier applied to the lowest quarter of bars.
     * @param scaling      how FFT bins are distributed across bars.
     * @return {@code true} if a new analysis frame was produced.
     */
    public boolean ingest(float[] samples, float sensitivity, float bassBoost, FrequencyScaling scaling) {
        boolean produced = false;
        for (float sample : samples) {
            ringBuffer[ringWritePos] = sample * sensitivity;
            ringWritePos = (ringWritePos + 1) % ringBuffer.length;
            samplesAccumulated++;

            if (samplesAccumulated >= ringBuffer.length) {
                analyze(bassBoost, scaling);
                samplesAccumulated = 0;
                produced = true;
            }
        }
        return produced;
    }

    private void analyze(float bassBoost, FrequencyScaling scaling) {
        // Read the ring buffer out in chronological order into the FFT's working array.
        // FFTProcessor owns its own real/imag buffers, so we only need a linear samples view.
        float[] ordered = orderedSamplesScratch;
        int start = ringWritePos;
        for (int i = 0; i < ringBuffer.length; i++) {
            ordered[i] = ringBuffer[(start + i) % ringBuffer.length];
        }

        float[] magnitudes = fftProcessor.process(ordered);
        mapMagnitudesToBars(magnitudes, bassBoost, scaling);
    }

    // Pre-allocated scratch buffer reused by analyze(); sized in constructor/resizers.
    private float[] orderedSamplesScratch;

    private void mapMagnitudesToBars(float[] magnitudes, float bassBoost, FrequencyScaling scaling) {
        int bassBoostBars = Math.max(1, barCount / 4);

        for (int bar = 0; bar < barCount; bar++) {
            int binIndex = switch (scaling) {
                case LINEAR -> (int) ((long) bar * magnitudes.length / barCount);
                case LOGARITHMIC -> MathUtil.logScaleIndex(bar, magnitudes.length, barCount);
            };
            binIndex = MathUtil.clamp(binIndex, 0, magnitudes.length - 1);

            float magnitude = magnitudes[binIndex];
            if (bar < bassBoostBars) {
                magnitude *= bassBoost;
            }

            // Compress with a soft log curve so quiet detail remains visible and loud peaks don't
            // instantly pin every bar at maximum height.
            float normalized = (float) Math.log10(1.0 + magnitude * 9.0);
            barOutput[bar] = MathUtil.clamp(normalized, 0.0f, 1.0f);
        }
    }

    /**
     * Computes RMS of a sample block to decide if the signal should be treated as silence.
     */
    public static boolean isSilent(float[] samples) {
        if (samples.length == 0) {
            return true;
        }
        double sumSquares = 0.0;
        for (float sample : samples) {
            sumSquares += sample * sample;
        }
        double rms = Math.sqrt(sumSquares / samples.length);
        return rms < SILENCE_RMS_THRESHOLD;
    }

    public float[] getBarOutput() {
        return barOutput;
    }

    public int getBarCount() {
        return barCount;
    }

    /**
     * Reconfigures the number of visual bars. Safe to call from the audio thread; not
     * intended to be called every frame.
     */
    public void setBarCount(int newBarCount) {
        this.barCount = newBarCount;
        this.barOutput = new float[newBarCount];
    }

    /**
     * Reconfigures the FFT window size, reallocating the ring buffer and FFT processor.
     */
    public void setFftSize(int newFftSize) {
        this.fftProcessor = new FFTProcessor(newFftSize);
        this.ringBuffer = new float[newFftSize];
        this.ringWritePos = 0;
        this.samplesAccumulated = 0;
        this.orderedSamplesScratch = new float[newFftSize];
    }
}
