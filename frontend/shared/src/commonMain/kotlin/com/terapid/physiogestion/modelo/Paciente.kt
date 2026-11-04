package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/*
 * DTOs de pacientes y rutinas (fase 2). Copia de backend/.../modelos/Paciente.kt.
 */

@Serializable
data class PacienteResumen(
    val pacienteId: Int,
    val nombre: String,
    val correo: String? = null,
    val tieneCuenta: Boolean = false,
    val fisioterapeutaId: Int? = null,
    val fisioterapeuta: String? = null,
    val tieneRutina: Boolean = false,
    val metaFuncional: String? = null,
    val ultimaSesion: String? = null,
    val semaforo: Semaforo? = null,
    val cumplimientoPromedio: Int? = null,
    /** «2026-11-05T10:00». */
    val proximaCita: String? = null,
)

@Serializable
data class FichaPaciente(
    val resumen: PacienteResumen,
    val rutina: Rutina? = null,
    val sesionesRecientes: List<SesionTerapia> = emptyList(),
    val pruebasEscaleras: List<PruebaEscalerasRegistrada> = emptyList(),
    val citas: List<Cita> = emptyList(),
)

@Serializable
data class AsignacionSolicitud(val fisioterapeutaId: Int)

@Serializable
data class EjercicioAsignado(
    val idEjercicio: Int,
    val series: Int,
    val repeticiones: Int,
    val descansoSegundos: Int,
    val anguloObjetivo: Int? = null,
)

@Serializable
data class RutinaSolicitud(
    val metaFuncional: String,
    val umbralDolor: Int,
    val ejercicios: List<EjercicioAsignado>,
)
