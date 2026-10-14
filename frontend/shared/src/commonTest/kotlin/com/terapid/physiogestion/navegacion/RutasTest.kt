package com.terapid.physiogestion.navegacion

import com.terapid.physiogestion.modelo.Rol
import kotlin.test.Test
import kotlin.test.assertEquals

/** Comprueba que cada rol se enruta a su propia vista. */
class RutasTest {
    @Test
    fun cadaRolTieneSuVista() {
        assertEquals(RutaAdministrador, rutaInicialPara(Rol.ADMINISTRADOR))
        assertEquals(RutaFisioterapeuta, rutaInicialPara(Rol.FISIOTERAPEUTA))
        assertEquals(RutaRecepcion, rutaInicialPara(Rol.RECEPCIONISTA))
        assertEquals(RutaPaciente, rutaInicialPara(Rol.PACIENTE))
    }

    @Test
    fun ningunRolCompartePantalla() {
        val rutas = Rol.entries.map(::rutaInicialPara)
        assertEquals(rutas.size, rutas.toSet().size)
    }

    @Test
    fun lasRutasConArgumentoLlevanSuDato() {
        assertEquals(RutaEjercicio(3), RutaEjercicio(3))
        assertEquals(7, RutaDetalleSesion(7).idSesion)
        kotlin.test.assertNotEquals(RutaEjercicio(3), RutaEjercicio(4))
    }
}
