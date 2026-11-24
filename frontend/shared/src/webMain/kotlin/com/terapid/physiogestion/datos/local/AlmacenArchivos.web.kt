package com.terapid.physiogestion.datos.local

/**
 * Implementación WEB: el navegador no deja escribir archivos, así que los
 * videos descargados quedan en memoria mientras la pestaña está abierta.
 */
actual class AlmacenArchivos actual constructor(private val subcarpeta: String) {

    private val videos = mutableMapOf<Int, ByteArray>()

    private fun rutaDe(idEjercicio: Int) = "memoria/$subcarpeta/ejercicio_$idEjercicio.mp4"

    actual fun directorio(): String = "memoria/$subcarpeta"

    actual fun existeVideo(idEjercicio: Int): Boolean = idEjercicio in videos

    actual fun guardarVideo(idEjercicio: Int, contenido: ByteArray): String {
        videos[idEjercicio] = contenido
        respetarLimite()
        return rutaDe(idEjercicio)
    }

    actual fun rutaVideo(idEjercicio: Int): String? = if (idEjercicio in videos) rutaDe(idEjercicio) else null

    actual fun tamanoOcupado(): Long = videos.values.sumOf { it.size.toLong() }

    actual fun cantidad(): Int = videos.size

    actual fun limpiar() = videos.clear()

    /** Igual que en el teléfono: al pasar el límite se quitan los primeros que se guardaron. */
    private fun respetarLimite() {
        val limite = LIMITE_CACHE_VIDEOS_MB * 1024L * 1024L
        while (tamanoOcupado() > limite && videos.size > 1) {
            videos.remove(videos.keys.first())
        }
    }
}
