package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.modelos.AsignacionSolicitud
import com.terapid.physiogestion.modelos.EstadoCita
import com.terapid.physiogestion.modelos.FichaPaciente
import com.terapid.physiogestion.modelos.PacienteResumen
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.RutinaSolicitud
import com.terapid.physiogestion.modelos.UsuarioPublico
import com.terapid.physiogestion.plugins.AccesoDenegado
import com.terapid.physiogestion.plugins.exigirRol
import com.terapid.physiogestion.servicios.ClinicaServicio
import com.terapid.physiogestion.servicios.PacienteConocido
import com.terapid.physiogestion.servicios.SesionesServicio
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 *  GET   /pacientes                       FISIOTERAPEUTA (los suyos), RECEPCIONISTA, ADMINISTRADOR (PAC-02)
 *  GET   /pacientes/{id}                  ficha: rutina, sesiones, escaleras y citas (PAC-03)
 *  PATCH /pacientes/{id}/fisioterapeuta   RECEPCIONISTA, ADMINISTRADOR: asignar fisioterapeuta (PAC-04)
 *  GET   /pacientes/{id}/rutina           PACIENTE (solo la suya), FISIOTERAPEUTA (sus pacientes)
 *  PUT   /pacientes/{id}/rutina           FISIOTERAPEUTA: crea o modifica la rutina con el catálogo (RUT-08)
 *  GET   /ejercicios                      catálogo de ejercicios
 *  GET   /fisioterapeutas                 fisioterapeutas activos (para agendar y asignar)
 */
fun Route.rutasRutinas(clinica: ClinicaServicio, sesiones: SesionesServicio) {
    get("/ejercicios") {
        call.exigirRol(Rol.FISIOTERAPEUTA, Rol.ADMINISTRADOR)
        call.respond(clinica.catalogo)
    }

    get("/fisioterapeutas") {
        call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA, Rol.FISIOTERAPEUTA)
        call.respond(clinica.fisioterapeutas())
    }

    route("/pacientes") {
        get {
            val usuario = call.exigirRol(Rol.FISIOTERAPEUTA, Rol.RECEPCIONISTA, Rol.ADMINISTRADOR)
            call.respond(clinica.pacientesVisibles(usuario).map { resumen(it, clinica, sesiones) })
        }

        get("/{id}") {
            val usuario = call.exigirRol(Rol.FISIOTERAPEUTA, Rol.RECEPCIONISTA, Rol.ADMINISTRADOR)
            val id = call.idPaciente()
            if (!clinica.puedeVer(usuario, id)) throw AccesoDenegado("Ese paciente no está en tu seguimiento.")
            val p = clinica.paciente(id)
            val clinico = usuario.rol != Rol.RECEPCIONISTA // recepción no ve los datos de salud
            call.respond(
                FichaPaciente(
                    resumen = resumen(p, clinica, sesiones),
                    rutina = if (clinico) clinica.rutinaDe(id) else null,
                    sesionesRecientes = if (clinico) sesiones.listar(setOf(id), 0, 5) else emptyList(),
                    pruebasEscaleras = if (clinico) sesiones.pruebasEscalerasDe(setOf(id)).take(5) else emptyList(),
                    citas = clinica.proximasDe(id),
                )
            )
        }

        patch("/{id}/fisioterapeuta") {
            call.exigirRol(Rol.RECEPCIONISTA, Rol.ADMINISTRADOR)
            val id = call.idPaciente()
            clinica.asignar(id, call.receive<AsignacionSolicitud>().fisioterapeutaId)
            call.respond(resumen(clinica.paciente(id), clinica, sesiones))
        }

        get("/{id}/rutina") {
            val usuario = call.exigirRol(Rol.PACIENTE, Rol.FISIOTERAPEUTA)
            val id = call.idPaciente()
            call.verificarRutina(usuario, id, clinica)
            val rutina = clinica.rutinaDe(id) ?: throw NotFoundException("Este paciente aún no tiene una rutina asignada.")
            call.respond(rutina)
        }

        put("/{id}/rutina") {
            val usuario = call.exigirRol(Rol.FISIOTERAPEUTA)
            val id = call.idPaciente()
            call.respond(clinica.guardarRutina(id, call.receive<RutinaSolicitud>(), usuario))
        }
    }
}

private fun ApplicationCall.idPaciente(): Int =
    parameters["id"]?.toIntOrNull() ?: throw BadRequestException("El id del paciente debe ser un número.")

/** Un paciente solo ve su rutina; el fisioterapeuta, la de sus pacientes. */
private fun ApplicationCall.verificarRutina(usuario: UsuarioPublico, pacienteId: Int, clinica: ClinicaServicio) {
    if (usuario.rol == Rol.PACIENTE && usuario.pacienteId != pacienteId) throw AccesoDenegado("Solo puedes consultar tu propia rutina.")
    if (usuario.rol == Rol.FISIOTERAPEUTA && pacienteId !in clinica.pacientesDe(usuario.id)) {
        throw AccesoDenegado("Ese paciente no está en tu seguimiento.")
    }
}

/** Fila de la lista: fisioterapeuta, rutina, última sesión, cumplimiento promedio y próxima cita. */
internal fun resumen(p: PacienteConocido, clinica: ClinicaServicio, sesiones: SesionesServicio): PacienteResumen {
    val fisioId = clinica.asignacionDe(p.pacienteId)?.fisioterapeutaId
    val rutina = clinica.rutinaDe(p.pacienteId)
    val recientes = sesiones.listar(setOf(p.pacienteId), 0, 5)
    val proxima = clinica.proximasDe(p.pacienteId).firstOrNull { it.estado == EstadoCita.PENDIENTE || it.estado == EstadoCita.CONFIRMADA }
    return PacienteResumen(
        pacienteId = p.pacienteId,
        nombre = p.nombre,
        correo = p.correo,
        tieneCuenta = p.tieneCuenta,
        fisioterapeutaId = fisioId,
        fisioterapeuta = fisioId?.let { id -> clinica.fisioterapeutas().firstOrNull { it.id == id }?.nombre },
        tieneRutina = rutina != null,
        metaFuncional = rutina?.metaFuncional,
        ultimaSesion = recientes.firstOrNull()?.fechaHoraInicio,
        semaforo = recientes.firstOrNull()?.semaforo,
        cumplimientoPromedio = recientes.takeIf { it.isNotEmpty() }?.map { it.porcentajeCumplimiento }?.average()?.toInt(),
        proximaCita = proxima?.let { "${it.fecha}T${it.hora}" },
    )
}
