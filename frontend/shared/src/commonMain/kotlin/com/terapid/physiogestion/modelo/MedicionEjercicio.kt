package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/**
 * Cómo se midió un ejercicio. Viaja al servidor y a la vista del
 * fisioterapeuta, que así distingue lo que solo midió el teléfono de lo que
 * además verificó la cámara.
 */
@Serializable
enum class MetodoMedicion {
    /** Acelerómetro y giroscopio con el teléfono sujeto al cuerpo. */
    SENSORES,

    /** Cámara con detección de postura y el teléfono apoyado (verificado, RFN-27). */
    CAMARA,
}

/**
 * Resumen de un ejercicio medido con sensores o verificado con la cámara, tal
 * como se guarda en la tabla medicion_ejercicio del teléfono.
 */
data class MedicionEjercicio(
    val id: Long = 0,
    val idEjercicio: Int,
    val claveDia: String,
    val medidoEn: Long,
    val repeticiones: Int,
    val repeticionesObjetivo: Int,
    val anguloMaximo: Int,
    val anguloObjetivo: Int?,
    val duracionSegundos: Int,
    val enviado: Boolean = false,
    val metodo: MetodoMedicion = MetodoMedicion.SENSORES,
) {
    val cumpleObjetivo: Boolean
        get() = repeticiones >= repeticionesObjetivo && (anguloObjetivo == null || anguloMaximo >= anguloObjetivo)
}
