package com.terapid.physiogestion.datos.local

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

/**
 * Implementación iOS de la caché de archivos: el directorio Caches de la app.
 * Igual que cacheDir en Android, el sistema puede vaciarlo cuando falta
 * espacio y, a diferencia de Documents, no se respalda en iCloud.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class AlmacenArchivos actual constructor(private val subcarpeta: String) {

    private val gestor = NSFileManager.defaultManager

    private val carpeta: String by lazy {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
        val ruta = "$caches/$subcarpeta"
        gestor.createDirectoryAtPath(ruta, true, null, null)
        ruta
    }

    private fun archivoDe(idEjercicio: Int) = "$carpeta/ejercicio_$idEjercicio.mp4"

    private fun nombres(): List<String> =
        gestor.contentsOfDirectoryAtPath(carpeta, null)?.filterIsInstance<String>() ?: emptyList()

    actual fun directorio(): String = carpeta

    actual fun existeVideo(idEjercicio: Int): Boolean = gestor.fileExistsAtPath(archivoDe(idEjercicio))

    actual fun guardarVideo(idEjercicio: Int, contenido: ByteArray): String {
        val ruta = archivoDe(idEjercicio)
        val datos = if (contenido.isEmpty()) {
            NSData()
        } else {
            contenido.usePinned { fijado -> NSData.create(bytes = fijado.addressOf(0), length = contenido.size.toULong()) }
        }
        datos.writeToFile(ruta, true)
        return ruta
    }

    actual fun rutaVideo(idEjercicio: Int): String? = archivoDe(idEjercicio).takeIf { existeVideo(idEjercicio) }

    actual fun tamanoOcupado(): Long = nombres().sumOf { nombre ->
        val atributos = gestor.attributesOfItemAtPath("$carpeta/$nombre", null)
        (atributos?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
    }

    actual fun cantidad(): Int = nombres().size

    actual fun limpiar() {
        nombres().forEach { gestor.removeItemAtPath("$carpeta/$it", null) }
    }
}
