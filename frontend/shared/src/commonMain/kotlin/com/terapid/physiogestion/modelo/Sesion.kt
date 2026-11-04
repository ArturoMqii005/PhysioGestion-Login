package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/*
 * DTOs de autenticación. Son copia de los del backend (backend/src/main/kotlin/modelos):
 * mismos nombres de campo, porque el JSON se arma y se lee con esos nombres.
 */

/** Perfiles de trabajo. Cada uno tiene su propia vista en la app. */
@Serializable
enum class Rol { ADMINISTRADOR, FISIOTERAPEUTA, RECEPCIONISTA, PACIENTE }

/** Usuario que devuelve el servidor (nunca incluye la contraseña). */
@Serializable
data class Usuario(
    val id: Int,
    val usuario: String,
    val nombre: String,
    val rol: Rol,
    val pacienteId: Int? = null,
    val correo: String? = null,
    /** Solo en la lista del administrador: false si la cuenta está desactivada. */
    val activo: Boolean = true,
    /** Solo en la lista del administrador: false si el paciente no ha confirmado su correo. */
    val correoVerificado: Boolean = true,
)

/** Cuerpo de POST /api/v1/auth/login. */
@Serializable
data class LoginSolicitud(
    val usuario: String,
    val contrasena: String,
)

/** Respuesta del login: token para las siguientes peticiones y datos del usuario. */
@Serializable
data class LoginRespuesta(
    val token: String,
    val usuario: Usuario,
    /** Cuándo vence el token si no se usa (cada petición lo renueva). */
    val expiraEn: String? = null,
    /** El paciente todavía no acepta el aviso de privacidad vigente (USR-21). */
    val requiereConsentimiento: Boolean = false,
    /** La cuenta tiene una contraseña temporal que hay que cambiar (USR-02). */
    val debeCambiarContrasena: Boolean = false,
)

/** Forma de los errores que manda el backend. [codigo] distingue casos sin comparar textos. */
@Serializable
data class ErrorRespuesta(
    val mensaje: String,
    val codigo: String? = null,
    val correo: String? = null,
)

/**
 * Sesión abierta en la app. Si [requiereConsentimiento] o [debeCambiarContrasena]
 * son true, la navegación pide eso antes de abrir la vista del rol.
 */
data class Sesion(
    val token: String,
    val usuario: Usuario,
    val requiereConsentimiento: Boolean = false,
    val debeCambiarContrasena: Boolean = false,
) {
    /** true cuando ya no falta nada y se puede abrir la vista del rol. */
    val lista: Boolean get() = !requiereConsentimiento && !debeCambiarContrasena
}
