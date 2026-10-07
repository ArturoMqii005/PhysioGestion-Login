package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.local.BaseDatosLocal
import com.terapid.physiogestion.datos.remoto.RutinaApi
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Sesion
import com.terapid.physiogestion.modelo.Usuario
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Prueba de integración en escritorio: backend simulado (MockEngine) + SQLite real
 * en un archivo temporal. Comprueba descarga, copia local y modo sin conexión.
 */
class RutinaRepositorioTest {
    private val archivoBd = File.createTempFile("physiogestion", ".db")
    private val bd = BaseDatosLocal(archivoBd.absolutePath)
    private var hayConexion = true

    private val rutinaJson = """
        {"id":101,"pacienteId":1,"paciente":"Paciente de prueba","fisioterapeuta":"Ft. de prueba",
         "metaFuncional":"Subir escaleras sin dolor","umbralDolor":7,"vigenteDesde":"2026-09-28",
         "ejercicios":[{"id":1,"nombre":"Puente de glúteo","indicaciones":"Eleva la cadera.",
         "series":3,"repeticiones":12,"descansoSegundos":30,"zonaCorporal":"Cadera"}]}
    """.trimIndent()

    private val cliente = clienteSimulado { peticion ->
        assertTrue(peticion.url.encodedPath.endsWith("/api/v1/pacientes/1/rutina"))
        assertEquals("Bearer token-prueba", peticion.headers[HttpHeaders.Authorization])
        if (hayConexion) json(rutinaJson) else respondError(HttpStatusCode.ServiceUnavailable)
    }

    private val gestor = GestorSesion().apply {
        abrir(Sesion("token-prueba", Usuario(5, "paciente", "Paciente de prueba", Rol.PACIENTE, pacienteId = 1)))
    }

    private val repositorio = RutinaRepositorio(
        api = RutinaApi(cliente, jsonPrueba),
        almacenamiento = AlmacenamientoLocal(bd),
        gestor = gestor,
        reloj = { 1_790_000_000_000 },
    )

    @AfterTest
    fun limpiar() = runBlocking {
        bd.cerrar()
        archivoBd.delete()
        Unit
    }

    @Test
    fun descargaLaRutinaYLaUsaSinConexion() = runBlocking {
        val enLinea = repositorio.obtenerRutina(1)
        assertEquals(OrigenDatos.SERVIDOR, enLinea.origen)
        assertEquals("Puente de glúteo", enLinea.rutina.ejercicios.first().nombre)

        hayConexion = false
        val sinConexion = repositorio.obtenerRutina(1)
        assertEquals(OrigenDatos.ALMACENAMIENTO_LOCAL, sinConexion.origen)
        assertEquals(enLinea.rutina, sinConexion.rutina)
        assertEquals(1_790_000_000_000, sinConexion.actualizadaEn)
    }

    @Test
    fun sinConexionYSinCopiaAvisaQueNoHayRutina() = runBlocking {
        hayConexion = false
        assertFailsWith<SinRutinaDisponible> { repositorio.obtenerRutina(1) }
        Unit
    }

    @Test
    fun guardaLasSesionesEnLaColaPendiente() = runBlocking {
        val sesion = RegistroSesion(
            rutinaId = 101, inicioMillis = 1_000, finMillis = 2_000, claveDia = "2026-09-29",
            ejerciciosRealizados = listOf(1), ejerciciosAsignados = 1, cumplimiento = 100,
            dolorAntes = 2, dolorDespues = 3, dificultad = Dificultad.MEDIA, observacion = "Bien",
            puntos = 30, alertaDolor = false,
        )
        val id = repositorio.registrarSesion(sesion)
        val historial = repositorio.historial()

        assertEquals(1, historial.size)
        assertEquals(sesion.copy(id = id), historial.first())
        assertTrue(historial.first().pendienteEnvio)
        assertEquals(3, repositorio.ultimoDolorRegistrado())
        assertEquals(30, repositorio.puntosDelDia("2026-09-29"))
    }
}
