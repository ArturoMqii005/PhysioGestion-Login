package com.terapid.physiogestion.plataforma

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

// Implementación iOS de las funciones declaradas con "expect" en commonMain.

actual fun crearMotorHttp(): HttpClientEngine = Darwin.create()

actual fun rutaBaseDatos(nombreArchivo: String): String {
    // Carpeta Documents del sandbox de la app
    val documentos = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .first() as String
    return "$documentos/$nombreArchivo"
}

actual fun ahoraEnMilisegundos(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun formatearFechaHora(millis: Long): String = formatear(millis, "dd/MM/yyyy HH:mm", "es_MX")

actual fun claveDelDia(millis: Long): String = formatear(millis, "yyyy-MM-dd", "en_US_POSIX")

private fun formatear(millis: Long, patron: String, idioma: String): String {
    val formato = NSDateFormatter()
    formato.locale = NSLocale(localeIdentifier = idioma)
    formato.dateFormat = patron
    return formato.stringFromDate(NSDate.dateWithTimeIntervalSince1970(millis / 1000.0))
}

/** El simulador de iOS comparte la red de la Mac, así que localhost llega al backend. */
actual fun urlServidorPorDefecto(): String = "http://localhost:8080"
