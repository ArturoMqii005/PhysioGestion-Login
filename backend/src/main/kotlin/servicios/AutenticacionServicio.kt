package com.terapid.physiogestion.servicios

import com.terapid.physiogestion.modelos.LoginRespuesta
import com.terapid.physiogestion.modelos.UsuarioPublico
import com.terapid.physiogestion.modelos.UsuarioRegistrado
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Valida las credenciales predefinidas (usuarios.json) y administra las
 * sesiones abiertas. Cada inicio de sesión recibe un token que la app
 * manda después en el encabezado "Authorization: Bearer <token>".
 */
class AutenticacionServicio(private val usuarios: List<UsuarioRegistrado>) {

    /** Sesiones activas en memoria: token -> usuario. Se pierden al reiniciar el servidor. */
    private val sesiones = ConcurrentHashMap<String, UsuarioPublico>()

    /** Devuelve la sesión nueva, o null si el usuario o la contraseña no coinciden. */
    fun iniciarSesion(usuario: String, contrasena: String): LoginRespuesta? {
        val encontrado = usuarios.firstOrNull {
            it.usuario.equals(usuario.trim(), ignoreCase = true) && it.contrasena == contrasena
        } ?: return null
        val token = UUID.randomUUID().toString()
        val publico = encontrado.aPublico()
        sesiones[token] = publico
        return LoginRespuesta(token = token, usuario = publico)
    }

    /** Usuario dueño del token, o null si el token no existe (sesión cerrada o inválida). */
    fun usuarioDeToken(token: String): UsuarioPublico? = sesiones[token]

    fun cerrarSesion(token: String) {
        sesiones.remove(token)
    }
}
