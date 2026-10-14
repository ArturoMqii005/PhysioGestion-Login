package com.terapid.physiogestion.datos.sensores

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.terapid.physiogestion.plataforma.ContextoAndroid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Implementación ANDROID del contador de pasos (TYPE_STEP_COUNTER). Su valor
 * se acumula desde el último reinicio del teléfono; la app guarda la primera
 * lectura del día y muestra la diferencia.
 */
actual class SensorPasos actual constructor() {

    private val gestor: SensorManager =
        ContextoAndroid.aplicacion.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val contador: Sensor? = gestor.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var receptor: SensorEventListener? = null

    actual val estaDisponible: Boolean get() = contador != null

    actual val cuentaDesdeInicioDelDia: Boolean = false

    actual fun pasos(): Flow<Long> = callbackFlow {
        val sensor = contador
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val oyente = object : SensorEventListener {
            override fun onSensorChanged(evento: SensorEvent) {
                trySend(evento.values[0].toLong())
            }

            override fun onAccuracyChanged(sensor: Sensor?, precision: Int) = Unit
        }
        receptor = oyente
        // SENSOR_DELAY_NORMAL basta: los pasos no necesitan alta frecuencia.
        gestor.registerListener(oyente, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose {
            gestor.unregisterListener(oyente)
            receptor = null
        }
    }

    actual fun detener() {
        receptor?.let { gestor.unregisterListener(it) }
        receptor = null
    }
}
