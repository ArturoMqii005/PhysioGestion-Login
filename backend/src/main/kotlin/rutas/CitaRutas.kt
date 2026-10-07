package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.datos.AlmacenJson
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.plugins.exigirRol
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.rutasCitas(almacen: AlmacenJson) {
    // Agenda del día. El fisioterapeuta solo recibe sus propias citas.
    get("/citas") {
        val usuario = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA, Rol.FISIOTERAPEUTA)
        val citas = if (usuario.rol == Rol.FISIOTERAPEUTA) {
            almacen.citas.filter { it.fisioterapeutaId == usuario.id }
        } else {
            almacen.citas
        }
        call.respond(citas.sortedBy { it.hora })
    }
}
