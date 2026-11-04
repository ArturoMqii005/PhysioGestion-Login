package com.terapid.physiogestion.servicio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReglasProgresoTest {
    @Test
    fun laSerieDeDolorQuedaEnOrdenYSoloLasUltimas() {
        val sesiones = listOf(
            "2026-11-03T18:00:00" to 4,
            "2026-10-30T09:00:00" to 7,
            "2026-11-04T07:40:00" to 3,
            "2026-11-01T10:00:00" to 6,
        )
        val serie = ReglasProgreso.serieDolor(sesiones, maximo = 3)
        assertEquals(listOf("2026-11-01", "2026-11-03", "2026-11-04"), serie.map { it.fecha })
        assertEquals("Bajó de 6 a 3 en 3 sesiones", ReglasProgreso.textoDolor(serie))
    }

    @Test
    fun elTextoDelDolorDiceSiSubioSeMantieneONoHayDatos() {
        assertTrue(ReglasProgreso.textoDolor(emptyList()).startsWith("Registra tu primera sesión"))
        assertEquals("Subió de 2 a 5 en 2 sesiones", ReglasProgreso.textoDolor(listOf(PuntoDolor("2026-11-01", 2), PuntoDolor("2026-11-02", 5))))
        assertEquals("Se mantiene en 4 en 2 sesiones", ReglasProgreso.textoDolor(listOf(PuntoDolor("2026-11-01", 4), PuntoDolor("2026-11-02", 4))))
    }

    @Test
    fun laMejoraDelRitmoSeCalculaContraLaPrimeraPrueba() {
        val serie = ReglasProgreso.serieRitmo(listOf(PuntoRitmo("2026-11-04", 5.6f), PuntoRitmo("2026-10-21", 4.2f), PuntoRitmo("2026-10-28", 0f)))
        assertEquals(listOf("2026-10-21", "2026-11-04"), serie.map { it.fecha })
        assertEquals(33, ReglasProgreso.mejoraRitmo(serie))
        assertNull(ReglasProgreso.mejoraRitmo(serie.take(1)))
    }

    @Test
    fun elCalendarioEmpiezaEnLunesYTerminaLaSemanaDeHoy() {
        // Miércoles 4 de noviembre de 2026: el calendario va del lunes 12 de octubre al domingo 8 de noviembre.
        val dias = setOf("2026-10-12", "2026-11-02", "2026-11-04", "2026-11-08")
        val c = ReglasProgreso.calendario(dias, hoy = "2026-11-04")
        assertEquals(28, c.size)
        assertEquals(EstadoDia.HECHO, c[0])
        assertEquals(EstadoDia.FALTO, c[1])
        assertEquals(EstadoDia.HECHO, c[21])
        assertEquals(EstadoDia.HOY_HECHO, c[23])
        assertEquals(EstadoDia.FUTURO, c[27]) // el domingo todavía no llega, aunque haya algo con esa fecha
        assertEquals(3 to 24, ReglasProgreso.constancia(c))
    }
}
