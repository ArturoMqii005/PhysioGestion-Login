package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.modelos.MarcarLeidosSolicitud
import com.terapid.physiogestion.plugins.usuarioActual
import com.terapid.physiogestion.servicios.AvisosServicio
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Recurso /avisos: cada usuario solo ve los suyos.
 *
 *  GET  /avisos?desde=12   bandeja (los más recientes primero); con «desde», solo los de id mayor
 *  POST /avisos/leidos     { "ids": [3, 4] } o { "todos": true } -> bandeja actualizada
 */
fun Route.rutasAvisos(avisos: AvisosServicio) {
    route("/avisos") {
        get {
            val usuario = call.usuarioActual()
            val desde = call.request.queryParameters["desde"]?.let {
                it.toIntOrNull() ?: throw BadRequestException("desde debe ser un número.")
            }
            call.respond(avisos.bandeja(usuario.id, desde))
        }
        post("/leidos") {
            val usuario = call.usuarioActual()
            val s = call.receive<MarcarLeidosSolicitud>()
            if (!s.todos && s.ids.isEmpty()) throw BadRequestException("Indica qué avisos marcar o usa «todos».")
            avisos.marcarLeidos(usuario.id, s.ids, s.todos)
            call.respond(avisos.bandeja(usuario.id))
        }
    }
}
