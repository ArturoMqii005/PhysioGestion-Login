package com.terapid.physiogestion.datos.sensores

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Implementación ESCRITORIO: una computadora no tiene acelerómetro ni
 * giroscopio. La pantalla del ejercicio lo detecta y ofrece contar las
 * repeticiones a mano.
 */
actual class SensorMovimiento actual constructor() {
    actual val hayAcelerometro: Boolean = false
    actual val hayGiroscopio: Boolean = false

    actual fun acelerometro(periodoMs: Int): Flow<LecturaEje> = emptyFlow()

    actual fun giroscopio(periodoMs: Int): Flow<LecturaEje> = emptyFlow()

    actual fun detener() = Unit
}
