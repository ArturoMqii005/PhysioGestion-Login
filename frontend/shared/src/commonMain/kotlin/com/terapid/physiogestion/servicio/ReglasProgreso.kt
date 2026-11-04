package com.terapid.physiogestion.servicio

import kotlin.math.roundToInt

/** Dolor al terminar una sesión, para la gráfica de evolución. [fecha] «2026-11-04». */
data class PuntoDolor(val fecha: String, val dolor: Int)

/** Ritmo de una prueba de escaleras (metros por minuto). */
data class PuntoRitmo(val fecha: String, val metrosPorMinuto: Float)

/** Cómo se pinta cada día del calendario de constancia. */
enum class EstadoDia { HECHO, FALTO, HOY_HECHO, HOY_PENDIENTE, FUTURO }

/**
 * Reglas de la pantalla «Tu progreso» (FIS-06 del lado del paciente). Sin
 * dependencias de plataforma para probarlas en la computadora.
 */
object ReglasProgreso {
    const val MAXIMO_PUNTOS = 12
    const val SEMANAS_CALENDARIO = 4

    /** Las últimas [maximo] sesiones en orden cronológico. [sesiones]: (fecha y hora ISO, dolor después). */
    fun serieDolor(sesiones: List<Pair<String, Int>>, maximo: Int = MAXIMO_PUNTOS): List<PuntoDolor> =
        sesiones.sortedBy { it.first }.takeLast(maximo).map { (f, d) -> PuntoDolor(f.take(10), d.coerceIn(0, 10)) }

    /** «Bajó de 7 a 3 en 10 sesiones», «Subió…», «Se mantiene en 4…». */
    fun textoDolor(serie: List<PuntoDolor>): String = when {
        serie.isEmpty() -> "Registra tu primera sesión para ver cómo cambia tu dolor."
        serie.size == 1 -> "Primera sesión: terminaste con dolor ${serie[0].dolor} de 10."
        serie.last().dolor < serie.first().dolor -> "Bajó de ${serie.first().dolor} a ${serie.last().dolor} en ${serie.size} sesiones"
        serie.last().dolor > serie.first().dolor -> "Subió de ${serie.first().dolor} a ${serie.last().dolor} en ${serie.size} sesiones"
        else -> "Se mantiene en ${serie.last().dolor} en ${serie.size} sesiones"
    }

    /** Las últimas [maximo] pruebas de escaleras en orden cronológico. */
    fun serieRitmo(pruebas: List<PuntoRitmo>, maximo: Int = 6): List<PuntoRitmo> =
        pruebas.filter { it.metrosPorMinuto > 0f }.sortedBy { it.fecha }.takeLast(maximo)

    /** Mejora porcentual del ritmo entre la primera y la última prueba; null si hay menos de dos. */
    fun mejoraRitmo(serie: List<PuntoRitmo>): Int? {
        if (serie.size < 2 || serie.first().metrosPorMinuto <= 0f) return null
        return ((serie.last().metrosPorMinuto - serie.first().metrosPorMinuto) / serie.first().metrosPorMinuto * 100f).roundToInt()
    }

    /**
     * Calendario de las últimas [SEMANAS_CALENDARIO] semanas, de lunes a domingo,
     * terminando en la semana de [hoy]. Los días después de hoy salen como FUTURO.
     */
    fun calendario(diasConSesion: Set<String>, hoy: String): List<EstadoDia> {
        val lunes = ReglasAgenda.sumarDias(hoy, -ReglasAgenda.diaSemana(hoy))
        val inicio = ReglasAgenda.sumarDias(lunes, -7 * (SEMANAS_CALENDARIO - 1))
        return (0 until 7 * SEMANAS_CALENDARIO).map { i ->
            val dia = ReglasAgenda.sumarDias(inicio, i)
            when {
                dia > hoy -> EstadoDia.FUTURO
                dia == hoy -> if (dia in diasConSesion) EstadoDia.HOY_HECHO else EstadoDia.HOY_PENDIENTE
                dia in diasConSesion -> EstadoDia.HECHO
                else -> EstadoDia.FALTO
            }
        }
    }

    /** Días con sesión y días transcurridos del calendario (para «18 de 24 días»). */
    fun constancia(calendario: List<EstadoDia>): Pair<Int, Int> {
        val hechos = calendario.count { it == EstadoDia.HECHO || it == EstadoDia.HOY_HECHO }
        val transcurridos = calendario.count { it != EstadoDia.FUTURO }
        return hechos to transcurridos
    }
}
