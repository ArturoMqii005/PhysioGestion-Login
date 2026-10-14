package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.ErrorRespuesta
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Errores de comunicación con el backend. Cada uno trae un mensaje claro,
 * sin códigos técnicos, que la vista puede mostrar tal cual (RC-19).
 */
sealed class ErrorApi(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

/** 401 en el login. */
class CredencialesInvalidas : ErrorApi("Usuario o contraseña incorrectos.")

/** 401 en cualquier otra ruta: el token expiró o el servidor se reinició. */
class SesionVencida : ErrorApi("Tu sesión terminó. Inicia sesión de nuevo.")

/** 403: el perfil no tiene permiso sobre ese recurso. */
class SinPermiso : ErrorApi("Tu perfil no tiene acceso a esta información.")

/** 404: el recurso no existe. */
class NoEncontrado(mensaje: String) : ErrorApi(mensaje)

/** 400 y 422: lo que se envió no cumple las validaciones. */
class DatosInvalidos(mensaje: String) : ErrorApi(mensaje)

/** 409: choca con otro registro (por ejemplo, dos sesiones con la misma hora de inicio). */
class Conflicto(mensaje: String) : ErrorApi(mensaje)

/** 429: demasiadas peticiones en poco tiempo. */
class DemasiadasSolicitudes : ErrorApi("Hiciste demasiadas solicitudes seguidas. Espera un momento.")

/** 5xx: la falla está en el servidor, no en la app. */
class ErrorDelServidor(causa: Throwable? = null) : ErrorApi(
    "El servidor tuvo un problema. Inténtalo más tarde.",
    causa,
)

/** Sin red, servidor apagado o tiempo de espera agotado. */
class ServidorNoDisponible(causa: Throwable? = null) : ErrorApi(
    "No pudimos conectar con el servidor. Verifica que el backend esté encendido e inténtalo de nuevo.",
    causa,
)

class RespuestaInesperada(causa: Throwable? = null) : ErrorApi(
    "El servidor respondió algo inesperado. Inténtalo más tarde.",
    causa,
)

/**
 * Ejecuta una llamada a la API y traduce cualquier falla a un [ErrorApi]
 * según el código de estado HTTP. Así todos los errores se muestran con el
 * mismo criterio y la vista nunca ve una excepción técnica.
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
        val codigo = e.response.status.value
        throw when {
            codigo == 401 -> if (esLogin) CredencialesInvalidas() else SesionVencida()
            codigo == 403 -> SinPermiso()
            codigo == 404 -> NoEncontrado(mensajeDelServidor(e) ?: "No se encontró la información.")
            codigo == 400 || codigo == 422 ->
                DatosInvalidos(mensajeDelServidor(e) ?: "Los datos enviados no son válidos.")
            codigo == 409 -> Conflicto(mensajeDelServidor(e) ?: "La operación choca con otro registro.")
            codigo == 429 -> DemasiadasSolicitudes()
            codigo in 500..599 -> ErrorDelServidor(e)
            else -> RespuestaInesperada(e)
        }
    } catch (e: SerializationException) {
        throw RespuestaInesperada(e)
    } catch (e: Exception) {
        throw ServidorNoDisponible(e)
    }

/** Lee el campo "mensaje" del JSON de error del backend, si viene. */
private suspend fun mensajeDelServidor(e: ResponseException): String? =
    try {
        e.response.body<ErrorRespuesta>().mensaje
    } catch (_: Exception) {
        null
    }

/** Resultado de una llamada y su resumen técnico (método, ruta y código), para el modo de desarrollo. */
data class Respuesta<T>(val datos: T, val detalle: String)

/** "PATCH /api/v1/sesiones/3 → 200 OK" */
fun HttpResponse.detalle(): String =
    "${call.request.method.value} ${call.request.url.encodedPath} → ${status.value} ${status.description}"
