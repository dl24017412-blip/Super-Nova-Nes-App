package com.example.jni

import android.view.Surface

object NativeBridge {
    init {
        System.loadLibrary("supernovasnes")
    }

    external fun nativeInit(storagePath: String): Boolean
    external fun nativeLoadRom(romBytes: ByteArray, filename: String): Int
    external fun nativeReset()
    external fun nativeStart()
    external fun nativePause()
    external fun nativeResume()
    external fun nativeStop()

    external fun nativeSetSurface(surface: Surface?): Boolean
    external fun nativeSurfaceChanged(width: Int, height: Int)
    external fun nativeSurfaceDestroyed()

    external fun nativeSetInputState(controller: Int, buttons: Int)

    external fun nativeSaveState(slot: Int, path: String): Boolean
    external fun nativeLoadState(slot: Int, path: String): Boolean
    external fun nativeSaveSram(path: String): Boolean
    external fun nativeLoadSram(path: String): Boolean

    external fun nativeSetVideoOptions(aspect: Int, filter: Int, profile: Int)
    external fun nativeSetAudioOptions(enabled: Boolean, volume: Float)
    external fun nativeGetStats(statsArray: FloatArray)

    external fun nativeGetRomTitle(): String
    external fun nativeGetRomSize(): Long
    external fun nativeIsHiRom(): Boolean
    external fun nativeHasBattery(): Boolean
}
