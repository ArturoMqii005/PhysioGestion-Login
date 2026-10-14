package com.terapid.physiogestion.datos.sensores

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Implementación ESCRITORIO: no hay contador de pasos. */
actual class SensorPasos actual constructor() {
    actual val estaDisponible: Boolean = false
    actual val cuentaDesdeInicioDelDia: Boolean = false

    actual fun pasos(): Flow<Long> = emptyFlow()

    actual fun detener() = Unit
}
