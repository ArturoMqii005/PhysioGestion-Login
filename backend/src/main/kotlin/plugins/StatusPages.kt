package com.terapid.physiogestion.plugins

import com.terapid.physiogestion.modelos.ErrorRespuesta
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

/** El usuario autenticado no tiene permiso para esa ruta. */
class AccesoDenegado(mensaje: String = "No tienes permiso para consultar esta información.") : Exception(mensaje)

/**
 * Status Pages: convierte los errores en respuestas JSON con un mensaje
 * claro (ErrorRespuesta) y el código HTTP correcto.
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
            call.respond(HttpStatusCode.Forbidden, ErrorRespuesta(causa.message ?: "Acceso denegado."))
        }
        exception<Throwable> { call, causa ->
            call.application.environment.log.error("Error no controlado", causa)
            call.respond(HttpStatusCode.InternalServerError, ErrorRespuesta("Ocurrió un error en el servidor."))
        }
    }
}
