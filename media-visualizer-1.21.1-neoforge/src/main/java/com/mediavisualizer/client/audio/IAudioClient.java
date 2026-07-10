package com.mediavisualizer.client.audio;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;

/**
 * Minimal JNA vtable binding for the WASAPI {@code IAudioClient} COM interface.
 * Vtable indices follow the declaration order in {@code audioclient.h}
 * (indices 0-2 belong to {@code IUnknown}):
 * <pre>
 * 3  Initialize
 * 4  GetBufferSize
 * 5  GetStreamLatency
 * 6  GetCurrentPadding
 * 7  IsFormatSupported
 * 8  GetMixFormat
 * 9  GetDevicePeriod
 * 10 Start
 * 11 Stop
 * 12 Reset
 * 13 SetEventHandle
 * 14 GetService
 * </pre>
 */
final class IAudioClient extends Unknown {

    IAudioClient(Pointer pointer) {
        super(pointer);
    }

    HRESULT initialize(int shareMode, int streamFlags, long bufferDuration, long periodicity,
                        Pointer format, GUID audioSessionGuid) {
        return (HRESULT) _invokeNativeObject(3,
                new Object[]{getPointer(), shareMode, streamFlags, bufferDuration, periodicity, format, audioSessionGuid},
                HRESULT.class);
    }

    WaveFormatEx getMixFormat() {
        PointerByReference formatPtr = new PointerByReference();
        HRESULT hr = (HRESULT) _invokeNativeObject(8, new Object[]{getPointer(), formatPtr}, HRESULT.class);
        if (hr.intValue() != 0) {
            throw new IllegalStateException("GetMixFormat failed: 0x" + Integer.toHexString(hr.intValue()));
        }
        return new WaveFormatEx(formatPtr.getValue());
    }

    HRESULT start() {
        return (HRESULT) _invokeNativeObject(10, new Object[]{getPointer()}, HRESULT.class);
    }

    HRESULT stop() {
        return (HRESULT) _invokeNativeObject(11, new Object[]{getPointer()}, HRESULT.class);
    }

    HRESULT getService(GUID iid, PointerByReference serviceOut) {
        return (HRESULT) _invokeNativeObject(14, new Object[]{getPointer(), iid, serviceOut}, HRESULT.class);
    }
}
