package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/**
 * Prueba de escaleras (barómetro + contador de pasos) tal como se guarda en
 * la tabla prueba_escaleras del teléfono. Sirve para seguir la meta
 * funcional «subir las escaleras de mi casa sin dolor» semana a semana.
 */
data class PruebaEscaleras(
    val id: Long = 0,
    val claveDia: String,
    val medidoEn: Long,
    val alturaSubidaCm: Int,
    val pisos: Int,
    val escalones: Int,
    val pasos: Int?,
    val duracionSegundos: Int,
    val enviado: Boolean = false,
) {
    val metrosSubidos: Float get() = alturaSubidaCm / 100f

    val metrosPorMinuto: Float
        get() = if (duracionSegundos <= 0) 0f else metrosSubidos * 60f / duracionSegundos
}

/** Cuerpo de POST /api/v1/pruebas-escaleras: solo el resumen, nunca las lecturas del barómetro. */
@Serializable
data class PruebaEscalerasSolicitud(
    val fecha: String,
    val alturaSubidaCm: Int,
    val pisos: Int,
    val escalonesEstimados: Int,
    val pasos: Int? = null,
    val duracionSegundos: Int,
)

/** Prueba ya registrada en el servidor (la consulta el fisioterapeuta). */
@Serializable
data class PruebaEscalerasRegistrada(
    val id: Int,
    val pacienteId: Int,
    val paciente: String = "",
    val fecha: String,
    val alturaSubidaCm: Int,
    val pisos: Int,
    val escalonesEstimados: Int,
    val pasos: Int? = null,
    val duracionSegundos: Int,
) {
    val metrosSubidos: Float get() = alturaSubidaCm / 100f

    val metrosPorMinuto: Float
        get() = if (duracionSegundos <= 0) 0f else metrosSubidos * 60f / duracionSegundos
}
