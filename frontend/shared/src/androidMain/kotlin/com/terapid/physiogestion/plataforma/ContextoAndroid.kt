package com.terapid.physiogestion.plataforma

import android.content.Context

/**
 * Solo Android: guarda el Context de la aplicación (Android lo exige para
 * abrir la base de datos, las preferencias, los sensores y la caché) y los
 * datos que llegan de BuildConfig. MainActivity lo inicializa antes de
 * mostrar la interfaz.
 */
object ContextoAndroid {
    lateinit var aplicacion: Context
        private set

    /** BuildConfig.DEBUG del módulo androidApp. */
    var esDepuracion: Boolean = true
        private set

    /** BuildConfig.VERSION_NAME del módulo androidApp. */
    var versionApp: String = "1.0"
        private set

    /** BuildConfig.SERVIDOR_URL: viene de local.properties; vacío = usar 10.0.2.2. */
    var servidorConfigurado: String = ""
        private set

    fun inicializar(
        context: Context,
        esDepuracion: Boolean = true,
        versionApp: String = "1.0",
        servidorConfigurado: String = "",
    ) {
        aplicacion = context.applicationContext
        this.esDepuracion = esDepuracion
        this.versionApp = versionApp
        this.servidorConfigurado = servidorConfigurado.trim()
    }
}
