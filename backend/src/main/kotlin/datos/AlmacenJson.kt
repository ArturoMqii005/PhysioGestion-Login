package com.terapid.physiogestion.datos

import com.terapid.physiogestion.modelos.Cita
import com.terapid.physiogestion.modelos.Rutina
import com.terapid.physiogestion.modelos.UsuarioRegistrado
import kotlinx.serialization.json.Json

/**
 * Fuente de datos de la etapa local: lee los archivos JSON de
 * src/main/resources/datos/ y los convierte en objetos Kotlin con
 * kotlinx.serialization. Más adelante se reemplazará por la base de datos.
 */
class AlmacenJson(private val json: Json) {

    val usuarios: List<UsuarioRegistrado> = leer("usuarios.json")
    val citas: List<Cita> = leer("citas.json")
    val rutinas: List<Rutina> = leer("rutinas.json")

    /** Lee un archivo de resources/datos y lo deserializa a una lista del tipo indicado. */
    private inline fun <reified T> leer(archivo: String): List<T> {
        val texto = AlmacenJson::class.java.getResource("/datos/$archivo")?.readText(Charsets.UTF_8)
            ?: error("No se encontró el archivo de datos: $archivo")
        return json.decodeFromString<List<T>>(texto)
    }
}
