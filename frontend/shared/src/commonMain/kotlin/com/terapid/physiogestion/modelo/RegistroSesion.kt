package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/** Dificultad percibida por el paciente al terminar la sesión (RFN-28). */
@Serializable
enum class Dificultad(val etiqueta: String) {
    BAJA("Fácil"),
    MEDIA("Regular"),
    ALTA("Difícil"),
}

/**
 * MODELO: una sesión de ejercicios realizada por el paciente (RFN-29).
 * Se guarda primero en la base de datos local; [pendienteEnvio] indica que
 * aún no llega al servidor (cola de envío, RFX-03) y [servidorId] es el id
 * que asignó el backend cuando el POST tuvo éxito.
 */
data class RegistroSesion(
    val id: Long = 0,
    val rutinaId: Int,
    val inicioMillis: Long,
    val finMillis: Long,
    val claveDia: String,
    val ejerciciosRealizados: List<Int>,
    val ejerciciosAsignados: Int,
    val cumplimiento: Int,
    val dolorAntes: Int,
    val dolorDespues: Int,
    val dificultad: Dificultad,
    val observacion: String,
    val puntos: Int,
    val alertaDolor: Boolean,
    val pendienteEnvio: Boolean = true,
    val servidorId: Int? = null,
    /** Repeticiones y ángulo de cada ejercicio (lo medido con sensores va marcado como verificado). */
    val detalle: List<EjercicioRealizado> = emptyList(),
)
