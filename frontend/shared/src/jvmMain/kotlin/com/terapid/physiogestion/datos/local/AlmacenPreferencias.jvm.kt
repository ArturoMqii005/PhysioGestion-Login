package com.terapid.physiogestion.datos.local

import java.util.prefs.Preferences

/**
 * Implementación ESCRITORIO: java.util.prefs.Preferences (en Windows queda en
 * el registro, en HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\terapid\...).
 */
actual class AlmacenPreferencias actual constructor(nombre: String) {

    private val nodo: Preferences = Preferences.userRoot().node("com/terapid/$nombre")

    actual fun guardarTexto(clave: String, valor: String) = nodo.put(clave, valor).also { nodo.flush() }

    actual fun leerTexto(clave: String, valorPorDefecto: String): String = nodo.get(clave, valorPorDefecto)

    actual fun guardarEntero(clave: String, valor: Int) = nodo.putInt(clave, valor).also { nodo.flush() }

    actual fun leerEntero(clave: String, valorPorDefecto: Int): Int = nodo.getInt(clave, valorPorDefecto)

    actual fun guardarLargo(clave: String, valor: Long) = nodo.putLong(clave, valor).also { nodo.flush() }

    actual fun leerLargo(clave: String, valorPorDefecto: Long): Long = nodo.getLong(clave, valorPorDefecto)

    actual fun guardarBooleano(clave: String, valor: Boolean) = nodo.putBoolean(clave, valor).also { nodo.flush() }

    actual fun leerBooleano(clave: String, valorPorDefecto: Boolean): Boolean = nodo.getBoolean(clave, valorPorDefecto)

    actual fun contiene(clave: String): Boolean = nodo.get(clave, null) != null

    actual fun borrar(clave: String) = nodo.remove(clave).also { nodo.flush() }

    /** Solo para pruebas: elimina el nodo completo. */
    internal fun eliminarTodo() {
        nodo.removeNode()
    }
}
