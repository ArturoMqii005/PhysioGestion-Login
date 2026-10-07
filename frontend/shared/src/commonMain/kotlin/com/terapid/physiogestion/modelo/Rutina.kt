package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/**
 * MODELO: rutina vigente del paciente (RFN-20).
 * Tiene la misma forma que el JSON de GET /api/v1/pacientes/{id}/rutina.
 */
@Serializable
data class Rutina(
    val id: Int,
    val pacienteId: Int,
    val paciente: String,
    val fisioterapeuta: String,
    val metaFuncional: String,
    /** Nivel de dolor (0 a 10) a partir del cual se sugiere suspender (RFX-09). */
    val umbralDolor: Int,
    val vigenteDesde: String,
    val ejercicios: List<Ejercicio>,
)

/** De dónde se obtuvo la rutina que se está mostrando. */
enum class OrigenDatos { SERVIDOR, ALMACENAMIENTO_LOCAL }

/** Rutina junto con su origen y la fecha en que se descargó. */
data class RutinaConOrigen(
    val rutina: Rutina,
    val origen: OrigenDatos,
    val actualizadaEn: Long,
)
