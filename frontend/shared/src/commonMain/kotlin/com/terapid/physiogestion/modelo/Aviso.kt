package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/*
 * Avisos que genera el servidor (GET /api/v1/avisos). Copia de
 * backend/.../modelos/Aviso.kt: mismos nombres de campos y del enum.
 */

@Serializable
enum class TipoAviso {
    /** Un paciente terminó su sesión con dolor alto (NOT-03). */
    DOLOR_ALTO,
    CITA_NUEVA,
    CITA_REPROGRAMADA,
    CITA_CANCELADA,
    RUTINA_ACTUALIZADA,
    PACIENTE_ASIGNADO,
}

@Serializable
data class Aviso(
    val id: Int,
    val usuarioId: Int,
    val tipo: TipoAviso,
    val titulo: String,
    val mensaje: String,
    /** Milisegundos desde 1970; se muestra con la hora del teléfono. */
    val creado: Long,
    val leido: Boolean = false,
    val pacienteId: Int? = null,
    val sesionId: Int? = null,
    val citaId: Int? = null,
) {
    /** true si es de los que no pueden esperar (se notifican en el canal urgente). */
    val urgente: Boolean get() = tipo == TipoAviso.DOLOR_ALTO
}

@Serializable
data class BandejaAvisos(
    val avisos: List<Aviso> = emptyList(),
    val sinLeer: Int = 0,
    val ultimoId: Int = 0,
)

@Serializable
data class MarcarLeidosSolicitud(val ids: List<Int> = emptyList(), val todos: Boolean = false)
