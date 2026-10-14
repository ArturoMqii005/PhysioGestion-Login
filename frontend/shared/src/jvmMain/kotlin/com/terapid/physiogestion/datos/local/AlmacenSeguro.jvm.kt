package com.terapid.physiogestion.datos.local

import java.util.prefs.Preferences

/**
 * Implementación ESCRITORIO. La versión de escritorio solo se usa para
 * desarrollo y pruebas, así que guarda el token en las preferencias del
 * usuario del sistema operativo, sin cifrado adicional.
 */
actual class AlmacenSeguro internal constructor(nombreNodo: String) {

    actual constructor() : this("com/terapid/physiogestion_seguro")

    private val nodo: Preferences = Preferences.userRoot().node(nombreNodo)

    actual fun guardarToken(token: String) {
        nodo.put(CLAVE_TOKEN, token)
        nodo.flush()
    }

    actual fun leerToken(): String? = nodo.get(CLAVE_TOKEN, null)

    actual fun borrarToken() {
        nodo.remove(CLAVE_TOKEN)
        nodo.flush()
    }

    /** Solo para pruebas: elimina el nodo completo. */
    internal fun eliminarTodo() {
        nodo.removeNode()
    }

    private companion object {
        const val CLAVE_TOKEN = "token_acceso"
    }
}
