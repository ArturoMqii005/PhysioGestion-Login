package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.modelos.CitaSolicitud
import com.terapid.physiogestion.modelos.EstadoCitaSolicitud
import com.terapid.physiogestion.modelos.ReprogramarCitaSolicitud
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
 *  PATCH /citas/{id}                      RECEPCIONISTA, ADMINISTRADOR: reprogramar (CIT-06)
 *  POST  /citas/{id}/estado               confirmar, cancelar, atendida o no asistió según el rol (CIT-06, CIT-07)
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
            call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA)
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
            call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA)
            call.respond(clinica.reprogramar(call.idCita(), call.receive<ReprogramarCitaSolicitud>()))
        }

        post("/{id}/estado") {
            val usuario = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA, Rol.FISIOTERAPEUTA, Rol.PACIENTE)
            val s = call.receive<EstadoCitaSolicitud>()
            call.respond(clinica.cambiarEstado(call.idCita(), s.estado, s.nota, usuario))
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
