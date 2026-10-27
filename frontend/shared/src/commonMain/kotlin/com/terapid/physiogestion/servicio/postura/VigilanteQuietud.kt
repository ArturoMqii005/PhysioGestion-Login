package com.terapid.physiogestion.servicio.postura

import com.terapid.physiogestion.datos.sensores.LecturaEje
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Vigila con el acelerómetro que el teléfono siga APOYADO mientras la cámara
 * observa (fusión de sensores contra la trampa).
 *
 * Con el teléfono quieto, el acelerómetro solo mide la gravedad: siempre la
 * misma dirección y unos 9.81 m/s². Si alguien lo levanta o lo mueve para
 * fingir el ejercicio frente a la cámara, cambian la dirección o la
 * magnitud. La primera lectura fija la referencia; desde ahí:
 *  - Se considera MOVIDO si la gravedad gira más de [inclinacionMaxima]
 *    grados o la magnitud se aleja más de [sacudidaMaxima] m/s² respecto de
 *    la última posición de reposo (así también se nota un giro lento).
 *  - Vuelve a QUIETO solo después de [esperaParaQuietoMs] sin movimiento,
 *    para que un temblor no lo haga parpadear. Esto permite tocar «Comenzar»
 *    con el teléfono en la mano y después apoyarlo.
 */
class VigilanteQuietud(
    private val inclinacionMaxima: Float = 8f,
    private val sacudidaMaxima: Float = 2.0f,
    private val esperaParaQuietoMs: Long = 1_000L,
    /** Suavizado del acelerómetro (paso bajo) para ignorar vibraciones mínimas. */
    private val alfa: Float = 0.3f,
) {
    private var referencia: FloatArray? = null
    private var filtrada: FloatArray? = null
    private var ultimoMovimiento = Long.MIN_VALUE

    var quieto: Boolean = true
        private set

    /** Reinicia la vigilancia: la siguiente lectura será la nueva posición de referencia. */
    fun reiniciar() {
        referencia = null
        filtrada = null
        ultimoMovimiento = Long.MIN_VALUE
        quieto = true
    }

    /** Procesa una lectura del acelerómetro y devuelve si el teléfono sigue quieto. */
    fun procesar(lectura: LecturaEje): Boolean {
        val anterior = filtrada
        val actual = if (anterior == null) {
            floatArrayOf(lectura.x, lectura.y, lectura.z)
        } else {
            floatArrayOf(
                alfa * lectura.x + (1 - alfa) * anterior[0],
                alfa * lectura.y + (1 - alfa) * anterior[1],
                alfa * lectura.z + (1 - alfa) * anterior[2],
            )
        }
        filtrada = actual
        val ref = referencia ?: run {
            referencia = actual.copyOf()
            return quieto
        }
        val magnitud = norma(actual)
        val inclinacion = anguloEntre(ref, actual)
        val seMovio = inclinacion > inclinacionMaxima || abs(magnitud - norma(ref)) > sacudidaMaxima
        if (seMovio) {
            // La nueva posición es la referencia: si lo dejan apoyado ahí, en un segundo vuelve a estar quieto.
            referencia = actual.copyOf()
            ultimoMovimiento = lectura.marcaTiempo
            quieto = false
        } else if (!quieto && lectura.marcaTiempo - ultimoMovimiento >= esperaParaQuietoMs) {
            quieto = true
        }
        return quieto
    }

    private fun norma(v: FloatArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    private fun anguloEntre(a: FloatArray, b: FloatArray): Float {
        val na = norma(a)
        val nb = norma(b)
        if (na < 1e-3f || nb < 1e-3f) return 0f
        val coseno = ((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (na * nb)).coerceIn(-1f, 1f)
        return acos(coseno) * 57.29578f
    }
}
