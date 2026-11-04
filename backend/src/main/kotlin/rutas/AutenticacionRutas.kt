package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.modelos.CorreoSolicitud
import com.terapid.physiogestion.modelos.ErrorRespuesta
import com.terapid.physiogestion.modelos.LoginSolicitud
import com.terapid.physiogestion.modelos.MensajeRespuesta
import com.terapid.physiogestion.modelos.RegistroSolicitud
import com.terapid.physiogestion.modelos.RestablecerSolicitud
import com.terapid.physiogestion.modelos.VerificarCorreoSolicitud
import com.terapid.physiogestion.plugins.AUTH_SESION
import com.terapid.physiogestion.plugins.LIMITE_CUENTAS
import com.terapid.physiogestion.servicios.AutenticacionServicio
import com.terapid.physiogestion.servicios.AvisoPrivacidadTexto
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.authorization
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Rutas públicas de cuentas. Todas pasan por el límite de 30 peticiones por
 * minuto por IP (SRV-10).
 *
 *  POST /auth/login                   usuario o correo + contraseña
 *  POST /auth/logout                  con token
 *  POST /auth/registro                código de invitación + datos de la cuenta (USR-01)
 *  POST /auth/verificar-correo        código de 6 dígitos (USR-20)
 *  POST /auth/reenviar-verificacion   otro código de verificación
 *  POST /auth/recuperacion            pide el código para restablecer (USR-10)
 *  POST /auth/restablecer             código + contraseña nueva (USR-11)
 *  GET  /aviso-privacidad             texto vigente (USR-21)
 */
fun Route.rutasAutenticacion(autenticacion: AutenticacionServicio) {
    get("/aviso-privacidad") { call.respond(AvisoPrivacidadTexto.vigente) }

    rateLimit(LIMITE_CUENTAS) {
        route("/auth") {
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

            post("/registro") {
                val respuesta = autenticacion.registrar(call.receive<RegistroSolicitud>())
                call.respond(HttpStatusCode.Created, respuesta)
            }

            post("/verificar-correo") {
                val s = call.receive<VerificarCorreoSolicitud>()
                call.respond(MensajeRespuesta(autenticacion.verificarCorreo(s.correo, s.codigo)))
            }

            post("/reenviar-verificacion") {
                val s = call.receive<CorreoSolicitud>()
                call.respond(HttpStatusCode.Accepted, MensajeRespuesta(autenticacion.reenviarVerificacion(s.correo)))
            }

            post("/recuperacion") {
                val s = call.receive<CorreoSolicitud>()
                // 202 Accepted: la solicitud se recibió; la respuesta no revela si el correo existe.
                call.respond(HttpStatusCode.Accepted, MensajeRespuesta(autenticacion.solicitarRecuperacion(s.correo)))
            }

            post("/restablecer") {
                val s = call.receive<RestablecerSolicitud>()
                call.respond(MensajeRespuesta(autenticacion.restablecer(s.correo, s.codigo, s.contrasenaNueva)))
            }

            authenticate(AUTH_SESION) {
                post("/logout") {
                    call.request.authorization()?.removePrefix("Bearer ")?.let(autenticacion::cerrarSesion)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
