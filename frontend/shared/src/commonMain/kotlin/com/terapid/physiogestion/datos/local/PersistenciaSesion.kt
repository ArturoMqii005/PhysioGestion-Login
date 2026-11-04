package com.terapid.physiogestion.datos.local

import com.terapid.physiogestion.modelo.Sesion
import com.terapid.physiogestion.modelo.Usuario
import kotlinx.serialization.json.Json

/**
 * SESIÓN GUARDADA EN EL TELÉFONO: permite abrir la app sin volver a escribir
 * usuario y contraseña. Los datos del usuario van en preferencias y el token
 * va aparte, cifrado, en [AlmacenSeguro].
 */
class PersistenciaSesion(
    private val preferencias: AlmacenPreferencias,
    private val seguro: AlmacenSeguro,
    private val json: Json,
) {
    fun guardar(sesion: Sesion) {
        preferencias.guardarTexto(ClavesAlmacen.SESION_USUARIO, json.encodeToString(Usuario.serializer(), sesion.usuario))
        val pendientes = listOfNotNull(
            "AVISO".takeIf { sesion.requiereConsentimiento },
            "CONTRASENA".takeIf { sesion.debeCambiarContrasena },
        )
        preferencias.guardarTexto(ClavesAlmacen.SESION_PENDIENTES, pendientes.joinToString(","))
        seguro.guardarToken(sesion.token)
    }

    /** Sesión guardada, o null si no hay (o si algo no se pudo leer o descifrar). */
    fun leer(): Sesion? {
        val usuario = preferencias.leerTexto(ClavesAlmacen.SESION_USUARIO).takeIf { it.isNotBlank() } ?: return null
        val token = seguro.leerToken() ?: return null
        return try {
            val pendientes = preferencias.leerTexto(ClavesAlmacen.SESION_PENDIENTES).split(',')
            Sesion(
                token = token,
                usuario = json.decodeFromString(Usuario.serializer(), usuario),
                requiereConsentimiento = "AVISO" in pendientes,
                debeCambiarContrasena = "CONTRASENA" in pendientes,
            )
        } catch (_: Exception) {
            borrar()
            null
        }
    }

    /** Cierra la sesión y elimina todo rastro del token en el dispositivo. */
    fun borrar() {
        preferencias.borrar(ClavesAlmacen.SESION_USUARIO)
        preferencias.borrar(ClavesAlmacen.SESION_PENDIENTES)
        seguro.borrarToken()
    }
}
