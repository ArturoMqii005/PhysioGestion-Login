package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.modelos.EstadoServidor
import com.terapid.physiogestion.modelos.VERSION_API
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.lang.management.ManagementFactory

/**
 * Chequeo de salud. Es público (no pide token) para que lo usen las
 * pruebas, el monitoreo y la verificación de cada despliegue.
 *
 * Railway inyecta como variables de entorno el nombre del entorno, la
 * región y, cuando el despliegue viene de GitHub, el commit desplegado.
 */
fun Route.rutasSalud() {
    get("/salud") {
        call.respond(
            EstadoServidor(
                estado = "OK",
                servicio = "PhysioGestion API",
                version = VERSION_API,
                entorno = System.getenv("RAILWAY_ENVIRONMENT_NAME") ?: "local",
                region = System.getenv("RAILWAY_REPLICA_REGION"),
                commit = System.getenv("RAILWAY_GIT_COMMIT_SHA")?.take(7),
                java = System.getProperty("java.version"),
                segundosActivo = ManagementFactory.getRuntimeMXBean().uptime / 1000,
            )
        )
    }
}
