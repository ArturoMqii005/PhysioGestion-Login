package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

@Serializable
enum class EstadoCita { PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA }

/** Cita de la agenda del día (GET /api/v1/citas). */
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
