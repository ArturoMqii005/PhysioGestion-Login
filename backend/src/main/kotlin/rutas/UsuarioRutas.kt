package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.datos.AlmacenJson
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.plugins.exigirRol
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.rutasUsuarios(almacen: AlmacenJson) {
    // Lista de usuarios del sistema, sin contraseñas. Solo para el administrador.
    get("/usuarios") {
        call.exigirRol(Rol.ADMINISTRADOR)
        call.respond(almacen.usuarios.map { it.aPublico() })
    }
}
