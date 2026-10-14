package com.terapid.physiogestion.plataforma

import android.os.Build

/** Implementación ANDROID: los datos salen de BuildConfig (vía ContextoAndroid) y de Build.VERSION. */
actual object Plataforma {
    actual val esDepuracion: Boolean
        get() = ContextoAndroid.esDepuracion

    actual val nombre: String
        get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    actual val versionApp: String
        get() = ContextoAndroid.versionApp
}
