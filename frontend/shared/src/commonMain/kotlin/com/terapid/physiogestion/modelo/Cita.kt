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
    /** Veces que el paciente la movió él mismo (máximo 2). */
    val reagendasPaciente: Int = 0,
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
    /** Por qué la mueve el fisioterapeuta («Me enfermé»); le llega al paciente. */
    val nota: String? = null,
)

/** POST /citas/{id}/reagendar: el paciente elige otra hora libre (más de 24 h antes, máximo 2 veces). */
@Serializable
data class ReagendarSolicitud(val fecha: String, val hora: String)

/** POST /citas/{id}/solicitudes: con menos de 24 h, el paciente pide el cambio y recepción decide. */
@Serializable
data class NuevaSolicitudReagenda(val fechaPropuesta: String? = null, val horaPropuesta: String? = null, val mensaje: String)

/**
 * Solicitud de cambio de cita que manda el paciente cuando ya no puede moverla él
 * mismo. Recepción y administración la aprueban (la cita se mueve) o la rechazan.
 */
@Serializable
data class SolicitudReagenda(
    val id: Int,
    val citaId: Int,
    /** PENDIENTE, APROBADA o RECHAZADA. */
    val estado: String = "PENDIENTE",
    val pacienteId: Int = 0,
    val paciente: String = "",
    val fisioterapeutaId: Int = 0,
    val fisioterapeuta: String = "",
    val fechaActual: String = "",
    val horaActual: String = "",
    val fechaPropuesta: String? = null,
    val horaPropuesta: String? = null,
    val mensaje: String = "",
    /** Fecha y hora ISO en que el paciente la mandó. */
    val creada: String = "",
    /** Nota de recepción al aprobar o rechazar, y quién la atendió. */
    val respuesta: String? = null,
    val atendidaPor: String? = null,
) {
    val pendiente: Boolean get() = estado == "PENDIENTE"
}

/** POST /solicitudes-reagenda/{id}/resolver: aprobar (con la fecha, hora y fisioterapeuta nuevos) o rechazar con motivo. */
@Serializable
data class ResolverSolicitud(
    val aprobar: Boolean,
    val fecha: String? = null,
    val hora: String? = null,
    val fisioterapeutaId: Int? = null,
    val nota: String? = null,
)

@Serializable
data class EstadoCitaSolicitud(val estado: EstadoCita, val nota: String? = null)

@Serializable
data class FisioterapeutaResumen(val id: Int, val nombre: String)
