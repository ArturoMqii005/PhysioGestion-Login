package com.terapid.physiogestion.servicio.movimiento

/**
 * Una repetición detectada. Es el resultado de interpretar la señal, no la
 * señal misma: de cientos de lecturas quedan tres números.
 */
data class Repeticion(
    val numero: Int,
    val duracionMs: Long,
    /** Ángulo máximo alcanzado en esta repetición, en grados. */
    val anguloAlcanzado: Float,
)

/** Resultado acumulado del análisis de un ejercicio. */
data class ResultadoEjercicio(
    val idEjercicio: Int,
    val repeticionesDetectadas: Int,
    val repeticionesObjetivo: Int,
    val anguloMaximo: Float,
    val anguloObjetivo: Int?,
    val duracionSegundos: Int,
) {
    /** Porcentaje de repeticiones completadas, sin pasar de 100. */
    val porcentajeRepeticiones: Int
        get() = if (repeticionesObjetivo <= 0) 0 else minOf(100, repeticionesDetectadas * 100 / repeticionesObjetivo)

    /** true cuando se alcanzaron las repeticiones y el ángulo pedidos. */
    val cumpleObjetivo: Boolean
        get() = repeticionesDetectadas >= repeticionesObjetivo &&
            (anguloObjetivo == null || anguloMaximo >= anguloObjetivo)
}
