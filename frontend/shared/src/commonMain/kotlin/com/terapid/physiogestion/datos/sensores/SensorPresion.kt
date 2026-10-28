package com.terapid.physiogestion.datos.sensores

import kotlinx.coroutines.flow.Flow

/** Una lectura del barómetro: presión del aire en hectopascales (hPa) y su marca de tiempo en ms. */
data class LecturaPresion(val hPa: Float, val marcaTiempo: Long)

/**
 * BARÓMETRO (sensor ambiental de presión).
 *
 * La presión del aire baja al subir: cerca del nivel del mar, alrededor de
 * 0.12 hPa por metro. Con eso se mide cuánto subió el paciente en la prueba
 * de escaleras, independientemente de cómo sujete el teléfono. Lo tienen
 * la mayoría de los teléfonos de gama media y alta (el Galaxy S23 Ultra y el
 * Pixel 9a sí) y el emulador lo simula en Virtual sensors → Additional sensors.
 */
expect class SensorPresion() {

    /** false cuando el dispositivo no tiene barómetro. */
    val estaDisponible: Boolean

    /** Lecturas de presión. El sensor se apaga solo al cancelar la corrutina que las recoge. */
    fun presion(periodoMs: Int = PERIODO_PRESION_MS): Flow<LecturaPresion>

    fun detener()
}

/** 10 lecturas por segundo bastan: la altura cambia despacio. */
const val PERIODO_PRESION_MS = 100
