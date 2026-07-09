package com.mediavisualizer.client.audio;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * Mirrors the Win32 {@code WAVEFORMATEX} structure returned by {@code IAudioClient.GetMixFormat}.
 * Only the fields needed to interpret captured PCM data are exposed; the structure is read
 * directly out of the pointer returned by WASAPI (owned by the OS, freed via {@code CoTaskMemFree}
 * by the caller in a full implementation).
 */
public class WaveFormatEx extends Structure {
    public short wFormatTag;
    public short nChannels;
    public int nSamplesPerSec;
    public int nAvgBytesPerSec;
    public short nBlockAlign;
    public short wBitsPerSample;
    public short cbSize;

    private static final short WAVE_FORMAT_IEEE_FLOAT = 3;
    private static final short WAVE_FORMAT_EXTENSIBLE = (short) 0xFFFE;

    public WaveFormatEx(Pointer pointer) {
        super(pointer);
        read();
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("wFormatTag", "nChannels", "nSamplesPerSec", "nAvgBytesPerSec",
                "nBlockAlign", "wBitsPerSample", "cbSize");
    }

    /**
     * WASAPI mix formats are almost always {@code WAVE_FORMAT_IEEE_FLOAT} (32-bit float) or,
     * when extensible, carry the IEEE float sub-format GUID. This mod treats any 32-bit format
     * as float, which matches the near-universal behavior of the shared-mode mix format.
     */
    public boolean isIeeeFloat() {
        return wFormatTag == WAVE_FORMAT_IEEE_FLOAT
                || (wFormatTag == WAVE_FORMAT_EXTENSIBLE && wBitsPerSample == 32);
    }
}
