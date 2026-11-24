package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/*
 * AVISOS (fase «Avisos» del inventario: NOT-01..04).
 *
 * El servidor guarda cada aviso para quien debe recibirlo y lo manda como push
 * con Firebase Cloud Messaging (PushServicio). La app también los consulta con
 * GET /avisos: en pantalla cada 30 s y, en Android, en segundo plano con
 * WorkManager, por si la push no llegó.
 */

/** Por qué se generó el aviso. Mismos nombres que en la app. */
@Serializable
enum class TipoAviso {
    /** Un paciente terminó su sesión con dolor igual o mayor a su límite (NOT-03). */
    DOLOR_ALTO,

    /** Recepción agendó una cita para el paciente. */
    CITA_NUEVA,

    /** La cita cambió de día u hora. */
    CITA_REPROGRAMADA,

    /** Se canceló una cita (al paciente si la canceló la clínica; al personal si la canceló el paciente). */
    CITA_CANCELADA,

    /** El fisioterapeuta modificó la rutina del paciente. */
    RUTINA_ACTUALIZADA,

    /** Se le asignó un paciente nuevo al fisioterapeuta. */
    PACIENTE_ASIGNADO,

    /** Un paciente pidió cambiar su cita (a recepción y a su fisioterapeuta). */
    SOLICITUD_REAGENDA,

    /** Recepción aprobó o rechazó la solicitud de cambio del paciente. */
    SOLICITUD_RESUELTA,

    /** Inicio de sesión en un dispositivo nuevo o cambio de contraseña. */
    SEGURIDAD,

    /** Anuncio general de la clínica (también llega por tema de Firebase). */
    ANUNCIO,

    /** Revisión diaria: pacientes del fisioterapeuta sin sesiones en 3 días o más. */
    PACIENTES_INACTIVOS,
}

@Serializable
data class Aviso(
    val id: Int,
    /** Usuario que lo recibe. */
    val usuarioId: Int,
    val tipo: TipoAviso,
    val titulo: String,
    val mensaje: String,
    /** Momento de creación en milisegundos desde 1970 (la app lo muestra en su hora local). */
    val creado: Long,
    val leido: Boolean = false,
    val pacienteId: Int? = null,
    val sesionId: Int? = null,
    val citaId: Int? = null,
)

/** Respuesta de GET /avisos: los más recientes primero. */
@Serializable
data class BandejaAvisos(
    val avisos: List<Aviso>,
    val sinLeer: Int,
    /** Id más alto que tiene el usuario (0 si no tiene avisos); la app lo usa para saber qué es nuevo. */
    val ultimoId: Int,
)

/** Cuerpo de POST /avisos/leidos: una lista de ids o todos. */
@Serializable
data class MarcarLeidosSolicitud(val ids: List<Int> = emptyList(), val todos: Boolean = false)
