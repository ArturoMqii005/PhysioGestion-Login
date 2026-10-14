package com.terapid.physiogestion.datos.local

import com.terapid.physiogestion.datos.GestorSesion
import com.terapid.physiogestion.datos.PreferenciasRepositorio
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Sesion
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.servicio.SeguimientoServicio
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Implementaciones de escritorio (jvmMain) de preferencias, sesión guardada y caché de archivos. */
class AlmacenamientoPlataformaTest {
    private val preferencias = AlmacenPreferencias("prueba-${UUID.randomUUID()}")
    private val archivos = AlmacenArchivos("prueba-${UUID.randomUUID()}")
    /** Nodo propio: las pruebas no tocan la sesión guardada de la app de escritorio. */
    private val seguro = AlmacenSeguro("com/terapid/prueba-${UUID.randomUUID()}")

    @AfterTest
    fun limpiar() {
        preferencias.eliminarTodo()
        archivos.eliminarCarpeta()
        seguro.eliminarTodo()
    }

    @Test
    fun lasPreferenciasGuardanYBorranValores() {
        preferencias.guardarTexto("servidor", "http://localhost:8080")
        preferencias.guardarEntero("racha", 4)
        preferencias.guardarLargo("pasos", 15_000_000_000L)
        preferencias.guardarBooleano("camara", true)
        assertEquals("http://localhost:8080", preferencias.leerTexto("servidor"))
        assertEquals(4, preferencias.leerEntero("racha"))
        assertEquals(15_000_000_000L, preferencias.leerLargo("pasos"))
        assertTrue(preferencias.leerBooleano("camara"))
        preferencias.borrar("racha")
        assertFalse(preferencias.contiene("racha"))
        assertEquals(7, preferencias.leerEntero("racha", 7))
    }

    @Test
    fun laSesionGuardadaSeRestauraAlAbrirLaApp() {
        val persistencia = PersistenciaSesion(preferencias, seguro, Json)
        val sesion = Sesion("token-123", Usuario(5, "paciente", "Jorge Ramírez Cruz", Rol.PACIENTE, pacienteId = 1))
        GestorSesion(persistencia).abrir(sesion)

        val alAbrirDeNuevo = GestorSesion(persistencia)
        assertEquals(sesion, alAbrirDeNuevo.sesion.value)

        alAbrirDeNuevo.cerrar()
        assertNull(GestorSesion(persistencia).sesion.value)
        assertNull(seguro.leerToken())
    }

    @Test
    fun laCacheDeVideosGuardaMideYSeVacia() {
        val ruta = archivos.guardarVideo(1, ByteArray(2_048) { 7 })
        assertTrue(archivos.existeVideo(1))
        assertEquals(ruta, archivos.rutaVideo(1))
        assertEquals(2_048L, archivos.tamanoOcupado())
        assertEquals("2 KB", formatearTamano(archivos.tamanoOcupado()))
        archivos.limpiar()
        assertFalse(archivos.existeVideo(1))
        assertEquals(0, archivos.cantidad())
    }

    @Test
    fun laRachaYLosPasosUsanLaFechaDelTelefono() {
        var ahora = 1_791_993_600_000L + 12 * 3_600_000L // un mediodía
        val repo = PreferenciasRepositorio(preferencias, SeguimientoServicio(), reloj = { ahora })

        assertEquals(1, repo.registrarDiaActivo(pacienteId = 1))
        assertEquals(1, repo.registrarDiaActivo(pacienteId = 1)) // otra sesión el mismo día
        ahora += 24 * 3_600_000L
        assertEquals(2, repo.registrarDiaActivo(pacienteId = 1)) // al día siguiente
        ahora += 3 * 24 * 3_600_000L
        assertEquals(0, repo.racha(pacienteId = 1)) // pasaron tres días: se rompió

        assertEquals(0L, repo.pasosDeHoy(lectura = 40_000, cuentaDesdeInicioDelDia = false))
        assertEquals(850L, repo.pasosDeHoy(lectura = 40_850, cuentaDesdeInicioDelDia = false))
        assertEquals(1_200L, repo.pasosDeHoy(lectura = 1_200, cuentaDesdeInicioDelDia = true))
    }
}
