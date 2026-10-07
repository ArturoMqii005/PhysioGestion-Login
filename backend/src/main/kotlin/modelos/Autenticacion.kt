package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/** Cuerpo JSON que manda la app en POST /api/v1/auth/login. */
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
)

/** Forma única de todos los errores que devuelve la API. */
@Serializable
data class ErrorRespuesta(
    val mensaje: String,
)
