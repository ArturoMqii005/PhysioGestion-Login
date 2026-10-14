package com.terapid.physiogestion.datos.sensores

import kotlin.math.sqrt

/**
 * Lectura de un sensor de tres ejes (acelerómetro o giroscopio).
 *
 * Unidades: el acelerómetro en m/s² y el giroscopio en rad/s. Cada
 * plataforma entrega siempre esas unidades (iOS multiplica por 9.81 porque
 * reporta la aceleración en múltiplos de g), así el código común no tiene
 * que preguntar en qué sistema está.
 */
data class LecturaEje(
    val x: Float,
    val y: Float,
    val z: Float,
    /** Milisegundos; solo importa la diferencia entre dos lecturas. */
    val marcaTiempo: Long,
) {
    /** Magnitud del vector, útil para saber si hubo movimiento sin importar la dirección. */
    val magnitud: Float get() = sqrt(x * x + y * y + z * z)
}

/** Periodos de muestreo que usa la app. */
object PeriodosMuestreo {
    /** Durante el ejercicio: 50 lecturas por segundo. */
    const val EJERCICIO_MS = 20

    /** Fuera del ejercicio basta con 5 por segundo. */
    const val REPOSO_MS = 200
}
