package com.terapid.physiogestion.plugins

import com.terapid.physiogestion.modelos.ErrorRespuesta
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond

/** 403: el usuario autenticado no tiene permiso para esa ruta. */
class AccesoDenegado(
    mensaje: String = "No tienes permiso para consultar esta información.",
    val codigo: String? = null,
) : Exception(mensaje)

/** 403 en el login: la contraseña es correcta, pero falta confirmar el correo (USR-20). */
class CorreoSinVerificar(val correo: String) :
    Exception("Confirma tu correo con el código que te enviamos antes de iniciar sesión.")

/** 429: demasiados intentos (login bloqueado o código escrito mal muchas veces). */
class DemasiadosIntentos(mensaje: String, val segundos: Long) : Exception(mensaje)

/** 409: la operación choca con un registro existente (por ejemplo, dos sesiones con la misma hora de inicio). */
class Conflicto(mensaje: String) : Exception(mensaje)

/** 422: el JSON está bien formado, pero su contenido no cumple las reglas (dolor fuera de 0..10, etc.). */
class DatosNoProcesables(mensaje: String, val codigo: String? = null) : Exception(mensaje)

/**
 * Status Pages: convierte los errores en respuestas JSON con un mensaje
 * claro (ErrorRespuesta) y el código HTTP correcto.
 *
 *  400 Bad Request ........... JSON mal formado o parámetro inválido
 *  403 Forbidden ............. el rol no tiene permiso
 *  404 Not Found ............. el recurso no existe
 *  409 Conflict .............. choca con otro registro
 *  422 Unprocessable Entity .. datos fuera de las reglas de negocio
 *  429 Too Many Requests ..... demasiados intentos (con Retry-After)
 *  500 Internal Server Error . falla no controlada del servidor
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorRespuesta("La solicitud no tiene el formato esperado."))
        }
        exception<NotFoundException> { call, causa ->
            call.respond(HttpStatusCode.NotFound, ErrorRespuesta(causa.message ?: "No se encontró la información."))
        }
        exception<AccesoDenegado> { call, causa ->
            call.respond(HttpStatusCode.Forbidden, ErrorRespuesta(causa.message ?: "Acceso denegado.", causa.codigo))
        }
        exception<CorreoSinVerificar> { call, causa ->
            call.respond(HttpStatusCode.Forbidden, ErrorRespuesta(causa.message!!, "CORREO_NO_VERIFICADO", causa.correo))
        }
        exception<DemasiadosIntentos> { call, causa ->
            if (causa.segundos > 0) call.response.header(HttpHeaders.RetryAfter, causa.segundos.toString())
            call.respond(HttpStatusCode.TooManyRequests, ErrorRespuesta(causa.message!!, "DEMASIADOS_INTENTOS"))
        }
        exception<Conflicto> { call, causa ->
            call.respond(HttpStatusCode.Conflict, ErrorRespuesta(causa.message ?: "La operación choca con otro registro."))
        }
        exception<DatosNoProcesables> { call, causa ->
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorRespuesta(causa.message ?: "Los datos no se pudieron procesar.", causa.codigo))
        }
        exception<Throwable> { call, causa ->
            call.application.environment.log.error("Error no controlado", causa)
            call.respond(HttpStatusCode.InternalServerError, ErrorRespuesta("Ocurrió un error en el servidor."))
        }
    }
}
