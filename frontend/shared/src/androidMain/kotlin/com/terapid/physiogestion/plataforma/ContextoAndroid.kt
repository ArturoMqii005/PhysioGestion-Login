package com.terapid.physiogestion.plataforma

import android.content.Context

/**
 * Solo Android: guarda el Context de la aplicación, que Android exige para
 * saber en qué carpeta privada crear la base de datos. MainActivity lo
 * inicializa antes de mostrar la interfaz.
 */
object ContextoAndroid {
    lateinit var aplicacion: Context
        private set

    fun inicializar(context: Context) {
        aplicacion = context.applicationContext
    }
}
