package com.terapid.physiogestion.modelo

import kotlinx.serialization.Serializable

/*
 * DTOs del recurso /api/v1/sesiones y /api/v1/resultados-ejercicio.
 * Son copia de los del backend (backend/src/main/kotlin/modelos/SesionTerapia.kt):
 * mismos nombres de campo, porque el JSON se arma y se lee con esos nombres.
 */

/** Semáforo de evolución (RFN-47): VERDE cumple, AMARILLO cumplimiento bajo, ROJO dolor alto. */
@Serializable
enum class Semaforo { VERDE, AMARILLO, ROJO }

/** Detalle de un ejercicio dentro de la sesión. [verificado] = lo midieron los sensores. */
@Serializable
data class EjercicioRealizado(
    val idEjercicio: Int,
    val repeticionesRealizadas: Int,
    val anguloMaximo: Int? = null,
    val verificado: Boolean = false,
)

/** Cuerpo de POST /sesiones. No lleva id: lo asigna el servidor. */
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

/** Cuerpo de PUT /sesiones/{id}: se manda el recurso completo (la observación va aunque sea null). */
@Serializable
data class ReemplazarSesionSolicitud(
    val dolorAntes: Int,
    val dolorDespues: Int,
    val dificultad: Dificultad,
    val observacion: String?,
)

/**
 * Cuerpo de PATCH /sesiones/{id}. Los campos nulos tienen valor por defecto,
 * así el serializador no los escribe en el JSON y el servidor conserva el valor anterior.
 */
@Serializable
data class ModificarSesionSolicitud(
    val dolorDespues: Int? = null,
    val dificultad: Dificultad? = null,
    val observacion: String? = null,
)

/** Sesión tal como la devuelve el servidor. */
@Serializable
data class SesionTerapia(
    val id: Int,
    val pacienteId: Int,
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

/** Cuerpo de POST /resultados-ejercicio: solo el resumen, nunca las lecturas crudas. */
@Serializable
data class ResultadoEjercicioSolicitud(
    val idEjercicio: Int,
    val repeticiones: Int,
    val repeticionesObjetivo: Int,
    val anguloMaximo: Int,
    val anguloObjetivo: Int? = null,
    val duracionSegundos: Int,
)

/** Página del historial: las sesiones y el total que informa el encabezado X-Total-Count. */
data class PaginaSesiones(
    val sesiones: List<SesionTerapia>,
    val total: Int,
)

/** "2026-10-14T18:05:00" -> "14/10/2026 18:05" (formato de México, RI-13). */
fun fechaIsoLegible(iso: String): String {
    val fecha = iso.substringBefore('T')
    val hora = iso.substringAfter('T', "").take(5)
    val partes = fecha.split('-')
    if (partes.size != 3) return iso
    return "${partes[2]}/${partes[1]}/${partes[0]}" + if (hora.isNotEmpty()) " $hora" else ""
}
