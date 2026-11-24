package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.AutenticacionApi
import com.terapid.physiogestion.modelo.Sesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.StateFlow

/** Inicio y cierre de sesión: une la API con el [GestorSesion]. */
class AutenticacionRepositorio(
    private val api: AutenticacionApi,
    private val gestor: GestorSesion,
    /** Antes de invalidar el token en el servidor (la app da de baja el teléfono para push). */
    private val antesDeCerrar: suspend (token: String) -> Unit = {},
) {
    val sesion: StateFlow<Sesion?> = gestor.sesion

    /** Valida las credenciales en el backend; si son correctas, abre la sesión. */
    suspend fun iniciarSesion(usuario: String, contrasena: String): Sesion {
        val respuesta = api.iniciarSesion(usuario, contrasena)
        val sesion = Sesion(
            token = respuesta.token,
            usuario = respuesta.usuario,
            requiereConsentimiento = respuesta.requiereConsentimiento,
            debeCambiarContrasena = respuesta.debeCambiarContrasena,
        )
        gestor.abrir(sesion)
        return sesion
    }

    /** Motivo por el que la sesión se cerró sola, para mostrarlo en el login (una sola vez). */
    fun tomarAvisoDeCierre(): String? = gestor.tomarAviso()

    /** Avisa al servidor (si responde) y cierra la sesión local en cualquier caso. */
    suspend fun cerrarSesion() {
        val token = gestor.sesion.value?.token
        gestor.cerrar()
        if (token != null) {
            try {
                try {
                    withTimeout(4_000) { antesDeCerrar(token) }
                } catch (e: CancellationException) {
                    if (e !is TimeoutCancellationException) throw e
                } catch (_: Exception) {
                    // Sin red: el servidor lo dará de baja cuando Firebase reporte el token.
                }
                api.cerrarSesion(token)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Si el servidor no responde, la sesión local ya quedó cerrada.
            }
        }
    }
}
