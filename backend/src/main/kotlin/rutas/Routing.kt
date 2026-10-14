package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.datos.AlmacenJson
import com.terapid.physiogestion.plugins.AUTH_SESION
import com.terapid.physiogestion.servicios.AutenticacionServicio
import com.terapid.physiogestion.servicios.SesionesServicio
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Mapa de la API. Todas las rutas llevan la versión (/api/v1) para que la
 * app instalada siga funcionando cuando cambie la API (RI-02).
 *
 *  POST /api/v1/auth/login                 público
 *  POST /api/v1/auth/logout                con token
 *  GET  /api/v1/usuarios                   ADMINISTRADOR
 *  GET  /api/v1/citas                      ADMINISTRADOR, RECEPCIONISTA, FISIOTERAPEUTA (solo las suyas)
 *  GET  /api/v1/pacientes/{id}/rutina      PACIENTE (solo la suya), FISIOTERAPEUTA
 *
 *  GET    /api/v1/sesiones?idPaciente=     PACIENTE (las suyas), FISIOTERAPEUTA (sus pacientes)
 *  GET    /api/v1/sesiones/{id}            PACIENTE, FISIOTERAPEUTA
 *  POST   /api/v1/sesiones                 PACIENTE -> 201 Created + Location
 *  PUT    /api/v1/sesiones/{id}            PACIENTE (reemplaza los datos editables)
 *  PATCH  /api/v1/sesiones/{id}            PACIENTE (modifica solo lo que se manda)
 *  DELETE /api/v1/sesiones/{id}            PACIENTE -> 204 No Content
 *  POST   /api/v1/resultados-ejercicio     PACIENTE (resumen medido con sensores)
 *  GET    /api/v1/resultados-ejercicio     PACIENTE, FISIOTERAPEUTA
 */
fun Application.configureRouting(
    almacen: AlmacenJson,
    autenticacion: AutenticacionServicio,
    sesiones: SesionesServicio,
) {
    routing {
        get("/") {
            call.respondText("PhysioGestion API v1 en funcionamiento")
        }
        route("/api/v1") {
            rutasAutenticacion(autenticacion)
            authenticate(AUTH_SESION) {
                rutasUsuarios(almacen)
                rutasCitas(almacen)
                rutasRutinas(almacen)
                rutasSesiones(sesiones)
            }
        }
    }
}
