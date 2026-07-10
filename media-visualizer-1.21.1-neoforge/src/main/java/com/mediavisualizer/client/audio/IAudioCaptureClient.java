package com.mediavisualizer.client.audio;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Minimal JNA vtable binding for the WASAPI {@code IAudioCaptureClient} COM interface.
 * Vtable indices follow {@code audioclient.h} (indices 0-2 belong to {@code IUnknown}):
 * <pre>
 * 3  GetBuffer
 * 4  ReleaseBuffer
 * 5  GetNextPacketSize
 * </pre>
 */
final class IAudioCaptureClient extends Unknown {

    IAudioCaptureClient(Pointer pointer) {
        super(pointer);
    }

    HRESULT getBuffer(PointerByReference dataOut, IntByReference numFramesOut, IntByReference flagsOut,
                       LongByReference devicePositionOut, LongByReference qpcPositionOut) {
        return (HRESULT) _invokeNativeObject(3,
                new Object[]{getPointer(), dataOut, numFramesOut, flagsOut, devicePositionOut, qpcPositionOut},
                HRESULT.class);
    }

    HRESULT releaseBuffer(int numFramesRead) {
        return (HRESULT) _invokeNativeObject(4, new Object[]{getPointer(), numFramesRead}, HRESULT.class);
    }

    HRESULT getNextPacketSize(IntByReference packetLengthOut) {
        return (HRESULT) _invokeNativeObject(5, new Object[]{getPointer(), packetLengthOut}, HRESULT.class);
    }
}
