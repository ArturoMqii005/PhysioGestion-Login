package com.terapid.physiogestion.rutas

import com.terapid.physiogestion.modelos.AltaUsuarioSolicitud
import com.terapid.physiogestion.modelos.CambiarContrasenaSolicitud
import com.terapid.physiogestion.modelos.ConsentimientoSolicitud
import com.terapid.physiogestion.modelos.EstadoCuenta
import com.terapid.physiogestion.modelos.EstadoCuentaSolicitud
import com.terapid.physiogestion.modelos.InvitacionSolicitud
import com.terapid.physiogestion.modelos.MensajeRespuesta
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.plugins.exigirRol
import com.terapid.physiogestion.plugins.usuarioActual
import com.terapid.physiogestion.plugins.Conflicto
import com.terapid.physiogestion.servicios.AutenticacionServicio
import com.terapid.physiogestion.servicios.ClinicaServicio
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.authorization
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 *  GET   /usuarios                       ADMINISTRADOR (sin contraseñas)
 *  POST  /usuarios                       ADMINISTRADOR: alta del personal con contraseña temporal (USR-02)
 *  PATCH /usuarios/{id}                  ADMINISTRADOR: activar, desactivar y editar nombre, correo o rol (ADM-03)
 *  GET   /usuarios/yo                    cualquiera: su cuenta y lo que falta (aviso, contraseña)
 *  PATCH /usuarios/yo/contrasena         cualquiera: cambio de contraseña (USR-12)
 *  POST  /usuarios/yo/consentimiento     paciente: acepta el aviso de privacidad (USR-21)
 *  GET   /invitaciones                   ADMINISTRADOR, RECEPCIONISTA
 *  POST  /invitaciones                   ADMINISTRADOR, RECEPCIONISTA: código para un paciente nuevo
 */
fun Route.rutasUsuarios(autenticacion: AutenticacionServicio, clinica: ClinicaServicio) {
    route("/usuarios") {
        get {
            call.exigirRol(Rol.ADMINISTRADOR)
            call.respond(autenticacion.listar())
        }
        post {
            call.exigirRol(Rol.ADMINISTRADOR)
            call.respond(HttpStatusCode.Created, autenticacion.darDeAlta(call.receive<AltaUsuarioSolicitud>()))
        }
        patch("/{id}") {
            val admin = call.exigirRol(Rol.ADMINISTRADOR)
            val id = call.parameters["id"]?.toIntOrNull() ?: throw BadRequestException("El id debe ser un número.")
            val s = call.receive<EstadoCuentaSolicitud>()
            val antes = autenticacion.usuario(id)
            // Un fisioterapeuta con pacientes o citas pendientes no puede dejar de serlo sin reasignarlos.
            if (antes?.rol == Rol.FISIOTERAPEUTA && s.rol != null && s.rol != Rol.FISIOTERAPEUTA && clinica.tieneCompromisos(id)) {
                throw Conflicto("Primero reasigna sus pacientes y sus citas pendientes a otro fisioterapeuta.")
            }
            if (antes?.rol == Rol.FISIOTERAPEUTA && s.activo == false && clinica.tieneCompromisos(id)) {
                throw Conflicto("Ese fisioterapeuta tiene pacientes o citas pendientes. Reasígnalos antes de desactivar su cuenta.")
            }
            val editado = autenticacion.editar(id, s, admin.id)
            if (antes != null && antes.rol == Rol.FISIOTERAPEUTA && editado.nombre != antes.nombre) {
                clinica.renombrarFisioterapeuta(id, editado.nombre)
            }
            call.respond(editado)
        }

        route("/yo") {
            get {
                val u = autenticacion.perfil(call.usuarioActual().id)
                call.respond(EstadoCuenta(u.aPublico(), autenticacion.requiereConsentimiento(u), u.debeCambiarContrasena))
            }
            patch("/contrasena") {
                val usuario = call.usuarioActual()
                val s = call.receive<CambiarContrasenaSolicitud>()
                val token = call.request.authorization()?.removePrefix("Bearer ").orEmpty()
                call.respond(MensajeRespuesta(autenticacion.cambiarContrasena(usuario.id, token, s.contrasenaActual, s.contrasenaNueva)))
            }
            post("/consentimiento") {
                val usuario = call.exigirRol(Rol.PACIENTE)
                autenticacion.aceptarAviso(usuario.id, call.receive<ConsentimientoSolicitud>().version)
                call.respond(MensajeRespuesta("Gracias. Guardamos tu aceptación del aviso de privacidad."))
            }
        }
    }

    route("/invitaciones") {
        get {
            call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA)
            call.respond(autenticacion.invitaciones())
        }
        post {
            val quien = call.exigirRol(Rol.ADMINISTRADOR, Rol.RECEPCIONISTA)
            val s = call.receive<InvitacionSolicitud>()
            call.respond(HttpStatusCode.Created, autenticacion.crearInvitacion(s.nombrePaciente, quien.nombre))
        }
    }
}
