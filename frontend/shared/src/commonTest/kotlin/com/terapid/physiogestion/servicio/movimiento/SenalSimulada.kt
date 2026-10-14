package com.terapid.physiogestion.servicio.movimiento

import com.terapid.physiogestion.datos.sensores.FiltroSenal
import com.terapid.physiogestion.datos.sensores.LecturaEje
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Genera lecturas como las de un teléfono sujeto al muslo que gira sobre su
 * eje X: la gravedad, vista desde el teléfono, es (0, g·sen θ, g·cos θ) y el
 * giroscopio marca la velocidad de giro dθ/dt en el eje X.
 */
object SenalSimulada {
    const val PERIODO_MS = 20L // 50 Hz

    /** Ángulo en grados para cada instante (ms). */
    fun interface Movimiento {
        fun angulo(ms: Long): Double
    }

    /** [n] repeticiones: subir hasta [grados] y bajar en [duracionMs], con [pausaMs] quieto entre una y otra. */
    fun repeticiones(n: Int, grados: Double, duracionMs: Long = 2_000, pausaMs: Long = 1_000) = Movimiento { ms ->
        val ciclo = duracionMs + pausaMs
        val enCiclo = ms % ciclo
        if (ms >= n * ciclo || enCiclo >= duracionMs) 0.0
        else grados * (1 - cos(2 * PI * enCiclo / duracionMs)) / 2
    }

    fun aceleracion(movimiento: Movimiento, ms: Long, ruido: Double = 0.0): LecturaEje {
        val theta = movimiento.angulo(ms) * PI / 180
        val vibracion = if (ruido == 0.0) 0.0 else ruido * sin(ms * 0.9)
        return LecturaEje(
            x = vibracion.toFloat(),
            y = (FiltroSenal.GRAVEDAD * sin(theta) + vibracion).toFloat(),
            z = (FiltroSenal.GRAVEDAD * cos(theta)).toFloat(),
            marcaTiempo = ms,
        )
    }

    fun giro(movimiento: Movimiento, ms: Long, sesgo: Float = 0f): LecturaEje {
        val antes = movimiento.angulo(ms - PERIODO_MS)
        val despues = movimiento.angulo(ms)
        val velocidad = (despues - antes) * PI / 180 / (PERIODO_MS / 1000.0) // rad/s
        return LecturaEje(velocidad.toFloat() + sesgo, sesgo, sesgo, ms)
    }

    /** Lecturas con el teléfono quieto, para calibrar. */
    fun reposo(muestras: Int = 75, sesgoGiro: Float = 0f): Pair<List<LecturaEje>, List<LecturaEje>> {
        val quieto = Movimiento { 0.0 }
        val acel = (0 until muestras).map { aceleracion(quieto, it * PERIODO_MS) }
        val giro = (0 until muestras).map { LecturaEje(sesgoGiro, sesgoGiro, sesgoGiro, it * PERIODO_MS) }
        return acel to giro
    }
}
