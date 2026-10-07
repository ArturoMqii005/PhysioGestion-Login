package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/** Perfiles de trabajo de PhysioGestion. Cada uno abre una vista distinta en la app. */
@Serializable
enum class Rol { ADMINISTRADOR, FISIOTERAPEUTA, RECEPCIONISTA, PACIENTE }

/**
 * Usuario tal como está escrito en resources/datos/usuarios.json.
 * Incluye la contraseña, por eso NUNCA se envía al cliente.
 */
@Serializable
data class UsuarioRegistrado(
    val id: Int,
    val usuario: String,
    val contrasena: String,
    val nombre: String,
    val rol: Rol,
    val pacienteId: Int? = null,
) {
    fun aPublico() = UsuarioPublico(id, usuario, nombre, rol, pacienteId)
}

/** Datos del usuario que sí viajan al cliente (sin contraseña). */
@Serializable
data class UsuarioPublico(
    val id: Int,
    val usuario: String,
    val nombre: String,
    val rol: Rol,
    val pacienteId: Int? = null,
)
