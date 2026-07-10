package com.mediavisualizer.client.audio;

/**
 * A reusable, allocation-free radix-2 Cooley-Tukey Fast Fourier Transform.
 * <p>
 * All working buffers ({@link #real}, {@link #imag}, {@link #window}, {@link #magnitudes})
 * are pre-allocated once in the constructor and reused for every call to {@link #process(float[])},
 * so this class produces zero garbage while the mod is running.
 * <p>
 * The FFT size must be a power of two. Common values are 512, 1024, 2048 and 4096.
 */
public final class FFTProcessor {

    private final int fftSize;
    private final float[] real;
    private final float[] imag;
    private final float[] window;
    private final float[] magnitudes;

    /**
     * Creates a processor for a fixed FFT size.
     *
     * @param fftSize power-of-two transform size, e.g. 1024.
     */
    public FFTProcessor(int fftSize) {
        if (Integer.bitCount(fftSize) != 1) {
            throw new IllegalArgumentException("fftSize must be a power of two, got " + fftSize);
        }
        this.fftSize = fftSize;
        this.real = new float[fftSize];
        this.imag = new float[fftSize];
        this.window = buildHannWindow(fftSize);
        // Only the first half of the spectrum is meaningful for real-valued input (Nyquist symmetry).
        this.magnitudes = new float[fftSize / 2];
    }

    private static float[] buildHannWindow(int size) {
        float[] w = new float[size];
        for (int i = 0; i < size; i++) {
            w[i] = 0.5f - 0.5f * (float) Math.cos((2.0 * Math.PI * i) / (size - 1));
        }
        return w;
    }

    /**
     * Runs a full FFT pass over {@code samples} (must have length {@code fftSize}) and
     * returns the internal magnitude buffer. The returned array is reused between calls;
     * callers must copy it if they need to retain values across frames.
     *
     * @param samples mono PCM samples in [-1, 1], length must equal {@link #getFftSize()}.
     * @return the internal magnitude spectrum buffer, length {@code fftSize / 2}.
     */
    public float[] process(float[] samples) {
        if (samples.length != fftSize) {
            throw new IllegalArgumentException("Expected " + fftSize + " samples, got " + samples.length);
        }

        // Apply the window function and load into the working real/imaginary buffers.
        for (int i = 0; i < fftSize; i++) {
            real[i] = samples[i] * window[i];
            imag[i] = 0.0f;
        }

        transformInPlace(real, imag);

        float normalization = 2.0f / fftSize;
        for (int i = 0; i < magnitudes.length; i++) {
            float re = real[i];
            float im = imag[i];
            magnitudes[i] = (float) Math.sqrt(re * re + im * im) * normalization;
        }

        return magnitudes;
    }

    /**
     * In-place iterative radix-2 Cooley-Tukey FFT (decimation in time).
     */
    private void transformInPlace(float[] re, float[] im) {
        int n = re.length;

        // Bit-reversal permutation.
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float tmpRe = re[i];
                re[i] = re[j];
                re[j] = tmpRe;
                float tmpIm = im[i];
                im[i] = im[j];
                im[j] = tmpIm;
            }
        }

        // Iterative butterfly computation.
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len;
            float wReal = (float) Math.cos(angle);
            float wImag = (float) Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                float curReal = 1.0f;
                float curImag = 0.0f;
                for (int k = 0; k < len / 2; k++) {
                    int evenIndex = i + k;
                    int oddIndex = i + k + len / 2;

                    float oddReal = re[oddIndex] * curReal - im[oddIndex] * curImag;
                    float oddImag = re[oddIndex] * curImag + im[oddIndex] * curReal;

                    re[oddIndex] = re[evenIndex] - oddReal;
                    im[oddIndex] = im[evenIndex] - oddImag;
                    re[evenIndex] += oddReal;
                    im[evenIndex] += oddImag;

                    float nextReal = curReal * wReal - curImag * wImag;
                    float nextImag = curReal * wImag + curImag * wReal;
                    curReal = nextReal;
                    curImag = nextImag;
                }
            }
        }
    }

    public int getFftSize() {
        return fftSize;
    }

    public int getMagnitudeBinCount() {
        return magnitudes.length;
    }
}
