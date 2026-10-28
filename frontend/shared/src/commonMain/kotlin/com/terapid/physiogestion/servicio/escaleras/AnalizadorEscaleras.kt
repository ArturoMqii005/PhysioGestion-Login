package com.terapid.physiogestion.servicio.escaleras

import com.terapid.physiogestion.datos.sensores.LecturaPresion
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** Resumen de una prueba de escaleras. */
data class ResultadoEscaleras(
    /** Altura subida en centímetros (solo cuenta lo que se sube, no lo que se baja). */
    val alturaSubidaCm: Int,
    val pisos: Int,
    val escalonesEstimados: Int,
    /** Pasos del contador del teléfono; null si no hay contador o no dio permiso. */
    val pasos: Int?,
    val duracionSegundos: Int,
) {
    val metrosSubidos: Float get() = alturaSubidaCm / 100f

    /** Velocidad de subida en metros por minuto: lo que se compara de una semana a otra. */
    val metrosPorMinuto: Float
        get() = if (duracionSegundos <= 0) 0f else metrosSubidos * 60f / duracionSegundos
}

enum class CalibracionPresion { CORRECTA, POCAS_MUESTRAS, INESTABLE }

/**
 * PROCESAMIENTO del barómetro para la prueba de escaleras.
 *
 *  1. Calibración: con el paciente quieto al pie de la escalera se promedia
 *     la presión; esa es la altura cero.
 *  2. Cada lectura se convierte en altura relativa con la fórmula
 *     barométrica h = 44330 · (1 − (p / p₀)^(1/5.255)), que sirve igual al
 *     nivel del mar que en Tulancingo (≈ 2200 m, ≈ 780 hPa).
 *  3. Un promedio exponencial quita el ruido del sensor (unas décimas de hPa).
 *  4. La subida se acumula con histéresis: solo cuenta cuando la altura sube
 *     más de [histeresisM] sobre el último nivel; bajar no resta ni suma, y
 *     el ruido en reposo no alcanza el umbral.
 *
 * Pisos y escalones se estiman con medidas típicas de una casa: 3 m por piso
 * y 17 cm por escalón.
 */
class AnalizadorEscaleras(
    private val alfa: Float = 0.15f,
    private val histeresisM: Float = 0.25f,
) {
    var presionBase: Float? = null
        private set

    private var suavizada: Float? = null
    private var ancla = 0f

    /** Altura actual respecto del punto de partida, en metros (puede ser negativa si bajó). */
    var alturaActual: Float = 0f
        private set

    private var subidaConfirmada = 0f

    /**
     * Total subido en metros: lo confirmado más lo que lleva subido desde el
     * último nivel (así el último tramo cuenta aunque no llegue al umbral).
     */
    val subidaAcumulada: Float get() = subidaConfirmada + maxOf(0f, alturaActual - ancla)

    /** Un piso de casa mide entre 2.7 y 3.2 m: se da una tolerancia antes de contarlo. */
    val pisos: Int get() = floor((subidaAcumulada + TOLERANCIA_PISO_M) / ALTURA_PISO_M).toInt()

    val escalonesEstimados: Int get() = (subidaAcumulada / ALTURA_ESCALON_M).roundToInt()

    /** Fija la altura cero con las lecturas tomadas en reposo. */
    fun calibrar(lecturas: List<LecturaPresion>): CalibracionPresion {
        if (lecturas.size < MUESTRAS_MINIMAS) return CalibracionPresion.POCAS_MUESTRAS
        val valores = lecturas.map { it.hPa }
        if (valores.max() - valores.min() > VARIACION_MAXIMA_HPA) return CalibracionPresion.INESTABLE
        presionBase = valores.average().toFloat()
        suavizada = null
        ancla = 0f
        alturaActual = 0f
        subidaConfirmada = 0f
        return CalibracionPresion.CORRECTA
    }

    /** Procesa una lectura y devuelve la altura actual en metros. */
    fun procesar(lectura: LecturaPresion): Float {
        val base = presionBase ?: return alturaActual
        val cruda = alturaRelativa(lectura.hPa, base)
        val anterior = suavizada
        val altura = if (anterior == null) cruda else alfa * cruda + (1 - alfa) * anterior
        suavizada = altura
        alturaActual = altura
        when {
            altura - ancla >= histeresisM -> {
                subidaConfirmada += altura - ancla
                ancla = altura
            }
            ancla - altura >= histeresisM -> ancla = altura // bajó: se mueve el nivel, sin restar
        }
        return altura
    }

    fun resultado(pasos: Int?, duracionSegundos: Int) = ResultadoEscaleras(
        alturaSubidaCm = (subidaAcumulada * 100).roundToInt(),
        pisos = pisos,
        escalonesEstimados = escalonesEstimados,
        pasos = pasos,
        duracionSegundos = duracionSegundos,
    )

    companion object {
        const val ALTURA_PISO_M = 3.0f
        const val TOLERANCIA_PISO_M = 0.4f
        const val ALTURA_ESCALON_M = 0.17f

        /** 3 s a 10 lecturas por segundo; con al menos 10 ya se puede promediar. */
        const val DURACION_CALIBRACION_MS = 3_000L
        const val MUESTRAS_MINIMAS = 10

        /** ≈ 1 m: si la presión varía más durante la calibración, el paciente no estaba quieto. */
        const val VARIACION_MAXIMA_HPA = 0.12f

        /** Altura en metros de la presión [hPa] respecto de [base] (fórmula barométrica internacional). */
        fun alturaRelativa(hPa: Float, base: Float): Float =
            (44_330.0 * (1.0 - (hPa.toDouble() / base.toDouble()).pow(1.0 / 5.255))).toFloat()
    }
}
