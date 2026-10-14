package com.terapid.physiogestion.modelo

import kotlin.test.Test
import kotlin.test.assertEquals

class FechaIsoTest {
    @Test
    fun convierteIsoAFormatoDeMexico() {
        assertEquals("14/10/2026 18:05", fechaIsoLegible("2026-10-14T18:05:00"))
        assertEquals("14/10/2026", fechaIsoLegible("2026-10-14"))
    }
}
