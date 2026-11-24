package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/**
 * Teléfono registrado para recibir notificaciones push (Firebase Cloud Messaging).
 * [token] es el identificador que FCM le da a cada instalación de la app.
 */
@Serializable
data class Dispositivo(
    val token: String,
    val usuarioId: Int,
    val plataforma: String,
    /** Milisegundos desde 1970. */
    val registrado: Long,
    val ultimoUso: Long,
    /** false después de cerrar sesión en ese teléfono: se conserva para no avisar «dispositivo nuevo» al volver a entrar. */
    val activo: Boolean = true,
)

/** POST /api/v1/dispositivos: la app manda su token al iniciar sesión y cuando FCM lo renueva. */
@Serializable
data class DispositivoSolicitud(val token: String, val plataforma: String = "ANDROID")

/** Respuesta: temas de FCM a los que la app se suscribe según el rol (anuncios de la clínica). */
@Serializable
data class DispositivoRegistrado(val temas: List<String>, val nuevo: Boolean)

@Serializable
enum class Destinatarios { PACIENTES, PERSONAL, TODOS }

/** Anuncio general de la clínica: llega a los teléfonos suscritos al tema y queda en la bandeja de avisos. */
@Serializable
data class Anuncio(
    val id: Int,
    val titulo: String,
    val texto: String,
    val destinatarios: Destinatarios,
    val creadoPor: String,
    /** Milisegundos desde 1970. */
    val creado: Long,
    /** Cuántas personas lo recibieron en su bandeja. */
    val personas: Int = 0,
)

/** POST /api/v1/anuncios (RECEPCIONISTA, ADMINISTRADOR). */
@Serializable
data class AnuncioSolicitud(val titulo: String, val texto: String, val destinatarios: Destinatarios)

/** Resultado de la revisión diaria de pacientes sin actividad. */
@Serializable
data class ResultadoRevision(val fisioterapeutasAvisados: Int, val pacientesSinActividad: Int)
