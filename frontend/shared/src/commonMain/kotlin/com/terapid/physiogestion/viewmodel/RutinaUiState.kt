package com.terapid.physiogestion.viewmodel

import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.EvaluacionSesion
import com.terapid.physiogestion.modelo.MedicionEjercicio
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.Rutina

/** Qué pasó con el envío de la sesión al servidor después de guardarla en el teléfono. */
enum class EstadoEnvio { ENVIANDO, ENVIADA, PENDIENTE }

/**
 * Todo lo que la vista necesita para dibujarse. Es inmutable: el ViewModel
 * crea una copia nueva en cada cambio y la vista solo lo lee.
 */
data class RutinaUiState(
    val cargando: Boolean = true,
    val mensajeError: String? = null,
    val rutina: Rutina? = null,
    val origen: OrigenDatos? = null,
    val actualizadaEn: Long? = null,
    /** true si la copia local tiene más días que Entorno.VIGENCIA_RUTINA_DIAS. */
    val copiaVencida: Boolean = false,
    val completados: Set<Int> = emptySet(),
    /** Última medición con sensores de hoy, por ejercicio. */
    val mediciones: Map<Int, MedicionEjercicio> = emptyMap(),
    val dolorAntes: Int? = null,
    val dolorDespues: Int? = null,
    val dificultad: Dificultad? = null,
    val observacion: String = "",
    val aviso: String? = null,
    val guardando: Boolean = false,
    val resultado: EvaluacionSesion? = null,
    val envio: EstadoEnvio? = null,
    val racha: Int = 0,
    val puntosTotales: Int = 0,
    /** Método, ruta y código de la última petición (solo se muestra en desarrollo). */
    val detalleTecnico: String? = null,
) {
    val totalEjercicios: Int get() = rutina?.ejercicios?.size ?: 0
    val avance: Float get() = if (totalEjercicios == 0) 0f else completados.size / totalEjercicios.toFloat()
}
