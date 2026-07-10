package com.mediavisualizer.client.audio;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.Guid.CLSID;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * True system-output loopback capture for Windows, implemented on top of the WASAPI
 * ("Windows Audio Session API") Core Audio interfaces via raw COM interop through JNA.
 * <p>
 * Unlike {@link JavaSoundLoopbackStrategy}, this strategy captures the actual render (output)
 * stream directly using the {@code AUDCLNT_STREAMFLAGS_LOOPBACK} flag on {@code IAudioClient},
 * exactly like OBS Studio and other screen/desktop-audio recorders do. This means it works
 * out of the box without the user needing to enable "Stereo Mix" or install a virtual cable.
 * <p>
 * This class is intentionally isolated behind {@link AudioCaptureStrategy} so the rest of the
 * mod is completely unaware of the platform-specific mechanics. {@link DeviceManager} only
 * instantiates it on Windows, and falls back to {@link JavaSoundLoopbackStrategy} everywhere
 * else, or if COM initialization fails for any reason (e.g. missing permissions).
 * <p>
 * <b>Threading:</b> {@code CoInitializeEx} is called with {@code COINIT_MULTITHREADED} on a
 * dedicated capture thread, since COM apartments are thread-local; all WASAPI calls for a given
 * client must happen from the thread that initialized COM for it.
 */
public final class WasapiLoopbackStrategy implements AudioCaptureStrategy {

