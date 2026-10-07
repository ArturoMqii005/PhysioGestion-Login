package com.terapid.physiogestion.viewmodel

import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.EvaluacionSesion
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.modelo.Rutina

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
    val completados: Set<Int> = emptySet(),
    val dolorAntes: Int? = null,
    val dolorDespues: Int? = null,
    val dificultad: Dificultad? = null,
    val observacion: String = "",
    val aviso: String? = null,
    val guardando: Boolean = false,
    val resultado: EvaluacionSesion? = null,
    val historial: List<RegistroSesion> = emptyList(),
    val puntosTotales: Int = 0,
) {
    val totalEjercicios: Int get() = rutina?.ejercicios?.size ?: 0
    val avance: Float get() = if (totalEjercicios == 0) 0f else completados.size / totalEjercicios.toFloat()
}
