package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.datos.AlmacenJson
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.plugins.AccesoDenegado
import com.terapid.physiogestion.plugins.exigirRol
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.rutasRutinas(almacen: AlmacenJson) {
    // Rutina vigente de un paciente. Un paciente solo puede ver la suya.
    get("/pacientes/{id}/rutina") {
        val usuario = call.exigirRol(Rol.PACIENTE, Rol.FISIOTERAPEUTA)
        val pacienteId = call.parameters["id"]?.toIntOrNull()
            ?: throw BadRequestException("El id del paciente debe ser un número.")
        if (usuario.rol == Rol.PACIENTE && usuario.pacienteId != pacienteId) {
            throw AccesoDenegado("Solo puedes consultar tu propia rutina.")
        }
        val rutina = almacen.rutinas.firstOrNull { it.pacienteId == pacienteId }
            ?: throw NotFoundException("Este paciente aún no tiene una rutina asignada.")
        call.respond(rutina)
    }
}
