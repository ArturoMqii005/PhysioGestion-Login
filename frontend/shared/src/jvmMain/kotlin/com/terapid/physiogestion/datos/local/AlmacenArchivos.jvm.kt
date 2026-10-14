package com.terapid.physiogestion.datos.local

import java.io.File

/** Implementación ESCRITORIO: carpeta .physiogestion/cache dentro del usuario. */
actual class AlmacenArchivos actual constructor(private val subcarpeta: String) {

    private val carpeta: File by lazy {
        File(System.getProperty("user.home"), ".physiogestion/cache/$subcarpeta").apply { mkdirs() }
    }

    private fun archivoDe(idEjercicio: Int) = File(carpeta, "ejercicio_$idEjercicio.mp4")

    actual fun directorio(): String = carpeta.absolutePath

    actual fun existeVideo(idEjercicio: Int): Boolean = archivoDe(idEjercicio).exists()

    actual fun guardarVideo(idEjercicio: Int, contenido: ByteArray): String {
        val archivo = archivoDe(idEjercicio)
        archivo.writeBytes(contenido)
        respetarLimite()
        return archivo.absolutePath
    }

    actual fun rutaVideo(idEjercicio: Int): String? = archivoDe(idEjercicio).takeIf { it.exists() }?.absolutePath

    actual fun tamanoOcupado(): Long = carpeta.listFiles()?.sumOf { it.length() } ?: 0L

    actual fun cantidad(): Int = carpeta.listFiles()?.size ?: 0

    actual fun limpiar() {
        carpeta.listFiles()?.forEach { it.delete() }
    }

    private fun respetarLimite() {
        val limite = LIMITE_CACHE_VIDEOS_MB * 1024L * 1024L
        var ocupado = tamanoOcupado()
        carpeta.listFiles()?.sortedBy { it.lastModified() }?.forEach { archivo ->
            if (ocupado <= limite) return
            ocupado -= archivo.length()
            archivo.delete()
        }
    }

    /** Solo para pruebas: borra también la carpeta. */
    internal fun eliminarCarpeta() {
        carpeta.deleteRecursively()
    }
}
