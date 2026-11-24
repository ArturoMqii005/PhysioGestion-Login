package com.terapid.physiogestion.datos.sensores

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Implementación WEB: el navegador no entrega el acelerómetro ni el
 * giroscopio a la app. La pantalla del ejercicio lo detecta y ofrece contar las
 * repeticiones a mano.
 */
actual class SensorMovimiento actual constructor() {
    actual val hayAcelerometro: Boolean = false
    actual val hayGiroscopio: Boolean = false

    actual fun acelerometro(periodoMs: Int): Flow<LecturaEje> = emptyFlow()

    actual fun giroscopio(periodoMs: Int): Flow<LecturaEje> = emptyFlow()

    actual fun detener() = Unit
}
