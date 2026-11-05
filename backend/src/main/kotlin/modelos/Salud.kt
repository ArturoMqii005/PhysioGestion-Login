package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/** Versión de la API que se publica en GET /api/v1/salud. Se sube con cada despliegue. */
const val VERSION_API = "1.5.0"

/**
 * Respuesta de GET /api/v1/salud: confirma que el servidor está vivo y
 * dice qué versión corre y en qué entorno. En Railway, [commit] es el
 * commit de GitHub que se desplegó; en la computadora local es null.
 */
@Serializable
data class EstadoServidor(
    val estado: String,
    val servicio: String,
    val version: String,
    val entorno: String,
    val region: String? = null,
    val commit: String? = null,
    val java: String,
    val segundosActivo: Long,
    /** Dónde se guardan los datos: «memoria» (se pierden al reiniciar) o «postgresql» (SRV-08). */
    val baseDatos: String,
    /** true si los códigos llegan por correo (Brevo); false si solo se escriben en el registro. */
    val correoConfigurado: Boolean,
)
