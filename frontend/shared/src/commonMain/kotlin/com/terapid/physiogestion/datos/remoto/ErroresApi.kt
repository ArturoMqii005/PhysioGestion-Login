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

/** 429: demasiadas peticiones en poco tiempo, o el acceso se bloqueó por intentos fallidos (USR-15). */
class DemasiadasSolicitudes(mensaje: String = "Hiciste demasiadas solicitudes seguidas. Espera un momento.") : ErrorApi(mensaje)

/** 403 en el login: la contraseña es correcta, pero falta confirmar el correo (USR-20). */
class CorreoSinVerificar(mensaje: String, val correo: String) : ErrorApi(mensaje)

/** 403 en el login: el administrador desactivó la cuenta. */
class CuentaDesactivada(mensaje: String) : ErrorApi(mensaje)

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
        val error = if (codigo == 401 || codigo >= 500) null else errorDelServidor(e)
        throw when {
            codigo == 401 -> if (esLogin) CredencialesInvalidas() else SesionVencida()
            codigo == 403 && esLogin && error?.codigo == "CORREO_NO_VERIFICADO" ->
                CorreoSinVerificar(error.mensaje, error.correo.orEmpty())
            codigo == 403 && esLogin && error?.codigo == "CUENTA_DESACTIVADA" -> CuentaDesactivada(error.mensaje)
            codigo == 403 -> SinPermiso()
            codigo == 404 -> NoEncontrado(error?.mensaje ?: "No se encontró la información.")
            codigo == 400 || codigo == 422 ->
                DatosInvalidos(error?.mensaje ?: "Los datos enviados no son válidos.")
            codigo == 409 -> Conflicto(error?.mensaje ?: "La operación choca con otro registro.")
            codigo == 429 -> error?.mensaje?.let(::DemasiadasSolicitudes) ?: DemasiadasSolicitudes()
            codigo in 500..599 -> ErrorDelServidor(e)
            else -> RespuestaInesperada(e)
        }
    } catch (e: SerializationException) {
        throw RespuestaInesperada(e)
    } catch (e: Exception) {
        throw ServidorNoDisponible(e)
    }

/** Lee el JSON de error del backend ({"mensaje", "codigo", "correo"}), si viene. */
private suspend fun errorDelServidor(e: ResponseException): ErrorRespuesta? =
    try {
        e.response.body<ErrorRespuesta>()
    } catch (_: Exception) {
        null
    }

/** Resultado de una llamada y su resumen técnico (método, ruta y código), para el modo de desarrollo. */
data class Respuesta<T>(val datos: T, val detalle: String)

/** "PATCH /api/v1/sesiones/3 → 200 OK" */
fun HttpResponse.detalle(): String =
    "${call.request.method.value} ${call.request.url.encodedPath} → ${status.value} ${status.description}"
