package com.terapid.physiogestion.modelo

/**
 * Resumen de un ejercicio medido con el acelerómetro y el giroscopio, tal
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
) {
    val cumpleObjetivo: Boolean
        get() = repeticiones >= repeticionesObjetivo && (anguloObjetivo == null || anguloMaximo >= anguloObjetivo)
}
