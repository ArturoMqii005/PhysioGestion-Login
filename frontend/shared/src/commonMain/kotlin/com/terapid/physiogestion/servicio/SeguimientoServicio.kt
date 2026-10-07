package com.terapid.physiogestion.servicio

import com.terapid.physiogestion.modelo.AlertaDolor
import com.terapid.physiogestion.modelo.EvaluacionSesion

/** Catálogo de puntos (RFN-41). Se deja configurable, como pide el requisito. */
data class ReglasPuntos(
    val porEjercicio: Int = 10,
    val bonoRutinaCompleta: Int = 20,
    /** Tope diario: hacer más de lo indicado no da más puntos (RFN-42). */
    val topeDiario: Int = 60,
)

/**
 * SERVICIO: reglas de negocio del seguimiento de la sesión.
 *
 * Es código puro de Kotlin (sin pantallas, sin red, sin base de datos),
 * por eso se comparte al 100 % entre Android, iOS y escritorio y se puede
 * probar con pruebas unitarias.
 */
class SeguimientoServicio(private val reglas: ReglasPuntos = ReglasPuntos()) {

    /** Porcentaje de cumplimiento respecto de lo asignado (RFN-30). */
    fun calcularCumplimiento(realizados: Int, asignados: Int): Int {
        require(realizados >= 0 && asignados >= 0) { "Los conteos no pueden ser negativos" }
        if (asignados == 0) return 0
        return realizados.coerceAtMost(asignados) * 100 / asignados
    }

    /** Escala numérica estándar de 0 a 10, sin escalas alternativas (RD-03). */
    fun esNivelDolorValido(nivel: Int): Boolean = nivel in 0..10

    /**
     * Revisa un nivel de dolor contra el umbral del paciente y contra el
     * registro anterior (RFX-09). Nunca sugiere continuar (RD-08).
     */
    fun evaluarDolor(dolorActual: Int, umbral: Int, dolorAnterior: Int?): AlertaDolor? {
        val motivo = when {
            dolorActual >= umbral ->
                "Registraste un dolor de $dolorActual/10, igual o mayor al límite de $umbral/10 que indicó tu fisioterapeuta."
            dolorAnterior != null && dolorActual - dolorAnterior >= 3 ->
                "Tu dolor subió de $dolorAnterior/10 a $dolorActual/10 respecto de tu registro anterior."
            else -> return null
        }
        return AlertaDolor(motivo = motivo, recomendacion = RECOMENDACION)
    }

    /** Puntos de la sesión respetando el tope diario (RFN-41, RFN-42). */
    fun calcularPuntos(realizados: Int, asignados: Int, puntosPreviosDelDia: Int): Int {
        val completa = asignados > 0 && realizados >= asignados
        val base = realizados.coerceAtMost(asignados) * reglas.porEjercicio +
            if (completa) reglas.bonoRutinaCompleta else 0
        val disponibles = (reglas.topeDiario - puntosPreviosDelDia).coerceAtLeast(0)
        return base.coerceAtMost(disponibles)
    }

    /**
     * Evalúa la sesión completa. El dolor de antes se compara con la sesión
     * anterior y el de después con el de antes de esta misma sesión.
     */
    fun evaluarSesion(
        realizados: Int,
        asignados: Int,
        dolorAntes: Int,
        dolorDespues: Int,
        umbral: Int,
        dolorSesionAnterior: Int?,
        puntosPreviosDelDia: Int,
    ): EvaluacionSesion {
        require(esNivelDolorValido(dolorAntes) && esNivelDolorValido(dolorDespues)) {
            "El dolor debe estar entre 0 y 10"
        }
        val alerta = evaluarDolor(dolorDespues, umbral, dolorAntes)
            ?: evaluarDolor(dolorAntes, umbral, dolorSesionAnterior)
        return EvaluacionSesion(
            cumplimiento = calcularCumplimiento(realizados, asignados),
            puntos = calcularPuntos(realizados, asignados, puntosPreviosDelDia),
            alerta = alerta,
        )
    }

    companion object {
        const val RECOMENDACION =
            "Te recomendamos suspender los ejercicios por hoy y comunicarte con la clínica."
    }
}
