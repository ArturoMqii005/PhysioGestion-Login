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
 *  GET  /api/v1/salud                      público (estado, versión y commit desplegado)
 *  GET  /api/v1/aviso-privacidad          público
 *  POST /api/v1/auth/login                 público (también registro, verificar-correo,
 *                                          reenviar-verificacion, recuperacion y restablecer)
 *  POST /api/v1/auth/logout                con token
 *  GET  /api/v1/usuarios                   ADMINISTRADOR (también POST alta y PATCH activar/desactivar)
 *  GET  /api/v1/usuarios/yo                con token (PATCH /contrasena, POST /consentimiento)
 *  GET  /api/v1/invitaciones               ADMINISTRADOR, RECEPCIONISTA (POST crea una)
 *  GET  /api/v1/citas                      ADMINISTRADOR, RECEPCIONISTA, FISIOTERAPEUTA (solo las suyas)
 *  GET  /api/v1/pacientes/{id}/rutina      PACIENTE (solo la suya), FISIOTERAPEUTA
 *
 *  GET    /api/v1/sesiones?idPaciente=     PACIENTE (las suyas), FISIOTERAPEUTA (sus pacientes)
 *  GET    /api/v1/sesiones/{id}            PACIENTE, FISIOTERAPEUTA
 *  POST   /api/v1/sesiones                 PACIENTE -> 201 Created + Location
 *  PUT    /api/v1/sesiones/{id}            PACIENTE (reemplaza los datos editables)
 *  PATCH  /api/v1/sesiones/{id}            PACIENTE (modifica solo lo que se manda)
 *  DELETE /api/v1/sesiones/{id}            PACIENTE -> 204 No Content
 *  POST   /api/v1/resultados-ejercicio     PACIENTE (resumen medido con sensores o con cámara)
 *  GET    /api/v1/resultados-ejercicio     PACIENTE, FISIOTERAPEUTA
 *  POST   /api/v1/pruebas-escaleras        PACIENTE (barómetro + contador de pasos)
 *  GET    /api/v1/pruebas-escaleras        PACIENTE (las suyas), FISIOTERAPEUTA (sus pacientes)
 */
fun Application.configureRouting(
    almacen: AlmacenJson,
    autenticacion: AutenticacionServicio,
    sesiones: SesionesServicio,
    baseDatos: String = "memoria",
) {
    routing {
        get("/") {
            call.respondText("PhysioGestion API v1 en funcionamiento")
        }
        route("/api/v1") {
            rutasSalud(baseDatos)
            rutasAutenticacion(autenticacion)
            authenticate(AUTH_SESION) {
                rutasUsuarios(autenticacion)
                rutasCitas(almacen)
                rutasRutinas(almacen)
                rutasSesiones(sesiones)
            }
        }
    }
}
