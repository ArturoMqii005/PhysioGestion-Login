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
 * Implementación ANDROID de los sensores inerciales. Android los administra
 * con SensorManager y entrega cada lectura a un receptor; callbackFlow
 * convierte ese receptor en el Flow que pide el contrato común.
 */
actual class SensorMovimiento actual constructor() {

    private val gestor: SensorManager =
        ContextoAndroid.aplicacion.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val sensorAcelerometro: Sensor? = gestor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val sensorGiroscopio: Sensor? = gestor.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    /** Receptores activos, para poder darlos de baja desde detener(). */
    private val receptores = mutableListOf<SensorEventListener>()

    actual val hayAcelerometro: Boolean get() = sensorAcelerometro != null
    actual val hayGiroscopio: Boolean get() = sensorGiroscopio != null

    actual fun acelerometro(periodoMs: Int): Flow<LecturaEje> = emitirDe(sensorAcelerometro, periodoMs)

    actual fun giroscopio(periodoMs: Int): Flow<LecturaEje> = emitirDe(sensorGiroscopio, periodoMs)

    /**
     * Registra un receptor y emite cada lectura. Dos detalles:
     *  - registerListener recibe el periodo en MICROsegundos (de ahí el × 1000).
     *  - awaitClose da de baja el receptor cuando se cancela la corrutina: al
     *    cerrar la pantalla, el ViewModel se destruye y el sensor se apaga solo.
     */
    private fun emitirDe(sensor: Sensor?, periodoMs: Int): Flow<LecturaEje> = callbackFlow {
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val oyente = object : SensorEventListener {
            override fun onSensorChanged(evento: SensorEvent) {
                trySend(
                    LecturaEje(
                        x = evento.values[0],
                        y = evento.values[1],
                        z = evento.values[2],
                        marcaTiempo = evento.timestamp / 1_000_000, // nanosegundos desde el arranque -> ms
                    )
                )
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
