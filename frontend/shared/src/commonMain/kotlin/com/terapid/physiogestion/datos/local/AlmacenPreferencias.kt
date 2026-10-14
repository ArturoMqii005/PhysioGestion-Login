package com.terapid.physiogestion.datos.local

/** Nombre del archivo de preferencias de la app. */
const val NOMBRE_PREFERENCIAS = "physiogestion_prefs"

/**
 * ALMACENAMIENTO LOCAL · PREFERENCIAS (clave → valor).
 *
 * Para datos pequeños y sueltos: la racha, la dirección del servidor, si la
 * verificación con cámara está activa. Lo que son muchos registros con
 * consultas (sesiones, mediciones) va en la base de datos SQLite
 * ([BaseDatosLocal]); lo que es binario y pesado (videos) va en archivos
 * ([AlmacenArchivos]); y el token va cifrado ([AlmacenSeguro]).
 *
 * Se declara con expect porque cada sistema tiene su mecanismo:
 * SharedPreferences en Android, NSUserDefaults en iOS y
 * java.util.prefs.Preferences en escritorio.
 */
expect class AlmacenPreferencias(nombre: String = NOMBRE_PREFERENCIAS) {
    fun guardarTexto(clave: String, valor: String)
    fun leerTexto(clave: String, valorPorDefecto: String = ""): String
    fun guardarEntero(clave: String, valor: Int)
    fun leerEntero(clave: String, valorPorDefecto: Int = 0): Int
    fun guardarLargo(clave: String, valor: Long)
    fun leerLargo(clave: String, valorPorDefecto: Long = 0L): Long
    fun guardarBooleano(clave: String, valor: Boolean)
    fun leerBooleano(clave: String, valorPorDefecto: Boolean = false): Boolean
    fun contiene(clave: String): Boolean
    fun borrar(clave: String)
}
