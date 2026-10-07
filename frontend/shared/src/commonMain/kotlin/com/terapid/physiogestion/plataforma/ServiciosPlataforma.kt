package com.terapid.physiogestion.plataforma

import io.ktor.client.engine.HttpClientEngine

/*
 * Declaraciones "expect": el código compartido dice QUÉ necesita y cada
 * plataforma (androidMain, jvmMain, iosMain) aporta el "actual" con CÓMO
 * se hace ahí. Es el único punto donde el código común depende de la plataforma.
 */

/** Motor HTTP nativo: OkHttp en Android, Java HttpClient en escritorio, NSURLSession en iOS. */
expect fun crearMotorHttp(): HttpClientEngine

/** Ruta absoluta donde cada sistema operativo permite guardar la base de datos. */
expect fun rutaBaseDatos(nombreArchivo: String): String

/** Hora actual en milisegundos desde 1970. */
expect fun ahoraEnMilisegundos(): Long

/** Fecha y hora en formato de México, por ejemplo "29/09/2026 18:45" (RI-13). */
expect fun formatearFechaHora(millis: Long): String

/** Día calendario local ("2026-09-29"), usado para el tope diario de puntos. */
expect fun claveDelDia(millis: Long): String

/**
 * Dirección del backend Ktor en la etapa local. Cambia por plataforma porque
 * el emulador de Android ve a la computadora como 10.0.2.2, no como localhost.
 */
expect fun urlServidorPorDefecto(): String
