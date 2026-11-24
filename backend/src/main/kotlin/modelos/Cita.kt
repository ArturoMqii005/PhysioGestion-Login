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
    /** Veces que el propio paciente la movió desde la app (máximo 2; después, solicitud). */
    val reagendasPaciente: Int = 0,
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
    /** Por qué se mueve. El fisioterapeuta debe explicarlo cuando no puede atender. */
    val nota: String? = null,
)

/** POST /api/v1/citas/{id}/reagendar: el paciente cambia su cita a otra hora libre de su fisioterapeuta. */
@Serializable
data class ReagendarSolicitud(val fecha: String, val hora: String)

@Serializable
enum class EstadoSolicitud { PENDIENTE, APROBADA, RECHAZADA }

/**
 * Solicitud de reagenda: el paciente propone otro día u hora (o solo explica qué
 * le acomoda) y recepción la aprueba o la rechaza.
 */
@Serializable
data class SolicitudReagenda(
    val id: Int,
    val citaId: Int,
    val pacienteId: Int,
    val paciente: String,
    val fisioterapeutaId: Int,
    val fisioterapeuta: String,
    val fechaActual: String,
    val horaActual: String,
    val fechaPropuesta: String? = null,
    val horaPropuesta: String? = null,
    val mensaje: String,
    val estado: EstadoSolicitud = EstadoSolicitud.PENDIENTE,
    /** Fecha y hora ISO en que se creó. */
    val creada: String,
    /** Nota de recepción al aprobar o rechazar. */
    val respuesta: String? = null,
    val atendidaPor: String? = null,
)

/** POST /api/v1/citas/{id}/solicitudes: lo que propone el paciente. */
@Serializable
data class NuevaSolicitudReagenda(
    val fechaPropuesta: String? = null,
    val horaPropuesta: String? = null,
    val mensaje: String,
)

/**
 * POST /api/v1/solicitudes-reagenda/{id}/resolver. Al aprobar se usa la fecha y hora
 * propuestas, o las que elija recepción si las manda.
 */
@Serializable
data class ResolverSolicitud(
    val aprobar: Boolean,
    val fecha: String? = null,
    val hora: String? = null,
    val fisioterapeutaId: Int? = null,
    val nota: String? = null,
)

/** POST /api/v1/citas/{id}/estado: confirmar, cancelar o marcar asistencia. */
@Serializable
data class EstadoCitaSolicitud(val estado: EstadoCita, val nota: String? = null)

/** Fisioterapeuta disponible para agendar. */
@Serializable
data class FisioterapeutaResumen(val id: Int, val nombre: String)
