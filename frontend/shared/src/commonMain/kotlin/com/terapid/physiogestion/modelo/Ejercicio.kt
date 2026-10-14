package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/**
 * MODELO: estructura de un ejercicio asignado por el fisioterapeuta.
 * Se recibe en JSON desde la API y se muestra en la vista.
 */
@Serializable
data class Ejercicio(
    val id: Int,
    val nombre: String,
    val indicaciones: String,
    val series: Int,
    val repeticiones: Int,
    val descansoSegundos: Int,
    val zonaCorporal: String,
    /** Ángulo que se espera en cada repetición, en grados; null si no aplica. */
    val anguloObjetivo: Int? = null,
    /** true si se puede medir con el acelerómetro y el giroscopio (RFN-26). */
    val medibleConSensores: Boolean = false,
    /** true si admite verificación visual con la cámara (RFN-27). */
    val admiteVerificacionPorCamara: Boolean = false,
) {
    /** Total de repeticiones de todas las series. */
    val repeticionesTotales: Int get() = series * repeticiones
}
