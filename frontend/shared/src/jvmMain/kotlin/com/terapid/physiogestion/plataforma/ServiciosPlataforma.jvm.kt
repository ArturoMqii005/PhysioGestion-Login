package com.terapid.physiogestion.plataforma

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Implementación ESCRITORIO (JVM) de las funciones declaradas con "expect" en commonMain.

private val espanolMexico: Locale = Locale.forLanguageTag("es-MX")

actual fun crearMotorHttp(): HttpClientEngine = Java.create()

actual fun rutaBaseDatos(nombreArchivo: String): String {
    // Carpeta .physiogestion dentro del usuario (C:\Users\<usuario>\.physiogestion en Windows)
    val carpeta = File(System.getProperty("user.home"), ".physiogestion").apply { mkdirs() }
    return File(carpeta, nombreArchivo).absolutePath
}

actual fun ahoraEnMilisegundos(): Long = System.currentTimeMillis()

actual fun formatearFechaHora(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", espanolMexico).format(Date(millis))

actual fun claveDelDia(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(millis))

/** En escritorio la app y el backend corren en la misma computadora. */
actual fun urlServidorPorDefecto(): String = "http://localhost:8080"
