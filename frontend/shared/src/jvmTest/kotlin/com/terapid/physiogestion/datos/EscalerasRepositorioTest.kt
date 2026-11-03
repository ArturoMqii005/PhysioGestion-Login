package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.local.BaseDatosLocal
import com.terapid.physiogestion.datos.remoto.EscalerasApi
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Sesion
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.servicio.escaleras.ResultadoEscaleras
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** La prueba de escaleras se guarda primero en SQLite y después se envía; sin red queda pendiente. */
class EscalerasRepositorioTest {
    private val archivoBd = File.createTempFile("physiogestion-escaleras", ".db")
    private val bd = BaseDatosLocal(archivoBd.absolutePath)
    private val almacenamiento = AlmacenamientoLocal(bd)
    private val gestor = GestorSesion().apply {
        abrir(Sesion("token-prueba", Usuario(5, "paciente", "Jorge", Rol.PACIENTE, pacienteId = 1)))
    }
    private var hayConexion = true
    /** Pruebas (por duración) que el servidor rechaza con 422. */
    private val rechazadas = mutableSetOf<Int>()
    private val cuerpos = mutableListOf<String>()

    private val cliente = clienteSimulado { peticion ->
        if (!hayConexion) throw IOException("Sin red")
        val texto = peticion.body.toByteArray().decodeToString()
        val duracion = jsonPrueba.parseToJsonElement(texto).jsonObject["duracionSegundos"]!!.jsonPrimitive.content.toInt()
        if (duracion in rechazadas) {
            return@clienteSimulado json("""{"mensaje":"Los valores de la prueba de escaleras están fuera de rango."}""", HttpStatusCode.UnprocessableEntity)
        }
        assertTrue(peticion.url.encodedPath.endsWith("/api/v1/pruebas-escaleras"))
        cuerpos += texto
        json(
            """{"id":1,"pacienteId":1,"paciente":"Jorge Ramírez Cruz","fecha":"2026-10-28T17:30:00","alturaSubidaCm":602,
                "pisos":2,"escalonesEstimados":35,"pasos":38,"duracionSegundos":45}""",
            HttpStatusCode.Created,
        )
    }

    private var ahora = 1_792_000_000_000L
    private val repositorio = EscalerasRepositorio(EscalerasApi(cliente), almacenamiento, gestor, reloj = { ahora })

    private val resultado = ResultadoEscaleras(alturaSubidaCm = 602, pisos = 2, escalonesEstimados = 35, pasos = 38, duracionSegundos = 45)

    @AfterTest
    fun limpiar() = runBlocking {
        bd.cerrar()
        archivoBd.delete()
        Unit
    }

    @Test
    fun seGuardaYSeEnviaSoloElResumen() = runBlocking {
        val envio = repositorio.registrar(resultado)
        assertTrue(envio.enviada)
        val cuerpo = jsonPrueba.parseToJsonElement(cuerpos.single()).jsonObject
        assertEquals(
            setOf("fecha", "alturaSubidaCm", "pisos", "escalonesEstimados", "pasos", "duracionSegundos"),
            cuerpo.keys,
        )
        assertEquals("602", cuerpo["alturaSubidaCm"]!!.jsonPrimitive.content)
        val guardada = repositorio.historial().single()
        assertTrue(guardada.enviado)
        assertEquals(8.0f, guardada.metrosPorMinuto, 0.05f) // 6.02 m en 45 s
    }

    @Test
    fun sinConexionQuedaPendienteYSeEnviaDespues() = runBlocking {
        hayConexion = false
        assertFalse(repositorio.registrar(resultado).enviada)
        assertEquals(1, almacenamiento.pruebasEscalerasPendientes().size)

        hayConexion = true
        assertEquals(1, repositorio.enviarPendientes())
        assertTrue(almacenamiento.pruebasEscalerasPendientes().isEmpty())
    }

    @Test
    fun unaPruebaRechazadaPorElServidorNoTapaLaCola() = runBlocking {
        hayConexion = false
        repositorio.registrar(resultado.copy(duracionSegundos = 7)) // el servidor la rechazará (422)
        repositorio.registrar(resultado.copy(duracionSegundos = 50))
        assertEquals(2, almacenamiento.pruebasEscalerasPendientes().size)

        hayConexion = true
        rechazadas += 7
        assertEquals(1, repositorio.enviarPendientes()) // la segunda sí llegó
        assertTrue(almacenamiento.pruebasEscalerasPendientes().isEmpty())
        assertEquals(2, repositorio.historial().size) // las dos siguen en el teléfono
    }

    @Test
    fun elHistorialMuestraLaMasRecientePrimero() = runBlocking {
        hayConexion = false
        repositorio.registrar(resultado.copy(duracionSegundos = 60))
        ahora += 7 * 24 * 3_600_000L // una semana después
        repositorio.registrar(resultado.copy(duracionSegundos = 45, pasos = null))
        val historial = repositorio.historial()
        assertEquals(listOf(45, 60), historial.map { it.duracionSegundos })
        assertEquals(null, historial.first().pasos)
    }
}
