package com.terapid.physiogestion.datos.sensores

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * iOS: pendiente. Se haría con CMAltimeter (startRelativeAltitudeUpdates),
 * que entrega la presión en kPa y la altura relativa ya calculada.
 */
actual class SensorPresion actual constructor() {
    actual val estaDisponible: Boolean = false

    actual fun presion(periodoMs: Int): Flow<LecturaPresion> = emptyFlow()

    actual fun detener() = Unit
}
