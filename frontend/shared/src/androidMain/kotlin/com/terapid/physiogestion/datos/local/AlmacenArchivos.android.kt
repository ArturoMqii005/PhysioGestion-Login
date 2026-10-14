package com.terapid.physiogestion.datos.local

import com.terapid.physiogestion.plataforma.ContextoAndroid
import java.io.File

/**
 * Implementación ANDROID de la caché de archivos: usa cacheDir, el directorio
 * que Android reserva para contenido prescindible. El usuario lo puede borrar
 * desde Ajustes → Apps → PhysioGestion → Almacenamiento → Borrar caché.
 */
actual class AlmacenArchivos actual constructor(private val subcarpeta: String) {

    private val carpeta: File by lazy { File(ContextoAndroid.aplicacion.cacheDir, subcarpeta).apply { mkdirs() } }

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

    /** Si la caché pasa del límite, borra primero los videos más antiguos. */
    private fun respetarLimite() {
        val limite = LIMITE_CACHE_VIDEOS_MB * 1024L * 1024L
        var ocupado = tamanoOcupado()
        carpeta.listFiles()?.sortedBy { it.lastModified() }?.forEach { archivo ->
            if (ocupado <= limite) return
            ocupado -= archivo.length()
            archivo.delete()
        }
    }
}
