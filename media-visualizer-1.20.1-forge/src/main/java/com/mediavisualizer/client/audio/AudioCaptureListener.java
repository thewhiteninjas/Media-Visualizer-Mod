package com.mediavisualizer.client.audio;

/**
 * Callback invoked by an {@link AudioCaptureStrategy} whenever a new block of mono PCM
 * samples has been captured from the system output. Implementations should return quickly;
 * heavy processing (e.g. FFT) should be dispatched separately so the capture thread is never
 * starved and audio is not dropped.
 */
@FunctionalInterface
public interface AudioCaptureListener {

    /**
     * @param samples    mono PCM samples in the range [-1, 1].
     * @param sampleRate the sample rate, in Hz, of the delivered samples.
     */
    void onSamples(float[] samples, int sampleRate);
}
