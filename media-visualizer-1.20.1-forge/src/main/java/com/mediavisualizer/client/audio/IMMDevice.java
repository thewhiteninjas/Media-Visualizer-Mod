package com.mediavisualizer.client.audio;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;

/**
 * Minimal JNA vtable binding for the WASAPI {@code IMMDevice} COM interface.
 * Vtable index 3 ({@code Activate}) matches the layout in {@code mmdeviceapi.h}.
 */
final class IMMDevice extends Unknown {

    IMMDevice(Pointer pointer) {
        super(pointer);
    }

    /**
     * {@code HRESULT Activate(REFIID iid, DWORD clsCtx, PROPVARIANT *activationParams, void **ppInterface)}
     */
    HRESULT activate(GUID iid, int clsCtx, Structure activationParams, PointerByReference interfaceOut) {
        Pointer paramsPointer = activationParams == null ? Pointer.NULL : activationParams.getPointer();
        return (HRESULT) _invokeNativeObject(3,
                new Object[]{getPointer(), iid, clsCtx, paramsPointer, interfaceOut}, HRESULT.class);
    }
}
