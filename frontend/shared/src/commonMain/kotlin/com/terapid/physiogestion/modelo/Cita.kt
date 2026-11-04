package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/** NO_ASISTIO: el fisioterapeuta registra que el paciente no llegó. */
@Serializable
enum class EstadoCita { PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA, NO_ASISTIO }

/** Cita de la agenda (GET /api/v1/citas). [fecha] «2026-11-04», [hora] «09:30». */
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
    val pacienteId: Int? = null,
    val duracionMinutos: Int = 50,
    val nota: String? = null,
) {
    /** Todavía se puede confirmar, reprogramar, cancelar o atender. */
    val abierta: Boolean get() = estado == EstadoCita.PENDIENTE || estado == EstadoCita.CONFIRMADA

    /** «09:30 – 10:20». */
    val horario: String get() = "$hora – ${sumarMinutos(hora, duracionMinutos)}"
}

/** «09:30» + 50 → «10:20». */
fun sumarMinutos(hora: String, minutos: Int): String {
    val partes = hora.split(':')
    val total = (partes.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (partes.getOrNull(1)?.toIntOrNull() ?: 0) + minutos
    return "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
}

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

@Serializable
data class ReprogramarCitaSolicitud(
    val fecha: String? = null,
    val hora: String? = null,
    val fisioterapeutaId: Int? = null,
    val duracionMinutos: Int? = null,
    val motivo: String? = null,
)

@Serializable
data class EstadoCitaSolicitud(val estado: EstadoCita, val nota: String? = null)

@Serializable
data class FisioterapeutaResumen(val id: Int, val nombre: String)
