package com.terapid.physiogestion.datos.local

/** Límite de la caché de videos. Al pasarlo se borran los videos más antiguos. */
const val LIMITE_CACHE_VIDEOS_MB = 200

/**
 * ALMACENAMIENTO LOCAL · ARCHIVOS (caché de videos de los ejercicios).
 *
 * Un video pesa varios megabytes y solo se reproduce completo, así que no va
 * en preferencias ni en la base de datos: va al directorio de caché que
 * reserva cada sistema. El sistema puede vaciarlo si falta espacio, y está
 * bien: un video borrado se vuelve a descargar, una sesión borrada no.
 */
expect class AlmacenArchivos(subcarpeta: String = "videos") {

    /** Ruta de la carpeta de caché en esta plataforma. */
    fun directorio(): String

    fun existeVideo(idEjercicio: Int): Boolean

    /** Guarda el video descargado y devuelve su ruta local. */
    fun guardarVideo(idEjercicio: Int, contenido: ByteArray): String

    /** Ruta local del video, o null si no se ha descargado. */
    fun rutaVideo(idEjercicio: Int): String?

    /** Bytes que ocupa la caché. */
    fun tamanoOcupado(): Long

    /** Cantidad de videos guardados. */
    fun cantidad(): Int

    /** Vacía la caché. */
    fun limpiar()
}

/** 1536000 -> "1.5 MB" */
fun formatearTamano(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> {
        val decimas = bytes * 10 / (1024 * 1024)
        "${decimas / 10}.${decimas % 10} MB"
    }
}
