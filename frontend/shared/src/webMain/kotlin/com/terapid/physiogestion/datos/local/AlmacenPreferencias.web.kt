package com.terapid.physiogestion.datos.local

import com.terapid.physiogestion.plataforma.borrarLocal
import com.terapid.physiogestion.plataforma.guardarLocal
import com.terapid.physiogestion.plataforma.leerLocal

/**
 * Implementación WEB: localStorage del navegador. Se conserva al cerrar la
 * pestaña, igual que las preferencias en el teléfono. Cada clave lleva el
 * nombre del almacén como prefijo ("physiogestion_prefs.servidor").
 */
actual class AlmacenPreferencias actual constructor(nombre: String) {

    private val prefijo = "$nombre."

    actual fun guardarTexto(clave: String, valor: String) = guardarLocal(prefijo + clave, valor)

    actual fun leerTexto(clave: String, valorPorDefecto: String): String = leerLocal(prefijo + clave) ?: valorPorDefecto

    actual fun guardarEntero(clave: String, valor: Int) = guardarLocal(prefijo + clave, valor.toString())

    actual fun leerEntero(clave: String, valorPorDefecto: Int): Int =
        leerLocal(prefijo + clave)?.toIntOrNull() ?: valorPorDefecto

    actual fun guardarLargo(clave: String, valor: Long) = guardarLocal(prefijo + clave, valor.toString())

    actual fun leerLargo(clave: String, valorPorDefecto: Long): Long =
        leerLocal(prefijo + clave)?.toLongOrNull() ?: valorPorDefecto

    actual fun guardarBooleano(clave: String, valor: Boolean) = guardarLocal(prefijo + clave, valor.toString())

    actual fun leerBooleano(clave: String, valorPorDefecto: Boolean): Boolean =
        leerLocal(prefijo + clave)?.toBooleanStrictOrNull() ?: valorPorDefecto

    actual fun contiene(clave: String): Boolean = leerLocal(prefijo + clave) != null

    actual fun borrar(clave: String) = borrarLocal(prefijo + clave)
}
