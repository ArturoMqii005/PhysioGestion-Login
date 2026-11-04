package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/** Cuerpo JSON que manda la app en POST /api/v1/auth/login. [usuario] acepta el usuario o el correo. */
@Serializable
data class LoginSolicitud(
    val usuario: String,
    val contrasena: String,
)

/** Respuesta JSON de un inicio de sesión correcto. */
@Serializable
data class LoginRespuesta(
    val token: String,
    val usuario: UsuarioPublico,
    /** Fecha y hora (ISO) en que vence el token si no se usa; cada petición la recorre (USR-14). */
    val expiraEn: String? = null,
    /** true si el paciente todavía no acepta el aviso de privacidad vigente (USR-21). */
    val requiereConsentimiento: Boolean = false,
    /** true si la cuenta tiene una contraseña temporal que hay que cambiar (USR-02). */
    val debeCambiarContrasena: Boolean = false,
)

/**
 * Forma única de todos los errores que devuelve la API. [codigo] es opcional y
 * permite que la app reaccione sin comparar textos (por ejemplo, CORREO_NO_VERIFICADO).
 */
@Serializable
data class ErrorRespuesta(
    val mensaje: String,
    val codigo: String? = null,
    /** Solo en CORREO_NO_VERIFICADO: a qué correo se envió el código (la contraseña ya fue correcta). */
    val correo: String? = null,
)

/** Respuesta de las operaciones que solo informan algo («Te enviamos un código…»). */
@Serializable
data class MensajeRespuesta(val mensaje: String)

// ---------------------------------------------------------------- Registro (USR-01, USR-20)

/** POST /api/v1/auth/registro: el paciente crea su cuenta con el código que le dio la clínica. */
@Serializable
data class RegistroSolicitud(
    val codigoInvitacion: String,
    val usuario: String,
    val correo: String,
    val contrasena: String,
    val aceptaAvisoPrivacidad: Boolean = false,
)

/** Respuesta del registro: a qué correo (enmascarado) se envió el código de verificación. */
@Serializable
data class RegistroRespuesta(
    val mensaje: String,
    val correoEnmascarado: String,
)

/** POST /api/v1/auth/verificar-correo. */
@Serializable
data class VerificarCorreoSolicitud(
    val correo: String,
    val codigo: String,
)

/** Cuerpo con solo el correo: reenviar la verificación o pedir la recuperación. */
@Serializable
data class CorreoSolicitud(val correo: String)

// ---------------------------------------------------------------- Contraseña (USR-10, USR-11, USR-12)

/** POST /api/v1/auth/restablecer: código recibido por correo + contraseña nueva. */
@Serializable
data class RestablecerSolicitud(
    val correo: String,
    val codigo: String,
    val contrasenaNueva: String,
)

/** PATCH /api/v1/usuarios/yo/contrasena. */
@Serializable
data class CambiarContrasenaSolicitud(
    val contrasenaActual: String,
    val contrasenaNueva: String,
)

// ---------------------------------------------------------------- Aviso de privacidad (USR-21)

/** GET /api/v1/aviso-privacidad: texto vigente que el paciente acepta. */
@Serializable
data class AvisoPrivacidad(
    val version: String,
    val titulo: String,
    val secciones: List<SeccionAviso>,
)

@Serializable
data class SeccionAviso(val titulo: String, val texto: String)

/** POST /api/v1/usuarios/yo/consentimiento. */
@Serializable
data class ConsentimientoSolicitud(val version: String)

// ---------------------------------------------------------------- Invitaciones y alta de personal

/** Código que recepción entrega a un paciente nuevo para que cree su cuenta. */
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

/** POST /api/v1/invitaciones. */
@Serializable
data class InvitacionSolicitud(val nombrePaciente: String)

/** POST /api/v1/usuarios (ADMINISTRADOR): alta de fisioterapeutas, recepcionistas y administradores. */
@Serializable
data class AltaUsuarioSolicitud(
    val usuario: String,
    val nombre: String,
    val rol: Rol,
    val correo: String,
)

/** La contraseña temporal solo se muestra una vez; el usuario debe cambiarla al entrar. */
@Serializable
data class AltaUsuarioRespuesta(
    val usuario: UsuarioPublico,
    val contrasenaTemporal: String,
)

/** PATCH /api/v1/usuarios/{id} (ADMINISTRADOR): activar o desactivar una cuenta y editar sus datos (ADM-03). */
@Serializable
data class EstadoCuentaSolicitud(
    val activo: Boolean? = null,
    val nombre: String? = null,
    val correo: String? = null,
    val rol: Rol? = null,
)

/** Usuario con los datos que necesita el panel de administración. */
@Serializable
data class UsuarioAdministrado(
    val id: Int,
    val usuario: String,
    val nombre: String,
    val rol: Rol,
    val pacienteId: Int? = null,
    val correo: String? = null,
    val activo: Boolean = true,
    val correoVerificado: Boolean = true,
)

/** GET /api/v1/usuarios/yo: datos de la cuenta y lo que la app debe pedir antes de continuar. */
@Serializable
data class EstadoCuenta(
    val usuario: UsuarioPublico,
    val requiereConsentimiento: Boolean,
    val debeCambiarContrasena: Boolean,
)
