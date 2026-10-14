package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.modelos.CrearSesionSolicitud
import com.terapid.physiogestion.modelos.ModificarSesionSolicitud
import com.terapid.physiogestion.modelos.ReemplazarSesionSolicitud
import com.terapid.physiogestion.modelos.ResultadoEjercicioSolicitud
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.SesionTerapia
import com.terapid.physiogestion.modelos.UsuarioPublico
import com.terapid.physiogestion.plugins.AccesoDenegado
import com.terapid.physiogestion.plugins.exigirRol
import com.terapid.physiogestion.servicios.SesionesServicio
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** Encabezado con el que la app evita duplicar una sesión al reintentar un POST. */
const val ENCABEZADO_IDEMPOTENCIA = "Idempotency-Key"

/**
 * Recurso /sesiones: los cinco métodos HTTP sobre el mismo recurso.
 *
 *  GET    /sesiones?idPaciente=1   historial paginado         (seguro e idempotente)
 *  GET    /sesiones/{id}           una sesión                 (seguro e idempotente)
 *  POST   /sesiones                registrar sesión -> 201    (NO idempotente, salvo con Idempotency-Key)
 *  PUT    /sesiones/{id}           reemplazar datos editables (idempotente)
 *  PATCH  /sesiones/{id}           modificar solo algunos     (parcial)
 *  DELETE /sesiones/{id}           eliminar -> 204            (idempotente: la segunda vez responde 404)
 */
fun Route.rutasSesiones(servicio: SesionesServicio) {
    route("/sesiones") {

        get {
            val usuario = call.exigirRol(Rol.PACIENTE, Rol.FISIOTERAPEUTA)
            val pacientes = call.pacientesVisibles(usuario, servicio)
            val pagina = call.parametroEntero("pagina", 0).coerceAtLeast(0)
            val tamano = call.parametroEntero("tamano", 20).coerceIn(1, 50)
            call.response.header("X-Total-Count", servicio.contar(pacientes).toString())
            call.respond(servicio.listar(pacientes, pagina, tamano))
        }

        get("/{id}") {
            val usuario = call.exigirRol(Rol.PACIENTE, Rol.FISIOTERAPEUTA)
            call.respond(call.sesionVisible(usuario, servicio))
        }

        post {
            val usuario = call.exigirRol(Rol.PACIENTE)
            val pacienteId = usuario.pacienteId ?: throw AccesoDenegado("Tu cuenta no está ligada a un paciente.")
            val solicitud = call.receive<CrearSesionSolicitud>()
            val creacion = servicio.crear(pacienteId, solicitud, call.request.headers[ENCABEZADO_IDEMPOTENCIA])
            // Location indica la dirección del recurso recién creado.
            call.response.header(HttpHeaders.Location, "/api/v1/sesiones/${creacion.sesion.id}")
            if (creacion.nueva) {
                call.respond(HttpStatusCode.Created, creacion.sesion)
            } else {
                call.response.header("Idempotent-Replayed", "true")
                call.respond(HttpStatusCode.OK, creacion.sesion)
            }
        }

        put("/{id}") {
            val usuario = call.exigirRol(Rol.PACIENTE)
            val actual = call.sesionPropia(usuario, servicio)
            call.respond(servicio.reemplazar(actual, call.receive<ReemplazarSesionSolicitud>()))
        }

        patch("/{id}") {
            val usuario = call.exigirRol(Rol.PACIENTE)
            val actual = call.sesionPropia(usuario, servicio)
            call.respond(servicio.modificar(actual, call.receive<ModificarSesionSolicitud>()))
        }

        delete("/{id}") {
            val usuario = call.exigirRol(Rol.PACIENTE)
            val actual = call.sesionPropia(usuario, servicio)
            servicio.eliminar(actual.id)
            call.respond(HttpStatusCode.NoContent) // 204: correcto y sin cuerpo
        }
    }

    // Resultados medidos con el acelerómetro y el giroscopio (RFN-26).
    route("/resultados-ejercicio") {
        post {
            val usuario = call.exigirRol(Rol.PACIENTE)
            val pacienteId = usuario.pacienteId ?: throw AccesoDenegado("Tu cuenta no está ligada a un paciente.")
            val resultado = servicio.registrarResultado(pacienteId, call.receive<ResultadoEjercicioSolicitud>())
            call.respond(HttpStatusCode.Created, resultado)
        }
        get {
            val usuario = call.exigirRol(Rol.PACIENTE, Rol.FISIOTERAPEUTA)
            call.respond(servicio.resultadosDe(call.pacientesVisibles(usuario, servicio)))
        }
    }
}

/**
 * Pacientes que puede consultar el usuario: el paciente solo a sí mismo y el
 * fisioterapeuta a los pacientes cuya rutina él asignó.
 */
private fun ApplicationCall.pacientesVisibles(usuario: UsuarioPublico, servicio: SesionesServicio): Set<Int> {
    val pedido = request.queryParameters["idPaciente"]?.let {
        it.toIntOrNull() ?: throw BadRequestException("idPaciente debe ser un número.")
    }
    return if (usuario.rol == Rol.PACIENTE) {
        val propio = usuario.pacienteId ?: throw AccesoDenegado("Tu cuenta no está ligada a un paciente.")
        if (pedido != null && pedido != propio) throw AccesoDenegado("Solo puedes consultar tus propias sesiones.")
        setOf(propio)
    } else {
        val suyos = servicio.pacientesDe(usuario.nombre)
        if (pedido == null) suyos
        else if (pedido in suyos) setOf(pedido)
        else throw AccesoDenegado("Ese paciente no está en tu seguimiento.")
    }
}

private fun ApplicationCall.idDeRuta(): Int =
    parameters["id"]?.toIntOrNull() ?: throw BadRequestException("El id de la sesión debe ser un número.")

private fun ApplicationCall.parametroEntero(nombre: String, porDefecto: Int): Int =
    request.queryParameters[nombre]?.let {
        it.toIntOrNull() ?: throw BadRequestException("$nombre debe ser un número.")
    } ?: porDefecto

/** Sesión que el usuario puede ver (404 si no existe, 403 si es de otro paciente). */
private fun ApplicationCall.sesionVisible(usuario: UsuarioPublico, servicio: SesionesServicio): SesionTerapia {
    val id = idDeRuta()
    val sesion = servicio.obtener(id) ?: throw NotFoundException("La sesión $id no existe o ya fue eliminada.")
    if (sesion.pacienteId !in pacientesVisibles(usuario, servicio)) {
        throw AccesoDenegado("No puedes consultar esta sesión.")
    }
    return sesion
}

/** Sesión que el paciente puede modificar o eliminar: solo las suyas. */
private fun ApplicationCall.sesionPropia(usuario: UsuarioPublico, servicio: SesionesServicio): SesionTerapia {
    val id = idDeRuta()
    val sesion = servicio.obtener(id) ?: throw NotFoundException("La sesión $id no existe o ya fue eliminada.")
    if (sesion.pacienteId != usuario.pacienteId) throw AccesoDenegado("Solo puedes modificar tus propias sesiones.")
    return sesion
}
