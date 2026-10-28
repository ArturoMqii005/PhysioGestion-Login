package com.terapid.physiogestion.datos.sensores

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** ESCRITORIO: una computadora no tiene barómetro; la pantalla de la prueba lo indica. */
actual class SensorPresion actual constructor() {
    actual val estaDisponible: Boolean = false

    actual fun presion(periodoMs: Int): Flow<LecturaPresion> = emptyFlow()

    actual fun detener() = Unit
}
