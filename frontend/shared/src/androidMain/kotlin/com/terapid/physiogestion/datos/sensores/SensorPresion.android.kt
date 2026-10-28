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

/** Implementación ANDROID del barómetro (Sensor.TYPE_PRESSURE, en hPa). */
actual class SensorPresion actual constructor() {

    private val gestor: SensorManager =
        ContextoAndroid.aplicacion.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val barometro: Sensor? = gestor.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val receptores = mutableListOf<SensorEventListener>()

    actual val estaDisponible: Boolean get() = barometro != null

    actual fun presion(periodoMs: Int): Flow<LecturaPresion> = callbackFlow {
        val sensor = barometro
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val oyente = object : SensorEventListener {
            override fun onSensorChanged(evento: SensorEvent) {
                trySend(LecturaPresion(hPa = evento.values[0], marcaTiempo = evento.timestamp / 1_000_000))
            }

            override fun onAccuracyChanged(sensor: Sensor?, precision: Int) = Unit
        }
        synchronized(receptores) { receptores += oyente }
        gestor.registerListener(oyente, sensor, periodoMs * 1_000)
        awaitClose {
            gestor.unregisterListener(oyente)
            synchronized(receptores) { receptores -= oyente }
        }
    }

    actual fun detener() {
        synchronized(receptores) {
            receptores.forEach { gestor.unregisterListener(it) }
            receptores.clear()
        }
    }
}
