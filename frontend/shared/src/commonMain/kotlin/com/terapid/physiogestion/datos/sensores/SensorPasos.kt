package com.terapid.physiogestion.datos.sensores

import kotlinx.coroutines.flow.Flow

/**
 * CONTADOR DE PASOS (RFN-39).
 *
 * Es un sensor compuesto: lo calcula el sistema con el acelerómetro en un
 * coprocesador de bajo consumo, así que gasta mucha menos batería que contar
 * los pasos dentro de la app y sigue contando con la pantalla apagada.
 * En Android exige el permiso ACTIVITY_RECOGNITION.
 */
expect class SensorPasos() {

    /** false cuando el dispositivo no tiene contador de pasos. */
    val estaDisponible: Boolean

    /**
     * true si [pasos] ya cuenta desde las 00:00 (iOS). En Android el valor se
     * acumula desde que se encendió el teléfono, y la app calcula la
     * diferencia contra la primera lectura del día.
     */
    val cuentaDesdeInicioDelDia: Boolean

    /** Total de pasos según el sistema. */
    fun pasos(): Flow<Long>

    fun detener()
}
