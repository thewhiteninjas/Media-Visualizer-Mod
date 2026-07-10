package com.mediavisualizer.client.audio;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;

/**
 * Minimal JNA vtable binding for the WASAPI {@code IMMDeviceEnumerator} COM interface.
 * Only the single method required by {@link WasapiLoopbackStrategy} is exposed.
 * Per {@code mmdeviceapi.h} (indices 0-2 belong to {@code IUnknown}):
 * <pre>
 * 3  EnumAudioEndpoints      (not used here)
 * 4  GetDefaultAudioEndpoint
 * 5  GetDevice
 * </pre>
 */
final class IMMDeviceEnumerator extends Unknown {

    IMMDeviceEnumerator(Pointer pointer) {
        super(pointer);
    }

    /**
     * {@code HRESULT GetDefaultAudioEndpoint(EDataFlow dataFlow, ERole role, IMMDevice **ppEndpoint)}
     */
    HRESULT getDefaultAudioEndpoint(int dataFlow, int role, PointerByReference endpointOut) {
        return (HRESULT) _invokeNativeObject(4,
                new Object[]{getPointer(), dataFlow, role, endpointOut}, HRESULT.class);
    }
}
