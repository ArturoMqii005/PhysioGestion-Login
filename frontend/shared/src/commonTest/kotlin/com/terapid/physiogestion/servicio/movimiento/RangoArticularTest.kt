package com.terapid.physiogestion.servicio.movimiento

import kotlin.test.Test
import kotlin.test.assertEquals

class RangoArticularTest {
    @Test
    fun clasificaRespectoDelObjetivo() {
        assertEquals(RangoArticular.INSUFICIENTE, RangoArticular.clasificar(50f, 90))
        assertEquals(RangoArticular.ACEPTABLE, RangoArticular.clasificar(70f, 90))
        assertEquals(RangoArticular.OBJETIVO_ALCANZADO, RangoArticular.clasificar(95f, 90))
        assertEquals(RangoArticular.EXCEDIDO, RangoArticular.clasificar(110f, 90))
    }

    @Test
    fun sinObjetivoSiempreEsAceptable() {
        assertEquals(RangoArticular.ACEPTABLE, RangoArticular.clasificar(10f, null))
    }
}
