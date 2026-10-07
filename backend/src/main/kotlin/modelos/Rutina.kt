package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/** Ejercicio de una rutina. Mismos campos que el modelo Ejercicio de la app. */
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

/** Rutina vigente de un paciente (resources/datos/rutinas.json). */
@Serializable
data class Rutina(
    val id: Int,
    val pacienteId: Int,
    val paciente: String,
    val fisioterapeuta: String,
    val metaFuncional: String,
    val umbralDolor: Int,
    val vigenteDesde: String,
    val ejercicios: List<Ejercicio>,
)
