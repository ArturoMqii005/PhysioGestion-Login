package com.terapid.physiogestion.servicio

import com.terapid.physiogestion.modelo.Ejercicio
import com.terapid.physiogestion.modelo.EjercicioAsignado
import com.terapid.physiogestion.modelo.RutinaSolicitud

/**
 * Fechas de la agenda sin depender de una biblioteca de calendario: las fechas
 * viajan como texto ISO («2026-11-04») y aquí se suman días y se calcula el día
 * de la semana con aritmética de calendario gregoriano.
 */
object ReglasAgenda {
    private val DIAS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    val DURACIONES = listOf(30, 45, 50, 60, 90)

    /** Días desde el 1 de enero de 1970 (para sumar y comparar fechas). */
    fun diaJuliano(iso: String): Long {
        val (a, m, d) = iso.split('-').map { it.toInt() }
        val y = if (m <= 2) a - 1 else a
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    fun desdeJuliano(dias: Long): String {
        val z = dias + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    fun sumarDias(iso: String, dias: Int): String = desdeJuliano(diaJuliano(iso) + dias)

    /** 0 = lunes … 6 = domingo. */
    fun diaSemana(iso: String): Int = (((diaJuliano(iso) + 3) % 7 + 7) % 7).toInt()

    /** Los próximos [cuantos] días en que abre la clínica (sin domingos), empezando por [desde]. */
    fun diasHabiles(desde: String, cuantos: Int): List<String> =
        generateSequence(desde) { sumarDias(it, 1) }.filter { diaSemana(it) != 6 }.take(cuantos).toList()

    /** «Hoy», «Mañana», «Ayer» o «Jue 06/11». */
    fun etiquetaDia(iso: String, hoy: String): String = when (diaJuliano(iso) - diaJuliano(hoy)) {
        0L -> "Hoy"
        1L -> "Mañana"
        -1L -> "Ayer"
        else -> "${DIAS[diaSemana(iso)]} ${iso.substring(8, 10)}/${iso.substring(5, 7)}"
    }

    /** «2026-11-05T10:00» → «Jue 05/11 · 10:00». */
    fun citaLegible(isoConHora: String, hoy: String): String {
        val fecha = isoConHora.substringBefore('T')
        val hora = isoConHora.substringAfter('T', "")
        return etiquetaDia(fecha, hoy) + if (hora.isNotEmpty()) " · $hora" else ""
    }
}

/** Ejercicio de la rutina que el fisioterapeuta está editando, con su dosis. */
data class ItemRutina(
    val ejercicio: Ejercicio,
    val series: Int,
    val repeticiones: Int,
    val descansoSegundos: Int,
    val anguloObjetivo: Int?,
) {
    companion object {
        fun de(e: Ejercicio) = ItemRutina(e, e.series, e.repeticiones, e.descansoSegundos, e.anguloObjetivo)
    }
}

/**
 * BORRADOR DE RUTINA (RUT-08): las reglas para editarla viven aquí, fuera de la
 * vista, para poder probarlas. Son las mismas que valida el servidor.
 */
data class BorradorRutina(
    val metaFuncional: String = "",
    val umbralDolor: Int = 7,
    val items: List<ItemRutina> = emptyList(),
) {
    fun agregar(e: Ejercicio): BorradorRutina =
        if (items.size >= 10 || items.any { it.ejercicio.id == e.id }) this else copy(items = items + ItemRutina.de(e))

    fun quitar(id: Int) = copy(items = items.filterNot { it.ejercicio.id == id })

    fun subir(id: Int): BorradorRutina {
        val i = items.indexOfFirst { it.ejercicio.id == id }
        if (i <= 0) return this
        return copy(items = items.toMutableList().apply { add(i - 1, removeAt(i)) })
    }

    /** Cambia un valor dentro de sus límites: series 1–10, repeticiones 1–50, descanso 0–600 s (de 15 en 15), ángulo 5–180°. */
    fun ajustar(id: Int, campo: CampoDosis, delta: Int) = copy(items = items.map { item ->
        if (item.ejercicio.id != id) item else when (campo) {
            CampoDosis.SERIES -> item.copy(series = (item.series + delta).coerceIn(1, 10))
            CampoDosis.REPETICIONES -> item.copy(repeticiones = (item.repeticiones + delta).coerceIn(1, 50))
            CampoDosis.DESCANSO -> item.copy(descansoSegundos = (item.descansoSegundos + delta * 15).coerceIn(0, 600))
            CampoDosis.ANGULO -> item.copy(anguloObjetivo = ((item.anguloObjetivo ?: 45) + delta * 5).coerceIn(5, 180))
        }
    })

    /** Problema que impide guardar, o null si se puede enviar. */
    fun problema(): String? = when {
        metaFuncional.trim().length !in 5..120 -> "Escribe la meta funcional del paciente (de 5 a 120 caracteres)."
        umbralDolor !in 1..10 -> "El umbral de dolor va de 1 a 10."
        items.isEmpty() -> "Agrega al menos un ejercicio."
        else -> null
    }

    fun aSolicitud() = RutinaSolicitud(
        metaFuncional = metaFuncional.trim(),
        umbralDolor = umbralDolor,
        ejercicios = items.map { EjercicioAsignado(it.ejercicio.id, it.series, it.repeticiones, it.descansoSegundos, it.anguloObjetivo) },
    )
}

enum class CampoDosis { SERIES, REPETICIONES, DESCANSO, ANGULO }
