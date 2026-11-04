package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/*
 * DTOs de las cuentas: registro, verificación del correo, recuperación y
 * restablecimiento de la contraseña, aviso de privacidad, invitaciones y alta
 * de personal. Son copia de backend/src/main/kotlin/modelos/Autenticacion.kt.
 */

@Serializable
data class RegistroSolicitud(
    val codigoInvitacion: String,
    val usuario: String,
    val correo: String,
    val contrasena: String,
    val aceptaAvisoPrivacidad: Boolean,
)

@Serializable
data class RegistroRespuesta(val mensaje: String, val correoEnmascarado: String)

@Serializable
data class VerificarCorreoSolicitud(val correo: String, val codigo: String)

@Serializable
data class CorreoSolicitud(val correo: String)

@Serializable
data class RestablecerSolicitud(val correo: String, val codigo: String, val contrasenaNueva: String)

@Serializable
data class CambiarContrasenaSolicitud(val contrasenaActual: String, val contrasenaNueva: String)

@Serializable
data class MensajeRespuesta(val mensaje: String)

@Serializable
data class SeccionAviso(val titulo: String, val texto: String)

@Serializable
data class AvisoPrivacidad(val version: String, val titulo: String, val secciones: List<SeccionAviso>)

@Serializable
data class ConsentimientoSolicitud(val version: String)

/** Código que recepción entrega al paciente nuevo para que cree su cuenta. */
@Serializable
data class Invitacion(
    val codigo: String,
    val pacienteId: Int,
    val nombrePaciente: String,
    val creadaPor: String,
    val creadaEn: String,
    val venceEn: String,
    val usada: Boolean = false,
)

@Serializable
data class InvitacionSolicitud(val nombrePaciente: String)

@Serializable
data class AltaUsuarioSolicitud(val usuario: String, val nombre: String, val rol: Rol, val correo: String)

@Serializable
data class AltaUsuarioRespuesta(val usuario: Usuario, val contrasenaTemporal: String)

/** PATCH /api/v1/usuarios/{id}: solo viaja lo que cambia (activar, nombre, correo o rol). */
@Serializable
data class EstadoCuentaSolicitud(
    val activo: Boolean? = null,
    val nombre: String? = null,
    val correo: String? = null,
    val rol: Rol? = null,
)
