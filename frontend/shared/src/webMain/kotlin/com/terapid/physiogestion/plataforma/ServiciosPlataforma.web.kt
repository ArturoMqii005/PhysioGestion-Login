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

/** En desarrollo, el backend corre en la misma computadora que el navegador. */
actual fun urlServidorPorDefecto(): String = "http://localhost:8080"

private fun dosDigitos(n: Int): String = n.toString().padStart(2, '0')
