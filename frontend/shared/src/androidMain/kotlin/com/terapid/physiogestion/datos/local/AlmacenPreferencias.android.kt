package com.terapid.physiogestion.datos.local

import android.content.Context
import android.content.SharedPreferences
import com.terapid.physiogestion.plataforma.ContextoAndroid

/** Implementación ANDROID: SharedPreferences, el mecanismo propio de la plataforma. */
actual class AlmacenPreferencias actual constructor(nombre: String) {

    private val preferencias: SharedPreferences =
        ContextoAndroid.aplicacion.getSharedPreferences(nombre, Context.MODE_PRIVATE)

    actual fun guardarTexto(clave: String, valor: String) = preferencias.edit().putString(clave, valor).apply()

    actual fun leerTexto(clave: String, valorPorDefecto: String): String =
        preferencias.getString(clave, valorPorDefecto) ?: valorPorDefecto

    actual fun guardarEntero(clave: String, valor: Int) = preferencias.edit().putInt(clave, valor).apply()

    actual fun leerEntero(clave: String, valorPorDefecto: Int): Int = preferencias.getInt(clave, valorPorDefecto)

    actual fun guardarLargo(clave: String, valor: Long) = preferencias.edit().putLong(clave, valor).apply()

    actual fun leerLargo(clave: String, valorPorDefecto: Long): Long = preferencias.getLong(clave, valorPorDefecto)

    actual fun guardarBooleano(clave: String, valor: Boolean) = preferencias.edit().putBoolean(clave, valor).apply()

    actual fun leerBooleano(clave: String, valorPorDefecto: Boolean): Boolean = preferencias.getBoolean(clave, valorPorDefecto)

    actual fun contiene(clave: String): Boolean = preferencias.contains(clave)

    actual fun borrar(clave: String) = preferencias.edit().remove(clave).apply()
}
