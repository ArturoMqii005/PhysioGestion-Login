package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.AutenticacionApi
import com.terapid.physiogestion.modelo.Sesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/** Inicio y cierre de sesión: une la API con el [GestorSesion]. */
class AutenticacionRepositorio(
    private val api: AutenticacionApi,
    private val gestor: GestorSesion,
) {
    val sesion: StateFlow<Sesion?> = gestor.sesion

    /** Valida las credenciales en el backend; si son correctas, abre la sesión. */
    suspend fun iniciarSesion(usuario: String, contrasena: String): Sesion {
        val respuesta = api.iniciarSesion(usuario, contrasena)
        val sesion = Sesion(token = respuesta.token, usuario = respuesta.usuario)
        gestor.abrir(sesion)
        return sesion
    }

    /** Avisa al servidor (si responde) y cierra la sesión local en cualquier caso. */
    suspend fun cerrarSesion() {
        val token = gestor.sesion.value?.token
        gestor.cerrar()
        if (token != null) {
            try {
                api.cerrarSesion(token)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Si el servidor no responde, la sesión local ya quedó cerrada.
            }
        }
    }
}
