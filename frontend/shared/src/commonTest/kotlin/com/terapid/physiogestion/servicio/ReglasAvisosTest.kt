package com.terapid.physiogestion.servicio

import com.terapid.physiogestion.modelo.Aviso
import com.terapid.physiogestion.modelo.BandejaAvisos
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.TipoAviso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReglasAvisosTest {
    private fun aviso(id: Int, leido: Boolean = false) =
        Aviso(id, 2, TipoAviso.DOLOR_ALTO, "Dolor alto: Jorge", "8/10", creado = 0, leido = leido, pacienteId = 1)

    private fun cita(id: Int, fecha: String, hora: String, estado: EstadoCita = EstadoCita.CONFIRMADA) =
        Cita(id, hora, "Jorge Ramírez Cruz", 2, "Ft. Carlos Ortega Luna", "Rodilla", estado, fecha, pacienteId = 1)

    @Test
    fun laPrimeraRevisionNoNotificaLoViejoYDespuesSoloLoNuevoSinLeer() {
        val bandeja = BandejaAvisos(listOf(aviso(9), aviso(8, leido = true), aviso(7), aviso(5)), sinLeer = 3, ultimoId = 9)
        // Primera vez en este teléfono: lo que ya existía se ve en la bandeja, no como notificación.
        assertTrue(ReglasAvisos.paraNotificar(bandeja, ultimoVisto = null).isEmpty())
        // Ya se había visto hasta el 6: salen el 7 y el 9 (el 8 ya está leído), del más viejo al más nuevo.
        assertEquals(listOf(7, 9), ReglasAvisos.paraNotificar(bandeja, ultimoVisto = 6).map { it.id })
        assertTrue(ReglasAvisos.paraNotificar(bandeja, ultimoVisto = 9).isEmpty())
    }

    @Test
    fun cadaCitaAbiertaTieneRecordatorioUnDiaYUnaHoraAntes() {
        val citas = listOf(
            cita(3, "2026-11-06", "12:00"),
            cita(1, "2026-11-05", "10:00", EstadoCita.PENDIENTE),
            cita(2, "2026-11-05", "16:00", EstadoCita.CANCELADA),
            cita(4, "2026-11-03", "09:00"),
            cita(5, "2026-11-04", "18:00", EstadoCita.ATENDIDA),
        )
        val r = ReglasAvisos.recordatoriosDeCitas(citas, hoy = "2026-11-04")
        assertEquals(listOf("cita-1-1440", "cita-1-60", "cita-3-1440", "cita-3-60"), r.map { it.clave })
        assertEquals("Mañana tienes cita", r[0].titulo)
        assertTrue("a las 10:00 con Ft. Carlos Ortega Luna" in r[0].texto, r[0].texto)
        assertEquals("Tu cita es en una hora", r[1].titulo)
        assertEquals(60, r[1].minutosAntes)
    }

    @Test
    fun laHoraDelRecordatorioSeMueveDeMediaEnMediaHoraEntreLas6YLas22() {
        assertEquals("18:00", ReglasAvisos.horaLegible(ReglasAvisos.HORA_SUGERIDA))
        assertEquals("18:30", ReglasAvisos.horaLegible(ReglasAvisos.ajustarHora(18 * 60, 1)))
        assertEquals("06:00", ReglasAvisos.horaLegible(ReglasAvisos.ajustarHora(6 * 60 + 30, -3)))
        assertEquals("22:00", ReglasAvisos.horaLegible(ReglasAvisos.ajustarHora(21 * 60 + 30, 4)))
    }

    @Test
    fun elAvisoDePendientesUsaSingularYPlural() {
        assertEquals("Tienes 1 sesión sin enviar", ReglasAvisos.textoPendientes(1).first)
        assertTrue(ReglasAvisos.textoPendientes(3).second.startsWith("Están guardadas"))
    }
}
