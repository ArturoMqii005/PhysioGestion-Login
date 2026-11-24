package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/** A quién le llega un anuncio de la clínica. */
@Serializable
enum class Destinatarios(val etiqueta: String) {
    PACIENTES("Pacientes"),
    PERSONAL("Personal de la clínica"),
    TODOS("Todos"),
}

/**
 * Anuncio general de la clínica («Cerramos el lunes festivo»). Llega como
 * notificación push y queda en la bandeja de avisos de cada persona.
 */
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

/** POST /api/v1/anuncios (recepción y administración). */
@Serializable
data class AnuncioSolicitud(val titulo: String, val texto: String, val destinatarios: Destinatarios)
