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
)

/** Forma de los errores que manda el backend. */
@Serializable
data class ErrorRespuesta(
    val mensaje: String,
)

/** Sesión abierta en la app. */
data class Sesion(
    val token: String,
    val usuario: Usuario,
)
