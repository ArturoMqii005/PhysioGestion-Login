package com.terapid.physiogestion.modelos

import kotlinx.serialization.Serializable

/*
 * DTOs del recurso /api/v1/sesiones: las sesiones de ejercicios que el
 * paciente hace en casa. Son copia de los de la app (frontend/.../modelo/SesionTerapia.kt).
 */

/** Dificultad percibida por el paciente (RFN-28). Mismos nombres que en la app. */
@Serializable
enum class Dificultad { BAJA, MEDIA, ALTA }

/** Semáforo de evolución (RFN-47): VERDE cumple, AMARILLO cumplimiento bajo, ROJO dolor alto. */
@Serializable
enum class Semaforo { VERDE, AMARILLO, ROJO }

/**
 * Detalle de cada ejercicio dentro de la sesión. [verificado] distingue lo
 * que midieron los sensores de lo que el paciente solo marcó como hecho.
 */
@Serializable
data class EjercicioRealizado(
    val idEjercicio: Int,
    val repeticionesRealizadas: Int,
    val anguloMaximo: Int? = null,
    val verificado: Boolean = false,
)

/** Cuerpo de POST /api/v1/sesiones. No lleva id: el servidor lo asigna. */
@Serializable
data class CrearSesionSolicitud(
    val idRutina: Int,
    val fechaHoraInicio: String,
    val fechaHoraFin: String,
    val dolorAntes: Int,
    val dolorDespues: Int,
    val dificultad: Dificultad,
    val observacion: String? = null,
    val ejercicios: List<EjercicioRealizado>,
)

/** Cuerpo de PUT /api/v1/sesiones/{id}: reemplaza TODOS los datos editables. */
@Serializable
data class ReemplazarSesionSolicitud(
    val dolorAntes: Int,
    val dolorDespues: Int,
    val dificultad: Dificultad,
    val observacion: String?,
)

/** Cuerpo de PATCH /api/v1/sesiones/{id}: solo cambian los campos que no vienen nulos. */
@Serializable
data class ModificarSesionSolicitud(
    val dolorDespues: Int? = null,
    val dificultad: Dificultad? = null,
    val observacion: String? = null,
)

/** Sesión tal como la guarda y la devuelve el servidor. */
@Serializable
data class SesionTerapia(
    val id: Int,
    val pacienteId: Int,
    /** Nombre del paciente, para que el fisioterapeuta sepa de quién es la sesión. */
    val paciente: String = "",
    val idRutina: Int,
    val fechaHoraInicio: String,
    val fechaHoraFin: String,
    val dolorAntes: Int,
    val dolorDespues: Int,
    val dificultad: Dificultad,
    val observacion: String? = null,
    val ejercicios: List<EjercicioRealizado>,
    val porcentajeCumplimiento: Int,
    val semaforo: Semaforo,
)

/**
 * Cuerpo de POST /api/v1/resultados-ejercicio. Solo viaja el resumen de la
 * medición con sensores, nunca las lecturas crudas del acelerómetro.
 */
@Serializable
data class ResultadoEjercicioSolicitud(
    val idEjercicio: Int,
    val repeticiones: Int,
    val repeticionesObjetivo: Int,
    val anguloMaximo: Int,
    val anguloObjetivo: Int? = null,
    val duracionSegundos: Int,
)

/** Resultado de sensores ya registrado en el servidor. */
@Serializable
data class ResultadoEjercicio(
    val id: Int,
    val pacienteId: Int,
    val idEjercicio: Int,
    val repeticiones: Int,
    val repeticionesObjetivo: Int,
    val anguloMaximo: Int,
    val anguloObjetivo: Int? = null,
    val duracionSegundos: Int,
    val cumpleObjetivo: Boolean,
)
