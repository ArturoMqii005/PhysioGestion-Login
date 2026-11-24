package com.terapid.physiogestion

import android.app.Application
import com.terapid.physiogestion.dependencias.Dependencias
import com.terapid.physiogestion.plataforma.ContextoAndroid
import com.terapid.physiogestion.plataforma.notificaciones.NotificadorAndroid

/**
 * Se crea antes que cualquier pantalla y también cuando Android despierta a la
 * app solo para un trabajo en segundo plano (revisar avisos, un recordatorio).
 * Por eso aquí, y no en MainActivity, se entrega al código común el Context,
 * las variables de BuildConfig y el notificador de Android.
 */
class PhysioGestionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ContextoAndroid.inicializar(
            context = this,
            esDepuracion = BuildConfig.DEBUG,
            versionApp = BuildConfig.VERSION_NAME,
            servidorConfigurado = BuildConfig.SERVIDOR_URL,
        )
        // Notificaciones push (Firebase) y locales: canales y notificador de Android.
        Dependencias.notificador = NotificadorAndroid(this).also { it.crearCanales() }
    }
}
