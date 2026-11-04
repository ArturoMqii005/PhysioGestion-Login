package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/** NO_ASISTIO: el fisioterapeuta registra que el paciente no llegó (cuenta para el seguimiento). */
@Serializable
enum class EstadoCita { PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA, NO_ASISTIO }

/** Cita de la agenda. [fecha] en formato ISO («2026-11-04») y [hora] «09:30». */
@Serializable
data class Cita(
    val id: Int,
    val hora: String,
    val paciente: String,
    val fisioterapeutaId: Int,
    val fisioterapeuta: String,
    val motivo: String,
    val estado: EstadoCita,
    val fecha: String = "",
    /** null si la persona todavía no tiene expediente en la app (por ejemplo, primera consulta). */
    val pacienteId: Int? = null,
    val duracionMinutos: Int = 50,
    /** Motivo de cancelación o nota de la reprogramación. */
    val nota: String? = null,
)

/** POST /api/v1/citas (RECEPCIONISTA, ADMINISTRADOR). */
@Serializable
data class CitaSolicitud(
    val paciente: String,
    val pacienteId: Int? = null,
    val fisioterapeutaId: Int,
    val fecha: String,
    val hora: String,
    val duracionMinutos: Int = 50,
    val motivo: String,
)

/** PATCH /api/v1/citas/{id}: reprogramar. Solo cambia lo que se manda. */
@Serializable
data class ReprogramarCitaSolicitud(
    val fecha: String? = null,
    val hora: String? = null,
    val fisioterapeutaId: Int? = null,
    val duracionMinutos: Int? = null,
    val motivo: String? = null,
)

/** POST /api/v1/citas/{id}/estado: confirmar, cancelar o marcar asistencia. */
@Serializable
data class EstadoCitaSolicitud(val estado: EstadoCita, val nota: String? = null)

/** Fisioterapeuta disponible para agendar. */
@Serializable
data class FisioterapeutaResumen(val id: Int, val nombre: String)
