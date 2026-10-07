package com.terapid.physiogestion.plataforma

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Implementación ANDROID de las funciones declaradas con "expect" en commonMain.

private val espanolMexico: Locale = Locale.forLanguageTag("es-MX")

actual fun crearMotorHttp(): HttpClientEngine = OkHttp.create()

actual fun rutaBaseDatos(nombreArchivo: String): String {
    // /data/data/com.terapid.physiogestion/databases/<archivo>
    val archivo = ContextoAndroid.aplicacion.getDatabasePath(nombreArchivo)
    archivo.parentFile?.mkdirs()
    return archivo.absolutePath
}

actual fun ahoraEnMilisegundos(): Long = System.currentTimeMillis()

actual fun formatearFechaHora(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", espanolMexico).format(Date(millis))

actual fun claveDelDia(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(millis))

/** El emulador de Android ve a la computadora (donde corre el backend) como 10.0.2.2. */
actual fun urlServidorPorDefecto(): String = "http://10.0.2.2:8080"
