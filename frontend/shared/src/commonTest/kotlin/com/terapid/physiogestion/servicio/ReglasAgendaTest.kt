package com.terapid.physiogestion.servicio

import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.Ejercicio
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.sumarMinutos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fechas de la agenda y reglas del editor de rutinas. */
class ReglasAgendaTest {
    @Test
    fun lasFechasSeSumanBienEnCambiosDeMesYAnio() {
        assertEquals("2026-12-01", ReglasAgenda.sumarDias("2026-11-30", 1))
        assertEquals("2027-01-01", ReglasAgenda.sumarDias("2026-12-31", 1))
        assertEquals("2028-02-29", ReglasAgenda.sumarDias("2028-02-28", 1)) // año bisiesto
        assertEquals("2026-11-03", ReglasAgenda.sumarDias("2026-11-04", -1))
        assertEquals(0L, ReglasAgenda.diaJuliano("1970-01-01"))
    }

    @Test
    fun diaDeLaSemanaYDiasHabiles() {
        assertEquals(2, ReglasAgenda.diaSemana("2026-11-04")) // miércoles
        assertEquals(6, ReglasAgenda.diaSemana("2026-11-08")) // domingo
        val dias = ReglasAgenda.diasHabiles("2026-11-06", 3) // viernes, sábado, (domingo no), lunes
        assertEquals(listOf("2026-11-06", "2026-11-07", "2026-11-09"), dias)
    }

    @Test
    fun etiquetasDeDiaYHorario() {
        assertEquals("Hoy", ReglasAgenda.etiquetaDia("2026-11-04", "2026-11-04"))
        assertEquals("Mañana", ReglasAgenda.etiquetaDia("2026-11-05", "2026-11-04"))
        assertEquals("Vie 06/11", ReglasAgenda.etiquetaDia("2026-11-06", "2026-11-04"))
        assertEquals("Mañana · 10:00", ReglasAgenda.citaLegible("2026-11-05T10:00", "2026-11-04"))
        assertEquals("10:20", sumarMinutos("09:30", 50))
        val cita = Cita(1, "18:30", "Jorge", 2, "Ft. Carlos", "Rodilla", EstadoCita.CONFIRMADA, "2026-11-04", 1, 90)
        assertEquals("18:30 – 20:00", cita.horario)
        assertTrue(cita.abierta)
        assertFalse(cita.copy(estado = EstadoCita.NO_ASISTIO).abierta)
    }

    private val sentadilla = Ejercicio(1, "Sentadilla", "…", 3, 10, 45, "Rodilla", 90, medibleConSensores = true)
    private val puente = Ejercicio(2, "Puente", "…", 3, 12, 30, "Cadera", 35, medibleConSensores = true)

    @Test
    fun elBorradorRespetaLosLimitesYNoRepiteEjercicios() {
        var b = BorradorRutina("Subir escaleras sin dolor", 7).agregar(sentadilla).agregar(sentadilla).agregar(puente)
        assertEquals(listOf(1, 2), b.items.map { it.ejercicio.id })
        b = b.ajustar(1, CampoDosis.SERIES, 20).ajustar(1, CampoDosis.DESCANSO, -10).ajustar(2, CampoDosis.ANGULO, 2)
        assertEquals(10, b.items[0].series)
        assertEquals(0, b.items[0].descansoSegundos)
        assertEquals(45, b.items[1].anguloObjetivo)
        b = b.subir(2)
        assertEquals(listOf(2, 1), b.items.map { it.ejercicio.id })
        val solicitud = b.aSolicitud()
        assertEquals(2, solicitud.ejercicios.first().idEjercicio)
        assertNull(b.problema())
    }

    @Test
    fun elBorradorNoSeGuardaSinMetaNiEjercicios() {
        assertNotNull(BorradorRutina("", 7).agregar(sentadilla).problema())
        assertNotNull(BorradorRutina("Caminar al mercado", 7).problema())
        assertNotNull(BorradorRutina("Caminar al mercado", 7).agregar(sentadilla).quitar(1).problema())
    }
}
