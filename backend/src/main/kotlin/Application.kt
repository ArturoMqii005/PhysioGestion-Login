package com.terapid.physiogestion

import com.terapid.physiogestion.datos.AlmacenJson
import com.terapid.physiogestion.plugins.configureHttp
import com.terapid.physiogestion.plugins.configureMonitoreo
import com.terapid.physiogestion.plugins.configureSeguridad
import com.terapid.physiogestion.plugins.configureSerialization
import com.terapid.physiogestion.plugins.configureStatusPages
import com.terapid.physiogestion.rutas.configureRouting
import com.terapid.physiogestion.servicios.AutenticacionServicio
import io.ktor.server.application.Application
import kotlinx.serialization.json.Json

/**
 * Módulo principal del servidor (lo indica application.yaml).
 * Crea los objetos compartidos e instala los plugins en orden.
 */
fun Application.module() {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    val almacen = AlmacenJson(json)
    val autenticacion = AutenticacionServicio(almacen.usuarios)

    configureMonitoreo()
    configureHttp()
    configureSerialization(json)
    configureStatusPages()
    configureSeguridad(autenticacion)
    configureRouting(almacen, autenticacion)
}
