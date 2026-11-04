package com.terapid.physiogestion.navegacion

import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Sesion
import com.terapid.physiogestion.modelo.Usuario
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

    @Test
    fun antesDeLaVistaDelRolSePideLoQueFalta() {
        val paciente = Sesion("t", Usuario(5, "paciente", "Jorge", Rol.PACIENTE, pacienteId = 1))
        assertEquals(RutaPaciente, rutaPara(paciente))
        assertEquals(RutaAvisoPrivacidad, rutaPara(paciente.copy(requiereConsentimiento = true)))
        // Con las dos pendientes, primero el aviso y después la contraseña.
        assertEquals(RutaAvisoPrivacidad, rutaPara(paciente.copy(requiereConsentimiento = true, debeCambiarContrasena = true)))
        val fisio = Sesion("t", Usuario(9, "sofia", "Sofía", Rol.FISIOTERAPEUTA), debeCambiarContrasena = true)
        assertEquals(RutaCambiarContrasena(obligatorio = true), rutaPara(fisio))
        assertEquals(RutaFisioterapeuta, rutaPara(fisio.copy(debeCambiarContrasena = false)))
    }
}
