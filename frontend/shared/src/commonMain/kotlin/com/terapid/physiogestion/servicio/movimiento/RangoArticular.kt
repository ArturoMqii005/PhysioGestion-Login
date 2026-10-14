package com.terapid.physiogestion.servicio.movimiento

import kotlin.math.abs

/**
 * Clasificación del rango de movimiento alcanzado respecto del objetivo que
 * fijó el fisioterapeuta. Traduce grados a una categoría que el paciente
 * entiende; vive aquí (y no en la vista) para poder probarla sin emulador.
 */
enum class RangoArticular {
    INSUFICIENTE,
    ACEPTABLE,
    OBJETIVO_ALCANZADO,
    EXCEDIDO;

    companion object {
        /** Debajo de este porcentaje del objetivo el rango es insuficiente. */
        const val UMBRAL_ACEPTABLE = 0.70f

        /** Grados por encima del objetivo que ya se consideran forzar de más. */
        const val MARGEN_EXCESO = 15f

        fun clasificar(anguloAlcanzado: Float, anguloObjetivo: Int?): RangoArticular {
            if (anguloObjetivo == null || anguloObjetivo <= 0) return ACEPTABLE
            val proporcion = anguloAlcanzado / anguloObjetivo
            return when {
                anguloAlcanzado > anguloObjetivo + MARGEN_EXCESO -> EXCEDIDO
                proporcion >= 1f -> OBJETIVO_ALCANZADO
                proporcion >= UMBRAL_ACEPTABLE -> ACEPTABLE
                else -> INSUFICIENTE
            }
        }
    }
}

/** Mensaje que ve el paciente para cada clasificación. */
fun RangoArticular.mensaje(): String = when (this) {
    RangoArticular.INSUFICIENTE -> "Intenta llegar un poco más lejos"
    RangoArticular.ACEPTABLE -> "Vas bien, casi llegas al objetivo"
    RangoArticular.OBJETIVO_ALCANZADO -> "Alcanzaste el rango indicado"
    RangoArticular.EXCEDIDO -> "Cuidado: estás forzando más de lo indicado"
}

/** Diferencia en grados respecto del objetivo, sin signo. */
fun desviacionAbsoluta(alcanzado: Float, objetivo: Int?): Float =
    if (objetivo == null) 0f else abs(alcanzado - objetivo)
