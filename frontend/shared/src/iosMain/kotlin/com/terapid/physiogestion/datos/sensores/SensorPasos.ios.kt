package com.terapid.physiogestion.datos.sensores

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreMotion.CMPedometer
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate

/**
 * Implementación iOS del contador de pasos con CMPedometer. A diferencia de
 * Android, cuenta desde la fecha que se le indique; aquí desde las 00:00.
 */
actual class SensorPasos actual constructor() {

    private val podometro = CMPedometer()

    actual val estaDisponible: Boolean get() = CMPedometer.isStepCountingAvailable()

    actual val cuentaDesdeInicioDelDia: Boolean = true

    actual fun pasos(): Flow<Long> = callbackFlow {
        if (!CMPedometer.isStepCountingAvailable()) {
            close()
            return@callbackFlow
        }
        val inicioDelDia = NSCalendar.currentCalendar.startOfDayForDate(NSDate())
        podometro.startPedometerUpdatesFromDate(inicioDelDia) { datos, _ ->
            datos?.numberOfSteps?.let { trySend(it.longValue) }
        }
        awaitClose { podometro.stopPedometerUpdates() }
    }

    actual fun detener() {
        podometro.stopPedometerUpdates()
    }
}
