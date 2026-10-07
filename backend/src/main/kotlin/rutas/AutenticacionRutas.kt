package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.modelos.ErrorRespuesta
import com.terapid.physiogestion.modelos.LoginSolicitud
import com.terapid.physiogestion.plugins.AUTH_SESION
import com.terapid.physiogestion.servicios.AutenticacionServicio
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.authorization
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.rutasAutenticacion(autenticacion: AutenticacionServicio) {
    route("/auth") {
        // El JSON del cuerpo se convierte en LoginSolicitud gracias a ContentNegotiation.
        post("/login") {
            val solicitud = call.receive<LoginSolicitud>()
            if (solicitud.usuario.isBlank() || solicitud.contrasena.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorRespuesta("Escribe tu usuario y tu contraseña."))
                return@post
            }
            val sesion = autenticacion.iniciarSesion(solicitud.usuario, solicitud.contrasena)
            if (sesion == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorRespuesta("Usuario o contraseña incorrectos."))
            } else {
                call.respond(sesion) // LoginRespuesta -> JSON
            }
        }
        authenticate(AUTH_SESION) {
            post("/logout") {
                call.request.authorization()?.removePrefix("Bearer ")?.let(autenticacion::cerrarSesion)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
