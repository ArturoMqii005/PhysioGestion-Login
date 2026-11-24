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
    /** Un paciente pidió cambiar su cita (al fisioterapeuta). */
    SOLICITUD_REAGENDA,
    /** Recepción aprobó o rechazó el cambio de cita que pidió el paciente. */
    SOLICITUD_RESUELTA,
    /** Entraron a la cuenta desde otro teléfono o cambió la contraseña. */
    SEGURIDAD,
    /** Anuncio general de la clínica. */
    ANUNCIO,
    /** Revisión diaria: pacientes del fisioterapeuta sin hacer su rutina. */
    PACIENTES_INACTIVOS,
    /** Un tipo que agregó el servidor después de esta versión de la app. */
    OTRO,
}

@Serializable
data class Aviso(
    val id: Int,
    val usuarioId: Int,
    /** Si el servidor manda un tipo que esta versión no conoce, queda como OTRO (coerceInputValues). */
    val tipo: TipoAviso = TipoAviso.OTRO,
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
    val urgente: Boolean get() = tipo == TipoAviso.DOLOR_ALTO || tipo == TipoAviso.SEGURIDAD
}

/** POST /api/v1/dispositivos: el token de Firebase de este teléfono. */
@Serializable
data class DispositivoSolicitud(val token: String, val plataforma: String = "ANDROID")

/** Temas de Firebase a los que el servidor suscribió el teléfono (anuncios por rol). */
@Serializable
data class DispositivoRegistrado(val temas: List<String> = emptyList(), val nuevo: Boolean = false)

@Serializable
data class BandejaAvisos(
    val avisos: List<Aviso> = emptyList(),
    val sinLeer: Int = 0,
    val ultimoId: Int = 0,
)

@Serializable
data class MarcarLeidosSolicitud(val ids: List<Int> = emptyList(), val todos: Boolean = false)
