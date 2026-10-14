package com.terapid.physiogestion.datos.sensores

import kotlin.math.sqrt

/** Resultado de la fase de calibración. */
enum class Calibracion { CORRECTA, POCAS_MUESTRAS, HUBO_MOVIMIENTO }

/**
 * CALIBRACIÓN Y FILTRADO.
 *
 * Las lecturas crudas traen dos problemas distintos que se corrigen distinto:
 *
 * 1. El SESGO (desviación de cero): un giroscopio quieto no marca 0 sino un
 *    valor pequeño y constante. Se corrige restando el promedio medido con
 *    el teléfono en reposo. En el acelerómetro el promedio en reposo NO es
 *    un error: es la gravedad en la postura inicial, y se guarda como
 *    [referencia] para medir después cuánto se inclinó el teléfono.
 *
 * 2. El RUIDO (vibración y electrónica): se atenúa con un filtro paso bajo
 *    exponencial, que solo recuerda el valor anterior y cuesta una
 *    multiplicación y una suma por eje.
 */
class FiltroSenal(
    /**
     * Compromiso entre suavidad y retraso: un valor bajo filtra más ruido pero
     * reacciona tarde. 0.15 a 50 Hz funciona para los movimientos lentos de
     * la rehabilitación (uno a tres segundos por repetición).
     */
    private val alfa: Float = 0.15f,
) {
    private var sesgoGiroX = 0f
    private var sesgoGiroY = 0f
    private var sesgoGiroZ = 0f
    private var ultima: LecturaEje? = null

    /** Gravedad promedio con el teléfono quieto en la postura inicial. */
    var referencia: LecturaEje? = null
        private set

    val estaCalibrado: Boolean get() = referencia != null

    /**
     * Promedia las lecturas tomadas con el teléfono quieto. Si la magnitud de
     * la aceleración varió demasiado, el paciente se movió y hay que repetir.
     */
    fun calibrar(aceleracionEnReposo: List<LecturaEje>, giroEnReposo: List<LecturaEje> = emptyList()): Calibracion {
        if (aceleracionEnReposo.size < MUESTRAS_MINIMAS) return Calibracion.POCAS_MUESTRAS
        val magnitudes = aceleracionEnReposo.map { it.magnitud }
        val media = magnitudes.average()
        val desviacion = sqrt(magnitudes.sumOf { (it - media) * (it - media) } / magnitudes.size)
        if (desviacion > MOVIMIENTO_MAXIMO) return Calibracion.HUBO_MOVIMIENTO

        referencia = LecturaEje(
            x = aceleracionEnReposo.map { it.x }.average().toFloat(),
            y = aceleracionEnReposo.map { it.y }.average().toFloat(),
            z = aceleracionEnReposo.map { it.z }.average().toFloat(),
            marcaTiempo = aceleracionEnReposo.last().marcaTiempo,
        )
        if (giroEnReposo.isNotEmpty()) {
            sesgoGiroX = giroEnReposo.map { it.x }.average().toFloat()
            sesgoGiroY = giroEnReposo.map { it.y }.average().toFloat()
            sesgoGiroZ = giroEnReposo.map { it.z }.average().toFloat()
        }
        ultima = null
        return Calibracion.CORRECTA
    }

    /** Filtro paso bajo del acelerómetro: suaviza la señal conservando la gravedad. */
    fun filtrarAceleracion(lectura: LecturaEje): LecturaEje {
        val anterior = ultima ?: lectura
        val filtrada = LecturaEje(
            x = alfa * lectura.x + (1 - alfa) * anterior.x,
            y = alfa * lectura.y + (1 - alfa) * anterior.y,
            z = alfa * lectura.z + (1 - alfa) * anterior.z,
            marcaTiempo = lectura.marcaTiempo,
        )
        ultima = filtrada
        return filtrada
    }

    /** Resta al giroscopio el sesgo medido en reposo. */
    fun corregirGiro(lectura: LecturaEje): LecturaEje =
        lectura.copy(x = lectura.x - sesgoGiroX, y = lectura.y - sesgoGiroY, z = lectura.z - sesgoGiroZ)

    /** Vuelve a empezar el filtrado (la calibración se conserva). */
    fun reiniciar() {
        ultima = null
    }

    companion object {
        const val GRAVEDAD = 9.81f

        /** Lecturas mínimas para que el promedio sea confiable (1.5 s a 50 Hz dan 75). */
        const val MUESTRAS_MINIMAS = 25

        /** Duración de la calibración, en milisegundos. */
        const val DURACION_CALIBRACION_MS = 1_500L

        /** Desviación máxima de la magnitud (m/s²) para considerar que el teléfono estuvo quieto. */
        const val MOVIMIENTO_MAXIMO = 0.35
    }
}
