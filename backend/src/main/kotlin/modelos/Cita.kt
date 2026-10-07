package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

@Serializable
enum class EstadoCita { PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA }

/** Cita de la agenda del día (resources/datos/citas.json). */
@Serializable
data class Cita(
    val id: Int,
    val hora: String,
    val paciente: String,
    val fisioterapeutaId: Int,
    val fisioterapeuta: String,
    val motivo: String,
    val estado: EstadoCita,
)
