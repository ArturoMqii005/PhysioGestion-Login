package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/**
 * Cuerpo de POST /api/v1/pruebas-escaleras: el resumen de la prueba de
 * escaleras que mide la app con el barómetro y el contador de pasos.
 * Mismos nombres de campo que en la app (frontend/.../modelo/PruebaEscaleras.kt).
 */
@Serializable
data class PruebaEscalerasSolicitud(
    /** Fecha y hora de la prueba en el teléfono (ISO 8601). */
    val fecha: String,
    val alturaSubidaCm: Int,
    val pisos: Int,
    val escalonesEstimados: Int,
    val pasos: Int? = null,
    val duracionSegundos: Int,
)

/** Prueba de escaleras registrada; el fisioterapeuta la consulta para seguir la meta funcional. */
@Serializable
data class PruebaEscalerasRegistrada(
    val id: Int,
    val pacienteId: Int,
    val paciente: String,
    val fecha: String,
    val alturaSubidaCm: Int,
    val pisos: Int,
    val escalonesEstimados: Int,
    val pasos: Int? = null,
    val duracionSegundos: Int,
)
