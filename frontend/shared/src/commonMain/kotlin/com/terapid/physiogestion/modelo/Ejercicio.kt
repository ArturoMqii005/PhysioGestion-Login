package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/**
 * MODELO: estructura de un ejercicio asignado por el fisioterapeuta.
 * Se recibe en JSON desde la API y se muestra en la vista.
 */
@Serializable
data class Ejercicio(
    val id: Int,
    val nombre: String,
    val indicaciones: String,
    val series: Int,
    val repeticiones: Int,
    val descansoSegundos: Int,
    val zonaCorporal: String,
)
