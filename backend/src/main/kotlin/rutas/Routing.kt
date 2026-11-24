package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.datos.AlmacenJson
import com.terapid.physiogestion.plugins.AUTH_SESION
import com.terapid.physiogestion.servicios.AutenticacionServicio
import com.terapid.physiogestion.servicios.AnunciosServicio
import com.terapid.physiogestion.servicios.AvisosServicio
import com.terapid.physiogestion.servicios.ClinicaServicio
import com.terapid.physiogestion.servicios.PushServicio
import com.terapid.physiogestion.servicios.RevisionDiariaServicio
import com.terapid.physiogestion.servicios.SesionesServicio
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Mapa de la API. Todas las rutas llevan la versión (/api/v1) para que la
 * app instalada siga funcionando cuando cambie la API (RI-02).
 *
 *  GET  /web/                              versión web para el personal (login y panel)
 *  GET  /api/v1/salud                      público (estado, versión y commit desplegado)
 *  GET  /api/v1/aviso-privacidad          público
 *  POST /api/v1/auth/login                 público (también registro, verificar-correo,
 *                                          reenviar-verificacion, recuperacion y restablecer)
 *  POST /api/v1/auth/logout                con token
 *  GET  /api/v1/usuarios                   ADMINISTRADOR (también POST alta y PATCH activar/desactivar)
 *  GET  /api/v1/usuarios/yo                con token (PATCH /contrasena, POST /consentimiento)
 *  GET  /api/v1/invitaciones               ADMINISTRADOR, RECEPCIONISTA (POST crea una)
 *  GET  /api/v1/citas?fecha=               agenda (también POST agendar, PATCH reprogramar, POST {id}/estado, GET libres)
 *  GET  /api/v1/pacientes                  lista y ficha de pacientes, asignación de fisioterapeuta
 *  GET  /api/v1/pacientes/{id}/rutina      PACIENTE (solo la suya), FISIOTERAPEUTA (también PUT para modificarla)
 *  GET  /api/v1/ejercicios                 catálogo · GET /api/v1/fisioterapeutas
 *  GET  /api/v1/avisos?desde=              con token: avisos del usuario (POST /avisos/leidos los marca)
 *  POST /api/v1/dispositivos               con token: registra el teléfono para push (DELETE /{token} al salir)
 *  POST /api/v1/anuncios                   ADMINISTRADOR, RECEPCIONISTA: anuncio por tema de Firebase (GET los lista)
 *  POST /api/v1/tareas/revision-diaria     GitHub Actions con X-Tarea-Clave: pacientes sin actividad
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
    clinica: ClinicaServicio,
    avisos: AvisosServicio,
    push: PushServicio,
    anuncios: AnunciosServicio,
    revision: RevisionDiariaServicio,
    claveTareas: String?,
    baseDatos: String = "memoria",
) {
    routing {
        get("/") {
            call.respondText("PhysioGestion API v1 en funcionamiento")
        }
        // Versión web: la app de Compose compilada a WebAssembly (frontend/shared/src/webMain).
        // La tarea :shared:publicarWeb del frontend la copia a resources/web. Se sirve desde
        // este mismo servidor, así que usa la API sin CORS.
        get("/web") { call.respondRedirect("/web/") }
        staticResources("/web", "web") {
            // Sin caché: después de cada despliegue el navegador toma la versión nueva.
            cacheControl { listOf(CacheControl.NoCache(null)) }
            // El navegador solo ejecuta el WebAssembly si llega con este tipo.
            contentType { url -> if (url.path.endsWith(".wasm")) ContentType("application", "wasm") else null }
        }
        route("/api/v1") {
            rutasSalud(baseDatos, push.configurado)
            rutasAutenticacion(autenticacion)
            rutasTareas(revision, claveTareas)
            authenticate(AUTH_SESION) {
                rutasUsuarios(autenticacion, clinica)
                rutasCitas(clinica)
                rutasRutinas(clinica, sesiones)
                rutasSesiones(sesiones, clinica)
                rutasAvisos(avisos)
                rutasNotificaciones(push, anuncios)
            }
        }
    }
}
