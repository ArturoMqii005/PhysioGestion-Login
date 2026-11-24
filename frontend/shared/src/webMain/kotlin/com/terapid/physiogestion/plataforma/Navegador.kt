// js(...) es la interoperabilidad de Kotlin/Wasm con JavaScript; Kotlin pide marcarla
// porque su API todavía puede cambiar entre versiones.
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.terapid.physiogestion.plataforma

/*
 * Puente con las funciones de JavaScript del navegador (interoperabilidad de
 * Kotlin/Wasm): cada función ejecuta la expresión de js(...) y Kotlin recibe el
 * resultado ya convertido (String, Int, Double...). Es el único archivo de la app
 * que escribe JavaScript; el resto de webMain usa estas funciones.
 */

// ---------------------------------------------------------------- Página

/** Dominio de la página, por ejemplo "localhost" o "physiogestion-login-production.up.railway.app". */
internal fun dominioDeLaPagina(): String = js("window.location.hostname")

/** Navegador y sistema, por ejemplo "Mozilla/5.0 (Windows NT 10.0; ...) Chrome/142.0 ...". */
internal fun agenteDelNavegador(): String = js("navigator.userAgent")

// ---------------------------------------------------------------- Fecha y hora (hora local del navegador)

internal fun ahoraJs(): Double = js("Date.now()")

internal fun anioDe(millis: Double): Int = js("new Date(millis).getFullYear()")

/** Mes de 1 a 12 (JavaScript cuenta de 0 a 11). */
internal fun mesDe(millis: Double): Int = js("new Date(millis).getMonth() + 1")

internal fun diaDe(millis: Double): Int = js("new Date(millis).getDate()")

internal fun horaDe(millis: Double): Int = js("new Date(millis).getHours()")

internal fun minutoDe(millis: Double): Int = js("new Date(millis).getMinutes()")

internal fun segundoDe(millis: Double): Int = js("new Date(millis).getSeconds()")

// ---------------------------------------------------------------- localStorage (se conserva al cerrar el navegador)

internal fun leerLocal(clave: String): String? = js("window.localStorage.getItem(clave)")

internal fun guardarLocal(clave: String, valor: String): Unit = js("window.localStorage.setItem(clave, valor)")

internal fun borrarLocal(clave: String): Unit = js("window.localStorage.removeItem(clave)")

// ---------------------------------------------------------------- sessionStorage (se borra al cerrar la pestaña)

internal fun leerDeSesion(clave: String): String? = js("window.sessionStorage.getItem(clave)")

internal fun guardarEnSesion(clave: String, valor: String): Unit = js("window.sessionStorage.setItem(clave, valor)")

internal fun borrarDeSesion(clave: String): Unit = js("window.sessionStorage.removeItem(clave)")
