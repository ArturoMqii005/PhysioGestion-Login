package com.terapid.physiogestion.plataforma

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

// Implementación WEB de las funciones declaradas con "expect" en commonMain.

/** El navegador hace las peticiones con fetch(). */
actual fun crearMotorHttp(): HttpClientEngine = Js.create()

/** El navegador no guarda una base de datos SQLite (ver AlmacenamientoEnMemoria). */
actual fun rutaBaseDatos(nombreArchivo: String): String = nombreArchivo

actual fun ahoraEnMilisegundos(): Long = ahoraJs().toLong()

actual fun formatearFechaHora(millis: Long): String {
    val m = millis.toDouble()
    return "${dosDigitos(diaDe(m))}/${dosDigitos(mesDe(m))}/${anioDe(m)} ${dosDigitos(horaDe(m))}:${dosDigitos(minutoDe(m))}"
}

actual fun claveDelDia(millis: Long): String {
    val m = millis.toDouble()
    return "${anioDe(m)}-${dosDigitos(mesDe(m))}-${dosDigitos(diaDe(m))}"
}

actual fun fechaHoraIso(millis: Long): String {
    val m = millis.toDouble()
    return claveDelDia(millis) + "T${dosDigitos(horaDe(m))}:${dosDigitos(minutoDe(m))}:${dosDigitos(segundoDe(m))}"
}

/**
 * En desarrollo (página abierta en localhost):
 *  - Desde el servidor de desarrollo de Gradle (wasmJsBrowserDevelopmentRun, puerto 8081)
 *    se usa el backend de Railway: así basta con correr la web, sin levantar el backend,
 *    y no choca con otro programa que ocupe el puerto 8080 en la computadora.
 *  - Desde el backend local (http://localhost:8080/web/), ese mismo backend.
 * Se puede cambiar en el login con «Servidor · Cambiar».
 */
actual fun urlServidorPorDefecto(): String =
    if (puertoDeLaPagina() == PUERTO_DESARROLLO_WEB) Entorno.URL_SERVIDOR_NUBE else origenDeLaPagina()

/** Puerto de webpack-dev-server (devServer.port en shared/build.gradle.kts). */
private const val PUERTO_DESARROLLO_WEB = "8081"

private fun dosDigitos(n: Int): String = n.toString().padStart(2, '0')
