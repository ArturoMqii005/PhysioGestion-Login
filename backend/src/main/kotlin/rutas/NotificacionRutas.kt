package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.modelos.AnuncioSolicitud
import com.terapid.physiogestion.modelos.DispositivoSolicitud
import com.terapid.physiogestion.modelos.ErrorRespuesta
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.plugins.exigirRol
import com.terapid.physiogestion.plugins.usuarioActual
import com.terapid.physiogestion.servicios.AnunciosServicio
import com.terapid.physiogestion.servicios.PushServicio
import com.terapid.physiogestion.servicios.RevisionDiariaServicio
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.security.MessageDigest

/**
 * Notificaciones push (con token):
 *
 *  POST   /dispositivos            { token, plataforma } -> temas a los que quedó suscrito
 *  DELETE /dispositivos/{token}    al cerrar sesión -> 204
 *  GET    /anuncios                ADMINISTRADOR, RECEPCIONISTA: los más recientes
 *  POST   /anuncios                ADMINISTRADOR, RECEPCIONISTA: { titulo, texto, destinatarios } -> 201
 */
fun Route.rutasNotificaciones(push: PushServicio, anuncios: AnunciosServicio) {
    route("/dispositivos") {
        post {
            val usuario = call.usuarioActual()
            call.respond(push.registrar(usuario, call.receive<DispositivoSolicitud>()))
        }
        delete("/{token}") {
            val usuario = call.usuarioActual()
            push.quitar(usuario, call.parameters["token"].orEmpty())
            call.respond(HttpStatusCode.NoContent)
        }
    }
    route("/anuncios") {
        get {
            call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA)
            call.respond(anuncios.recientes())
        }
        post {
            val usuario = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA)
            call.respond(HttpStatusCode.Created, anuncios.publicar(usuario, call.receive<AnuncioSolicitud>()))
        }
    }
}

/**
 * Tareas programadas (sin token de usuario): las llama GitHub Actions con la clave
 * compartida en el encabezado X-Tarea-Clave (variable TAREA_CLAVE en Railway y
 * secreto TAREA_CLAVE en GitHub).
 *
 *  POST /tareas/revision-diaria    pacientes con 3 días o más sin sesiones -> aviso a su fisioterapeuta
 */
fun Route.rutasTareas(revision: RevisionDiariaServicio, clave: String?) {
    post("/tareas/revision-diaria") {
        if (clave.isNullOrBlank()) {
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorRespuesta("Las tareas programadas no están configuradas (falta TAREA_CLAVE)."))
            return@post
        }
        val dada = call.request.headers["X-Tarea-Clave"].orEmpty()
        if (!MessageDigest.isEqual(dada.toByteArray(), clave.toByteArray())) {
            call.respond(HttpStatusCode.Forbidden, ErrorRespuesta("La clave de la tarea no es correcta."))
            return@post
        }
        call.respond(revision.ejecutar())
    }
}
