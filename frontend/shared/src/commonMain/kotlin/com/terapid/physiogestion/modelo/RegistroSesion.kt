package com.terapid.physiogestion.modelo

/** Dificultad percibida por el paciente al terminar la sesión (RFN-28). */
enum class Dificultad(val etiqueta: String) {
    BAJA("Fácil"),
    MEDIA("Regular"),
    ALTA("Difícil"),
}

/**
 * MODELO: una sesión de ejercicios realizada por el paciente (RFN-29).
 * Se guarda en la base de datos local; [pendienteEnvio] indica que aun
 * no se ha enviado al servidor (cola de envío, RFX-03).
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
)
