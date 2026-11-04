package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/** Fisioterapeuta responsable de cada paciente (PAC-04). */
@Serializable
data class Asignacion(val pacienteId: Int, val fisioterapeutaId: Int)

/** Fila de la lista de pacientes (PAC-02). */
@Serializable
data class PacienteResumen(
    val pacienteId: Int,
    val nombre: String,
    val correo: String? = null,
    /** false si todavía no se registra con su invitación. */
    val tieneCuenta: Boolean,
    val fisioterapeutaId: Int? = null,
    val fisioterapeuta: String? = null,
    val tieneRutina: Boolean,
    val metaFuncional: String? = null,
    val ultimaSesion: String? = null,
    val semaforo: Semaforo? = null,
    val cumplimientoPromedio: Int? = null,
    val proximaCita: String? = null,
)

/** Ficha del paciente (PAC-03): resumen, rutina vigente, sesiones, escaleras y citas. */
@Serializable
data class FichaPaciente(
    val resumen: PacienteResumen,
    val rutina: Rutina? = null,
    val sesionesRecientes: List<SesionTerapia> = emptyList(),
    val pruebasEscaleras: List<PruebaEscalerasRegistrada> = emptyList(),
    val citas: List<Cita> = emptyList(),
)

/** PATCH /api/v1/pacientes/{id}/fisioterapeuta. */
@Serializable
data class AsignacionSolicitud(val fisioterapeutaId: Int)

/** Ejercicio elegido del catálogo con la dosis que indica el fisioterapeuta. */
@Serializable
data class EjercicioAsignado(
    val idEjercicio: Int,
    val series: Int,
    val repeticiones: Int,
    val descansoSegundos: Int,
    val anguloObjetivo: Int? = null,
)

/** PUT /api/v1/pacientes/{id}/rutina (FISIOTERAPEUTA): crea o reemplaza la rutina (RUT-08). */
@Serializable
data class RutinaSolicitud(
    val metaFuncional: String,
    val umbralDolor: Int,
    val ejercicios: List<EjercicioAsignado>,
)
