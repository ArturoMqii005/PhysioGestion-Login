package com.terapid.physiogestion.plataforma

import kotlin.experimental.ExperimentalNativeApi
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

/** Implementación iOS: la versión sale de Info.plist y la bandera de depuración del compilador. */
actual object Plataforma {
    @OptIn(ExperimentalNativeApi::class)
    actual val esDepuracion: Boolean
        get() = kotlin.native.Platform.isDebugBinary

    actual val nombre: String
        get() = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

    actual val versionApp: String
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "1.0"
}
