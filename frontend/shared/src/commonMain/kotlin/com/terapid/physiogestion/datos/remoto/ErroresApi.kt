package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.ErrorRespuesta
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Errores de comunicación con el backend. Cada uno trae un mensaje claro,
 * sin códigos técnicos, que la vista puede mostrar tal cual (RC-19).
 */
sealed class ErrorApi(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

class CredencialesInvalidas : ErrorApi("Usuario o contraseña incorrectos.")

class SesionVencida : ErrorApi("Tu sesión terminó. Inicia sesión de nuevo.")

class SinPermiso : ErrorApi("Tu perfil no tiene acceso a esta información.")

class NoEncontrado(mensaje: String) : ErrorApi(mensaje)

class ServidorNoDisponible(causa: Throwable? = null) : ErrorApi(
    "No pudimos conectar con el servidor. Verifica que el backend esté encendido e inténtalo de nuevo.",
    causa,
)

class RespuestaInesperada(causa: Throwable? = null) : ErrorApi(
    "El servidor respondió algo inesperado. Inténtalo más tarde.",
    causa,
)

/**
 * Ejecuta una llamada a la API y traduce cualquier falla a un [ErrorApi].
 * [esLogin] distingue un 401 por contraseña incorrecta de un 401 por sesión vencida.
 */
suspend fun <T> llamarApi(esLogin: Boolean = false, llamada: suspend () -> T): T =
    try {
        llamada()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ErrorApi) {
        throw e
    } catch (e: ResponseException) {
        throw when (e.response.status) {
            HttpStatusCode.Unauthorized -> if (esLogin) CredencialesInvalidas() else SesionVencida()
            HttpStatusCode.Forbidden -> SinPermiso()
            HttpStatusCode.NotFound -> NoEncontrado(mensajeDelServidor(e) ?: "No se encontró la información.")
            else -> RespuestaInesperada(e)
        }
    } catch (e: SerializationException) {
        throw RespuestaInesperada(e)
    } catch (e: Exception) {
        // Sin red, servidor apagado, tiempo de espera agotado, etc.
        throw ServidorNoDisponible(e)
    }

/** Lee el campo "mensaje" del JSON de error del backend, si viene. */
private suspend fun mensajeDelServidor(e: ResponseException): String? =
    try {
        e.response.body<ErrorRespuesta>().mensaje
    } catch (_: Exception) {
        null
    }
