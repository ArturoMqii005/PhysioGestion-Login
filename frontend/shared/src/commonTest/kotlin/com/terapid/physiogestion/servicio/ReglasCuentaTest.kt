package com.terapid.physiogestion.servicio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Las reglas de la app coinciden con las del servidor y avisan antes de enviar. */
class ReglasCuentaTest {
    @Test
    fun contrasenaDebeTener8CaracteresConLetrasYNumeros() {
        assertNotNull(ReglasCuenta.problemaContrasena("abc123"))
        assertNotNull(ReglasCuenta.problemaContrasena("soloLetrasAqui"))
        assertNotNull(ReglasCuenta.problemaContrasena("12345678"))
        assertNotNull(ReglasCuenta.problemaContrasena(" Rodilla2026"))
        assertNull(ReglasCuenta.problemaContrasena("Rodilla2026"))
    }

    @Test
    fun laContrasenaNoPuedeIncluirElUsuario() {
        assertNotNull(ReglasCuenta.problemaContrasena("maria.lopez2026", usuario = "maria.lopez"))
        assertNull(ReglasCuenta.problemaContrasena("Rodilla2026", usuario = "maria.lopez"))
    }

    @Test
    fun laFuerzaSubeConLargoYVariedad() {
        assertEquals(0, ReglasCuenta.fuerza(""))
        assertTrue(ReglasCuenta.fuerza("abc") < ReglasCuenta.fuerza("rodilla2026"))
        assertEquals(4, ReglasCuenta.fuerza("Rodilla-Sana-2026"))
    }

    @Test
    fun usuarioYCorreo() {
        assertNull(ReglasCuenta.problemaUsuario("maria.lopez"))
        assertNotNull(ReglasCuenta.problemaUsuario("ml"))
        assertNotNull(ReglasCuenta.problemaUsuario("María López"))
        assertNull(ReglasCuenta.problemaCorreo("maria@gmail.com"))
        assertNotNull(ReglasCuenta.problemaCorreo("maria@gmail"))
        assertEquals("maria.lopez", ReglasCuenta.usuarioSugerido("Maria.Lopez@gmail.com"))
    }

    @Test
    fun codigosSeLimpianMientrasSeEscriben() {
        assertEquals("K7QM-4R2X", ReglasCuenta.formatearInvitacion("k7qm 4r2x"))
        assertEquals("K7Q", ReglasCuenta.formatearInvitacion("k7q"))
        assertNull(ReglasCuenta.problemaInvitacion("K7QM-4R2X"))
        assertNotNull(ReglasCuenta.problemaInvitacion("K7QM"))
        assertEquals("123456", ReglasCuenta.limpiarCodigo("12 34-567"))
    }
}
