package com.terapid.physiogestion.datos.sensores

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

/**
 * Implementación iOS de los sensores inerciales con CMMotionManager (CoreMotion).
 *
 * Diferencia de unidades: iOS reporta la aceleración en múltiplos de la
 * gravedad y Android en m/s². Aquí se multiplica por 9.81 para que el código
 * común reciba siempre la misma magnitud.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SensorMovimiento actual constructor() {

    private val gestor = CMMotionManager()

    actual val hayAcelerometro: Boolean get() = gestor.accelerometerAvailable
    actual val hayGiroscopio: Boolean get() = gestor.gyroAvailable

    actual fun acelerometro(periodoMs: Int): Flow<LecturaEje> = callbackFlow {
        if (!gestor.accelerometerAvailable) {
            close()
            return@callbackFlow
        }
        gestor.accelerometerUpdateInterval = periodoMs / 1000.0
        gestor.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { datos, _ ->
            val marca = ((datos?.timestamp ?: 0.0) * 1000).toLong() // segundos desde el arranque -> ms
            datos?.acceleration?.useContents {
                trySend(
                    LecturaEje(
                        x = (x * GRAVEDAD).toFloat(),
                        y = (y * GRAVEDAD).toFloat(),
                        z = (z * GRAVEDAD).toFloat(),
                        marcaTiempo = marca,
                    )
                )
            }
        }
        awaitClose { gestor.stopAccelerometerUpdates() }
    }

    actual fun giroscopio(periodoMs: Int): Flow<LecturaEje> = callbackFlow {
        if (!gestor.gyroAvailable) {
            close()
            return@callbackFlow
        }
        gestor.gyroUpdateInterval = periodoMs / 1000.0
        gestor.startGyroUpdatesToQueue(NSOperationQueue.mainQueue) { datos, _ ->
            val marca = ((datos?.timestamp ?: 0.0) * 1000).toLong()
            datos?.rotationRate?.useContents {
                // CoreMotion ya entrega rad/s, igual que Android.
                trySend(LecturaEje(x.toFloat(), y.toFloat(), z.toFloat(), marca))
            }
        }
        awaitClose { gestor.stopGyroUpdates() }
    }

    actual fun detener() {
        gestor.stopAccelerometerUpdates()
        gestor.stopGyroUpdates()
    }

    private companion object {
        const val GRAVEDAD = 9.81
    }
}
