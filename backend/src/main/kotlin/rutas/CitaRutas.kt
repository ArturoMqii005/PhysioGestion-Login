package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.modelos.CitaSolicitud
import com.terapid.physiogestion.modelos.EstadoCitaSolicitud
import com.terapid.physiogestion.modelos.EstadoSolicitud
import com.terapid.physiogestion.modelos.NuevaSolicitudReagenda
import com.terapid.physiogestion.modelos.ReagendarSolicitud
import com.terapid.physiogestion.modelos.ReprogramarCitaSolicitud
import com.terapid.physiogestion.modelos.ResolverSolicitud
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.plugins.AccesoDenegado
import com.terapid.physiogestion.plugins.exigirRol
import com.terapid.physiogestion.servicios.ClinicaServicio
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 *  GET   /citas?fecha=2026-11-04          agenda del día (hoy si no se indica). El fisioterapeuta, solo las suyas;
 *                                         el paciente recibe sus próximas citas (CIT-08)
 *  GET   /citas/libres?fisioterapeutaId=&fecha=&duracion=   horas de inicio disponibles
 *  POST  /citas                           RECEPCIONISTA, ADMINISTRADOR: agendar -> 201 (CIT-05); choque -> 409
 *  PATCH /citas/{id}                      RECEPCIONISTA, ADMINISTRADOR: reprogramar (CIT-06);
 *                                         FISIOTERAPEUTA: sus citas, cuando no puede atender (con nota)
 *  POST  /citas/{id}/estado               confirmar, cancelar, atendida o no asistió según el rol (CIT-06, CIT-07)
 *  GET   /citas/{id}/libres?fecha=        horas libres para mover esa cita (el paciente, solo con su fisioterapeuta)
 *  GET   /citas/{id}/disponibles          fisioterapeutas libres a la misma hora (para pasarla)
 *  POST  /citas/{id}/reagendar            PACIENTE: la mueve él mismo (más de 24 h antes, máximo 2 veces)
 *  POST  /citas/{id}/solicitudes          PACIENTE: pide el cambio a recepción -> 201
 *  GET   /solicitudes-reagenda?estado=    solicitudes visibles para el usuario
 *  POST  /solicitudes-reagenda/{id}/resolver   RECEPCIONISTA, ADMINISTRADOR: aprobar o rechazar
 */
fun Route.rutasCitas(clinica: ClinicaServicio) {
    route("/citas") {
        get {
            val usuario = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA, Rol.FISIOTERAPEUTA, Rol.PACIENTE)
            if (usuario.rol == Rol.PACIENTE) {
                val propio = usuario.pacienteId ?: throw AccesoDenegado("Tu cuenta no está ligada a un paciente.")
                call.respond(clinica.proximasDe(propio))
                return@get
            }
            call.respond(clinica.agenda(usuario, call.fecha("fecha") ?: clinica.hoy()))
        }

        get("/libres") {
            call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA, Rol.FISIOTERAPEUTA)
            val fisio = call.request.queryParameters["fisioterapeutaId"]?.toIntOrNull()
                ?: throw BadRequestException("Falta fisioterapeutaId.")
            val fecha = call.fecha("fecha") ?: clinica.hoy()
            val duracion = call.request.queryParameters["duracion"]?.toIntOrNull() ?: 50
            val excepto = call.request.queryParameters["excepto"]?.toIntOrNull()
            call.respond(clinica.horasLibres(fisio, fecha, duracion, excepto))
        }

        post {
            call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA)
            val cita = clinica.agendar(call.receive<CitaSolicitud>())
            call.response.header("Location", "/api/v1/citas/${cita.id}")
            call.respond(HttpStatusCode.Created, cita)
        }

        patch("/{id}") {
            val usuario = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA, Rol.FISIOTERAPEUTA)
            call.respond(clinica.reprogramar(call.idCita(), call.receive<ReprogramarCitaSolicitud>(), usuario))
        }

        get("/{id}/libres") {
            val usuario = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA, Rol.FISIOTERAPEUTA, Rol.PACIENTE)
            val fecha = call.fecha("fecha") ?: clinica.hoy()
            val fisio = call.request.queryParameters["fisioterapeutaId"]?.toIntOrNull()
            call.respond(clinica.horasLibresParaCita(call.idCita(), fecha, usuario, fisio))
        }

        get("/{id}/disponibles") {
            val usuario = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA, Rol.FISIOTERAPEUTA)
            call.respond(clinica.fisiosDisponibles(call.idCita(), usuario))
        }

        post("/{id}/reagendar") {
            val usuario = call.exigirRol(Rol.PACIENTE)
            call.respond(clinica.reagendarPorPaciente(call.idCita(), call.receive<ReagendarSolicitud>(), usuario))
        }

        post("/{id}/solicitudes") {
            val usuario = call.exigirRol(Rol.PACIENTE)
            call.respond(HttpStatusCode.Created, clinica.solicitarReagenda(call.idCita(), call.receive<NuevaSolicitudReagenda>(), usuario))
        }

        post("/{id}/estado") {
            val usuario = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA, Rol.FISIOTERAPEUTA, Rol.PACIENTE)
            val s = call.receive<EstadoCitaSolicitud>()
            call.respond(clinica.cambiarEstado(call.idCita(), s.estado, s.nota, usuario))
        }
    }

    route("/solicitudes-reagenda") {
        get {
            val usuario = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA, Rol.FISIOTERAPEUTA, Rol.PACIENTE)
            val estado = call.request.queryParameters["estado"]?.let {
                runCatching { EstadoSolicitud.valueOf(it.uppercase()) }.getOrElse { throw BadRequestException("Estado no válido: usa PENDIENTE, APROBADA o RECHAZADA.") }
            }
            call.respond(clinica.solicitudes(usuario, estado))
        }

        post("/{id}/resolver") {
            val usuario = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA)
            val id = call.parameters["id"]?.toIntOrNull() ?: throw BadRequestException("El id de la solicitud debe ser un número.")
            call.respond(clinica.resolverSolicitud(id, call.receive<ResolverSolicitud>(), usuario))
        }
    }
}

private fun ApplicationCall.idCita(): Int =
    parameters["id"]?.toIntOrNull() ?: throw BadRequestException("El id de la cita debe ser un número.")

private fun ApplicationCall.fecha(nombre: String): LocalDate? = request.queryParameters[nombre]?.let {
    try {
        LocalDate.parse(it)
    } catch (_: DateTimeParseException) {
        throw BadRequestException("La fecha debe tener el formato 2026-11-04.")
    }
}
