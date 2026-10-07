package com.terapid.physiogestion.servicio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pruebas unitarias del servicio (código común: se ejecutan en todas las plataformas). */
class SeguimientoServicioTest {
    private val servicio = SeguimientoServicio()

    @Test
    fun cumplimientoEsElPorcentajeDeLoAsignado() {
        assertEquals(75, servicio.calcularCumplimiento(realizados = 3, asignados = 4))
        assertEquals(100, servicio.calcularCumplimiento(realizados = 4, asignados = 4))
        assertEquals(0, servicio.calcularCumplimiento(realizados = 0, asignados = 0))
    }

    @Test
    fun hacerDeMasNoPasaDelCienPorCiento() {
        assertEquals(100, servicio.calcularCumplimiento(realizados = 6, asignados = 4))
    }

    @Test
    fun soloSeAceptaLaEscalaDeCeroADiez() {
        assertTrue(servicio.esNivelDolorValido(0))
        assertTrue(servicio.esNivelDolorValido(10))
        assertFalse(servicio.esNivelDolorValido(-1))
        assertFalse(servicio.esNivelDolorValido(11))
    }

    @Test
    fun alertaCuandoElDolorAlcanzaElUmbral() {
        val alerta = servicio.evaluarDolor(dolorActual = 7, umbral = 7, dolorAnterior = 6)
        assertNotNull(alerta)
        assertEquals(SeguimientoServicio.RECOMENDACION, alerta.recomendacion)
    }

    @Test
    fun alertaCuandoElDolorSubeTresPuntos() {
        assertNotNull(servicio.evaluarDolor(dolorActual = 5, umbral = 8, dolorAnterior = 2))
    }

    @Test
    fun sinAlertaConDolorBajoYEstable() {
        assertNull(servicio.evaluarDolor(dolorActual = 4, umbral = 7, dolorAnterior = 2))
        assertNull(servicio.evaluarDolor(dolorActual = 3, umbral = 7, dolorAnterior = null))
    }

    @Test
    fun laRecomendacionNuncaSugiereContinuar() {
        assertFalse(SeguimientoServicio.RECOMENDACION.contains("continu", ignoreCase = true))
    }

    @Test
    fun puntosConBonoPorRutinaCompleta() {
        assertEquals(60, servicio.calcularPuntos(realizados = 4, asignados = 4, puntosPreviosDelDia = 0))
        assertEquals(20, servicio.calcularPuntos(realizados = 2, asignados = 4, puntosPreviosDelDia = 0))
    }

    @Test
    fun elTopeDiarioImpideAcumularMasPuntos() {
        assertEquals(0, servicio.calcularPuntos(realizados = 4, asignados = 4, puntosPreviosDelDia = 60))
        assertEquals(10, servicio.calcularPuntos(realizados = 4, asignados = 4, puntosPreviosDelDia = 50))
    }

    @Test
    fun evaluarSesionComparaElDolorDeDespuesConElDeAntes() {
        val r = servicio.evaluarSesion(
            realizados = 3, asignados = 4, dolorAntes = 2, dolorDespues = 5,
            umbral = 7, dolorSesionAnterior = null, puntosPreviosDelDia = 0,
        )
        assertEquals(75, r.cumplimiento)
        assertEquals(30, r.puntos)
        assertNotNull(r.alerta)
    }
}
