package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/** Perfiles de trabajo de PhysioGestion. Cada uno abre una vista distinta en la app. */
@Serializable
enum class Rol { ADMINISTRADOR, FISIOTERAPEUTA, RECEPCIONISTA, PACIENTE }

/**
 * Usuario tal como se guarda en el servidor (usuarios.json o la base de datos).
 * Solo guarda el HASH BCrypt de la contraseña, nunca la contraseña (USR-13),
 * y por eso tampoco se envía al cliente.
 */
@Serializable
data class UsuarioRegistrado(
    val id: Int,
    val usuario: String,
    val contrasenaHash: String,
    val nombre: String,
    val rol: Rol,
    val pacienteId: Int? = null,
    val correo: String? = null,
    /** false mientras el paciente no confirma su correo con el código (USR-20). */
    val correoVerificado: Boolean = true,
    /** Una cuenta desactivada no puede iniciar sesión, pero conserva su historial. */
    val activo: Boolean = true,
    /** Versión del aviso de privacidad que aceptó el paciente (USR-21); null si no lo ha aceptado. */
    val avisoAceptado: String? = null,
    /** true cuando el administrador dio de alta la cuenta con una contraseña temporal (USR-02). */
    val debeCambiarContrasena: Boolean = false,
) {
    fun aPublico() = UsuarioPublico(id, usuario, nombre, rol, pacienteId, correo)
}

/** Datos del usuario que sí viajan al cliente (sin contraseña ni hash). */
@Serializable
data class UsuarioPublico(
    val id: Int,
    val usuario: String,
    val nombre: String,
    val rol: Rol,
    val pacienteId: Int? = null,
    val correo: String? = null,
)
