package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.CuentaApi
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.AltaUsuarioRespuesta
import com.terapid.physiogestion.modelo.AltaUsuarioSolicitud
import com.terapid.physiogestion.modelo.AvisoPrivacidad
import com.terapid.physiogestion.modelo.EstadoCuentaSolicitud
import com.terapid.physiogestion.modelo.Invitacion
import com.terapid.physiogestion.modelo.RegistroRespuesta
import com.terapid.physiogestion.modelo.RegistroSolicitud
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Usuario

/**
 * Ciclo de vida de la cuenta (módulo USR): registro con invitación, correo,
 * contraseña y aviso de privacidad; también invitaciones y alta del personal.
 * Lo que necesita sesión usa el token del [GestorSesion] y, si el servidor
 * lo rechaza, cierra la sesión como el resto de la app.
 */
class CuentaRepositorio(
    private val api: CuentaApi,
    private val gestor: GestorSesion,
) {
    suspend fun registrar(
        codigoInvitacion: String,
        usuario: String,
        correo: String,
        contrasena: String,
        aceptaAviso: Boolean,
    ): RegistroRespuesta = api.registrar(
        RegistroSolicitud(codigoInvitacion.trim(), usuario.trim().lowercase(), correo.trim().lowercase(), contrasena, aceptaAviso)
    )

    suspend fun verificarCorreo(correo: String, codigo: String): String = api.verificarCorreo(correo, codigo).mensaje

    suspend fun reenviarVerificacion(correo: String): String = api.reenviarVerificacion(correo).mensaje

    suspend fun solicitarRecuperacion(correo: String): String = api.solicitarRecuperacion(correo).mensaje

    suspend fun restablecer(correo: String, codigo: String, nueva: String): String = api.restablecer(correo, codigo, nueva).mensaje

    suspend fun avisoPrivacidad(): AvisoPrivacidad = api.avisoPrivacidad()

    /** Acepta el aviso; al quitar la marca, la navegación abre la vista del paciente. */
    suspend fun aceptarAviso(version: String) {
        conSesion { api.aceptarAviso(version, it) }
        gestor.actualizar { it.copy(requiereConsentimiento = false) }
    }

    /** Cambia la contraseña; el servidor cierra las sesiones de los otros dispositivos. */
    suspend fun cambiarContrasena(actual: String, nueva: String): String {
        val mensaje = conSesion { api.cambiarContrasena(actual, nueva, it) }.mensaje
        gestor.actualizar { it.copy(debeCambiarContrasena = false) }
        return mensaje
    }

    suspend fun invitaciones(): List<Invitacion> = conSesion { api.invitaciones(it) }

    suspend fun crearInvitacion(nombrePaciente: String): Invitacion = conSesion { api.crearInvitacion(nombrePaciente, it) }

    suspend fun darDeAlta(usuario: String, nombre: String, rol: Rol, correo: String): AltaUsuarioRespuesta =
        conSesion { api.darDeAlta(AltaUsuarioSolicitud(usuario.trim().lowercase(), nombre.trim(), rol, correo.trim().lowercase()), it) }

    suspend fun cambiarEstado(id: Int, activo: Boolean): Usuario = conSesion { api.editarUsuario(id, EstadoCuentaSolicitud(activo = activo), it) }

    /** Edita nombre, correo o rol (ADM-03). */
    suspend fun editarUsuario(id: Int, nombre: String?, correo: String?, rol: Rol?): Usuario =
        conSesion { api.editarUsuario(id, EstadoCuentaSolicitud(nombre = nombre?.trim(), correo = correo?.trim()?.lowercase(), rol = rol), it) }

    private suspend fun <T> conSesion(llamada: suspend (String) -> T): T =
        try {
            llamada(gestor.token())
        } catch (e: SesionVencida) {
            gestor.cerrar(e.message)
            throw e
        }
}
