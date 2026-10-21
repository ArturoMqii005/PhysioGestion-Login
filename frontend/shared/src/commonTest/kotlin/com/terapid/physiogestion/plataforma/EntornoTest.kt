package com.terapid.physiogestion.plataforma

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntornoTest {

    @Test
    fun produccionYPruebasApuntanAlServidorDeRailway() {
        assertEquals(Entorno.URL_SERVIDOR_NUBE, Entorno.urlServidorPara(TipoEntorno.PRODUCCION))
        assertEquals(Entorno.URL_SERVIDOR_NUBE, Entorno.urlServidorPara(TipoEntorno.PRUEBAS))
    }

    @Test
    fun elServidorEnLaNubeUsaHttpsYNoTerminaEnDiagonal() {
        assertTrue(Entorno.URL_SERVIDOR_NUBE.startsWith("https://"))
        assertTrue(!Entorno.URL_SERVIDOR_NUBE.endsWith("/"))
    }
}
