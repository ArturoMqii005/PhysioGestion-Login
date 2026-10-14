package com.terapid.physiogestion.datos.local

import platform.Foundation.NSUserDefaults

/** Implementación iOS: NSUserDefaults, el mecanismo propio de Apple. Las claves llevan el nombre como prefijo. */
actual class AlmacenPreferencias actual constructor(private val nombre: String) {

    private val preferencias = NSUserDefaults.standardUserDefaults

    private fun llave(clave: String) = "$nombre.$clave"

    actual fun guardarTexto(clave: String, valor: String) {
        preferencias.setObject(valor, llave(clave))
    }

    actual fun leerTexto(clave: String, valorPorDefecto: String): String =
        preferencias.stringForKey(llave(clave)) ?: valorPorDefecto

    actual fun guardarEntero(clave: String, valor: Int) {
        preferencias.setInteger(valor.toLong(), llave(clave))
    }

    actual fun leerEntero(clave: String, valorPorDefecto: Int): Int =
        if (contiene(clave)) preferencias.integerForKey(llave(clave)).toInt() else valorPorDefecto

    actual fun guardarLargo(clave: String, valor: Long) {
        preferencias.setInteger(valor, llave(clave))
    }

    actual fun leerLargo(clave: String, valorPorDefecto: Long): Long =
        if (contiene(clave)) preferencias.integerForKey(llave(clave)) else valorPorDefecto

    actual fun guardarBooleano(clave: String, valor: Boolean) {
        preferencias.setBool(valor, llave(clave))
    }

    actual fun leerBooleano(clave: String, valorPorDefecto: Boolean): Boolean =
        if (contiene(clave)) preferencias.boolForKey(llave(clave)) else valorPorDefecto

    actual fun contiene(clave: String): Boolean = preferencias.objectForKey(llave(clave)) != null

    actual fun borrar(clave: String) {
        preferencias.removeObjectForKey(llave(clave))
    }
}
