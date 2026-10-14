package com.terapid.physiogestion.datos.sensores

import kotlinx.coroutines.flow.Flow

/**
 * SENSORES INERCIALES: acelerómetro y giroscopio (RFN-26).
 *
 * Se declara con expect porque cada sistema los expone distinto: Android con
 * SensorManager y un receptor de eventos, iOS con CMMotionManager. El
 * contrato se expresa en magnitudes físicas, y por eso todo el filtrado, el
 * conteo de repeticiones y la medición del ángulo viven en código común.
 */
expect class SensorMovimiento() {

    /** false cuando el dispositivo no tiene acelerómetro (por ejemplo, una computadora). */
    val hayAcelerometro: Boolean

    /** false cuando el dispositivo no tiene giroscopio. */
    val hayGiroscopio: Boolean

    /** Lecturas del acelerómetro en m/s². El sensor se apaga solo al cancelar la corrutina que las recoge. */
    fun acelerometro(periodoMs: Int = PeriodosMuestreo.EJERCICIO_MS): Flow<LecturaEje>

    /** Lecturas del giroscopio en rad/s. */
    fun giroscopio(periodoMs: Int = PeriodosMuestreo.EJERCICIO_MS): Flow<LecturaEje>

    /** Da de baja cualquier receptor que siga registrado. */
    fun detener()
}