    private static final GUID CLSID_MMDEVICE_ENUMERATOR =
            new CLSID("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private static final GUID IID_IMMDEVICE_ENUMERATOR =
            new IID("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private static final GUID IID_IAUDIO_CLIENT =
            new IID("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2");
    private static final GUID IID_IAUDIO_CAPTURE_CLIENT =
            new IID("C8ADBD64-E71E-48a0-A4DE-185C395CD317");

    private static final int EDATAFLOW_RENDER = 0;
    private static final int ERole_CONSOLE = 0;
    private static final int AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000;
    private static final int AUDCLNT_SHAREMODE_SHARED = 0;
    /**
     * CLSCTX_ALL = CLSCTX_INPROC_SERVER(0x1) | CLSCTX_INPROC_HANDLER(0x2) |
     * CLSCTX_LOCAL_SERVER(0x4) | CLSCTX_REMOTE_SERVER(0x10), per {@code objidl.h}. Defined
     * locally rather than pulled from a JNA constant to avoid depending on its exact location,
     * which has moved between JNA-platform versions.
     */
    private static final int CLSCTX_ALL = 0x1 | 0x2 | 0x4 | 0x10;
    /** 1,000,000 * 100ns units = 1 second buffer, matching common WASAPI loopback samples. */
    private static final long BUFFER_DURATION_100NS = 10_000_000L;

    private volatile boolean running;
    private Thread captureThread;
    private volatile CountDownLatch initLatch;
    private volatile AudioCaptureException initError;

    @Override
    public List<AudioDevice> listDevices() {
        // Enumerating individual render endpoints via IMMDeviceCollection is possible but
        // adds significant additional COM boilerplate. Since loopback always mirrors
        // whatever is currently configured as the Windows default playback device, we expose
        // a single logical entry and let the OS-level "Sound Settings" control which physical
        // device that refers to. Advanced users can still pick a specific device through the
        // portable JavaSoundLoopbackStrategy device list if desired.
        List<AudioDevice> devices = new ArrayList<>();
        devices.add(AudioDevice.SYSTEM_DEFAULT);
        return devices;
    }

    @Override
    public void start(AudioDevice device, AudioCaptureListener listener) throws AudioCaptureException {
        stop();
        running = true;
        initLatch = new CountDownLatch(1);
        initError = null;

        captureThread = new Thread(() -> runCaptureThread(listener), "MediaVisualizer-WasapiCapture");
        captureThread.setDaemon(true);
        captureThread.setPriority(Thread.NORM_PRIORITY + 1);
        captureThread.start();

        // Block briefly so failures during COM/WASAPI setup surface synchronously to the
        // caller (AudioCaptureService), which can then fall back to JavaSoundLoopbackStrategy
        // instead of silently sitting idle forever.
        boolean signaled;
        try {
            signaled = initLatch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
            throw new AudioCaptureException("Interrupted while waiting for WASAPI capture to initialize", e);
        }

        if (!signaled) {
            running = false;
            throw new AudioCaptureException("WASAPI capture did not initialize within 3 seconds");
        }
        if (initError != null) {
            running = false;
            throw initError;
        }
    }

    private void runCaptureThread(AudioCaptureListener listener) {
        HRESULT hr = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
        boolean comInitialized = COMUtils.SUCCEEDED(hr) || hr.intValue() == 1 /* S_FALSE, already initialized */;

        PointerByReference enumeratorPtr = new PointerByReference();
        PointerByReference devicePtr = new PointerByReference();
        PointerByReference clientPtr = new PointerByReference();
        PointerByReference captureClientPtr = new PointerByReference();

        try {
            if (!comInitialized) {
                throw new AudioCaptureException("CoInitializeEx failed: 0x" + Integer.toHexString(hr.intValue()));
            }

            checkResult("CoCreateInstance(MMDeviceEnumerator)", Ole32.INSTANCE.CoCreateInstance(
                    CLSID_MMDEVICE_ENUMERATOR, null, CLSCTX_ALL,
                    IID_IMMDEVICE_ENUMERATOR, enumeratorPtr));

            IMMDeviceEnumerator enumerator = new IMMDeviceEnumerator(enumeratorPtr.getValue());
            checkResult("GetDefaultAudioEndpoint", enumerator.getDefaultAudioEndpoint(
                    EDATAFLOW_RENDER, ERole_CONSOLE, devicePtr));

            IMMDevice mmDevice = new IMMDevice(devicePtr.getValue());
            checkResult("IMMDevice.Activate(IAudioClient)", mmDevice.activate(
                    IID_IAUDIO_CLIENT, CLSCTX_ALL, null, clientPtr));

            IAudioClient audioClient = new IAudioClient(clientPtr.getValue());

            WaveFormatEx mixFormatPtrHolder = audioClient.getMixFormat();

            checkResult("IAudioClient.Initialize", audioClient.initialize(
                    AUDCLNT_SHAREMODE_SHARED, AUDCLNT_STREAMFLAGS_LOOPBACK,
                    BUFFER_DURATION_100NS, 0, mixFormatPtrHolder.getPointer(), null));

            checkResult("IAudioClient.GetService(IAudioCaptureClient)", audioClient.getService(
                    IID_IAUDIO_CAPTURE_CLIENT, captureClientPtr));

            IAudioCaptureClient captureClient = new IAudioCaptureClient(captureClientPtr.getValue());

            int sampleRate = mixFormatPtrHolder.nSamplesPerSec;
            int channels = mixFormatPtrHolder.nChannels;
            boolean isFloat = mixFormatPtrHolder.isIeeeFloat();

            checkResult("IAudioClient.Start", audioClient.start());

            // Initialization succeeded: unblock start() and begin the steady-state loop.
            System.out.println("[MediaVisualizer] WASAPI loopback capture started ("
                    + sampleRate + "Hz, " + channels + " channel(s)).");
            initLatch.countDown();

            float[] monoBuffer = new float[4096];

            while (running) {
                IntByReference packetLength = new IntByReference();
                checkResult("GetNextPacketSize", captureClient.getNextPacketSize(packetLength));

                if (packetLength.getValue() == 0) {
                    Thread.sleep(5);
                    continue;
                }

                PointerByReference dataPtr = new PointerByReference();
                IntByReference numFrames = new IntByReference();
                IntByReference flags = new IntByReference();

                checkResult("GetBuffer", captureClient.getBuffer(dataPtr, numFrames, flags, null, null));

                int frames = numFrames.getValue();
                if (frames > 0) {
                    monoBuffer = ensureCapacity(monoBuffer, frames);
                    downmixToMono(dataPtr.getValue(), frames, channels, isFloat, monoBuffer);
                    float[] delivered = frames == monoBuffer.length ? monoBuffer : java.util.Arrays.copyOf(monoBuffer, frames);
                    listener.onSamples(delivered, sampleRate);
                }

                checkResult("ReleaseBuffer", captureClient.releaseBuffer(frames));
            }

            checkResult("IAudioClient.Stop", audioClient.stop());
        } catch (AudioCaptureException | InterruptedException e) {
            // Capture cannot continue; the render thread will keep displaying the last known
            // (decaying) values. If this happened during setup (before the latch was signaled),
            // propagate the failure back to start() so it can fall back to another strategy.
            System.err.println("[MediaVisualizer] WASAPI loopback capture stopped: " + e.getMessage());
            if (initLatch.getCount() > 0) {
                initError = (e instanceof AudioCaptureException ace)
                        ? ace
                        : new AudioCaptureException("WASAPI capture interrupted during initialization", e);
                initLatch.countDown();
            }
        } finally {
            releaseIfPresent(captureClientPtr);
            releaseIfPresent(clientPtr);
            releaseIfPresent(devicePtr);
            releaseIfPresent(enumeratorPtr);
            if (comInitialized) {
                Ole32.INSTANCE.CoUninitialize();
            }
        }
    }

    private static float[] ensureCapacity(float[] buffer, int required) {
        if (buffer.length >= required) {
            return buffer;
        }
        return new float[Integer.highestOneBit(required - 1) << 1];
    }

    /**
     * Downmixes an interleaved multi-channel buffer (float or 16-bit PCM) to mono by averaging
     * channels, writing results into {@code out}. No allocation occurs when {@code out} is
     * already large enough.
     */
    private static void downmixToMono(Pointer data, int frames, int channels, boolean isFloat, float[] out) {
        if (isFloat) {
            float[] interleaved = data.getFloatArray(0, frames * channels);
            for (int f = 0; f < frames; f++) {
                float sum = 0f;
                for (int c = 0; c < channels; c++) {
                    sum += interleaved[f * channels + c];
                }
                out[f] = sum / channels;
            }
        } else {
            short[] interleaved = data.getShortArray(0, frames * channels);
            for (int f = 0; f < frames; f++) {
                float sum = 0f;
                for (int c = 0; c < channels; c++) {
                    sum += interleaved[f * channels + c] / 32768.0f;
                }
                out[f] = sum / channels;
            }
        }
    }

    private static void checkResult(String call, HRESULT hr) throws AudioCaptureException {
        if (!COMUtils.SUCCEEDED(hr)) {
            throw new AudioCaptureException(call + " failed: 0x" + Integer.toHexString(hr.intValue()));
        }
    }

    private static void releaseIfPresent(PointerByReference ref) {
        if (ref != null && ref.getValue() != null) {
            new IUnknownRef(ref.getValue()).release();
        }
    }

    @Override
    public void stop() {
        running = false;
        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }
    }

    @Override
    public int getSampleRate() {
        // Actual rate is determined per-session from the WASAPI mix format and passed
        // to the listener with every callback; this getter reports the common default.
        return 48000;
    }

    /**
     * Minimal helper for releasing raw IUnknown-derived COM pointers without needing a
     * fully typed wrapper for every interface.
     */
    private static final class IUnknownRef extends com.sun.jna.platform.win32.COM.Unknown {
        IUnknownRef(Pointer pointer) {
            super(pointer);
        }

        void release() {
            this.Release();
        }
    }
}
