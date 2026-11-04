package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.ClinicaApi
import com.terapid.physiogestion.datos.remoto.Conflicto
import com.terapid.physiogestion.modelo.CitaSolicitud
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.RutinaSolicitud
import com.terapid.physiogestion.modelo.EjercicioAsignado
import com.terapid.physiogestion.modelo.Sesion
import com.terapid.physiogestion.modelo.Usuario
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Citas, pacientes y rutinas contra un backend simulado. */
class ClinicaRepositorioTest {
    private val gestor = GestorSesion().apply { abrir(Sesion("t9", Usuario(4, "recepcion", "Ana", Rol.RECEPCIONISTA))) }

    private val citaJson = """{"id":7,"hora":"10:00","paciente":"Patricia Hernández Gómez","fisioterapeutaId":2,
        "fisioterapeuta":"Ft. Carlos Ortega Luna","motivo":"Dolor lumbar","estado":"PENDIENTE","fecha":"2026-11-05","duracionMinutos":50}"""

    @Test
    fun agendarEnviaLaCitaYLeeLaRespuesta() = runBlocking {
        var cuerpo = ""
        val cliente = clienteSimulado { p ->
            assertEquals(HttpMethod.Post, p.method)
            assertTrue(p.url.encodedPath.endsWith("/api/v1/citas"))
            cuerpo = p.body.toByteArray().decodeToString()
            json(citaJson, HttpStatusCode.Created)
        }
        val cita = ClinicaRepositorio(ClinicaApi(cliente), gestor).agendar(
            CitaSolicitud("Patricia Hernández Gómez", null, 2, "2026-11-05", "10:00", 50, "Dolor lumbar")
        )
        val enviado = jsonPrueba.parseToJsonElement(cuerpo).jsonObject
        assertEquals("2026-11-05", enviado["fecha"]!!.jsonPrimitive.content)
        assertEquals("10:00", enviado["hora"]!!.jsonPrimitive.content)
        assertEquals(EstadoCita.PENDIENTE, cita.estado)
        assertEquals("10:00 – 10:50", cita.horario)
    }

    @Test
    fun unChoqueDeHorarioMuestraElMensajeDelServidor() = runBlocking {
        val cliente = clienteSimulado {
            json("""{"mensaje":"Ft. Carlos Ortega Luna ya tiene una cita de 10:00 a 10:50 ese día."}""", HttpStatusCode.Conflict)
        }
        val error = assertFailsWith<Conflicto> {
            ClinicaRepositorio(ClinicaApi(cliente), gestor).agendar(CitaSolicitud("Elena", null, 2, "2026-11-05", "10:30", 50, "Hombro"))
        }
        assertEquals("Ft. Carlos Ortega Luna ya tiene una cita de 10:00 a 10:50 ese día.", error.message)
    }

    @Test
    fun lasHorasLibresSePidenConFisioFechaYDuracion() = runBlocking {
        var consulta = ""
        val cliente = clienteSimulado { p ->
            consulta = p.url.encodedQuery
            json("""["07:00","07:30","11:00"]""")
        }
        val libres = ClinicaRepositorio(ClinicaApi(cliente), gestor).horasLibres(3, "2026-11-05", 60, excepto = 7)
        assertEquals(listOf("07:00", "07:30", "11:00"), libres)
        assertTrue("fisioterapeutaId=3" in consulta && "fecha=2026-11-05" in consulta && "duracion=60" in consulta && "excepto=7" in consulta, consulta)
    }

    @Test
    fun laFichaYLaRutinaViajanCompletas() = runBlocking {
        var metodoRutina: HttpMethod? = null
        val cliente = clienteSimulado { p ->
            if (p.method == HttpMethod.Put) {
                metodoRutina = p.method
                assertTrue(p.body.toByteArray().decodeToString().contains("\"idEjercicio\":5"))
                json("""{"id":101,"pacienteId":1,"paciente":"Jorge","fisioterapeuta":"Ft. Carlos","metaFuncional":"Subir escaleras",
                    "umbralDolor":6,"vigenteDesde":"04/11/2026","ejercicios":[]}""")
            } else {
                json("""{"resumen":{"pacienteId":1,"nombre":"Jorge Ramírez Cruz","tieneCuenta":true,"fisioterapeutaId":2,
                    "fisioterapeuta":"Ft. Carlos","tieneRutina":true,"semaforo":"VERDE","cumplimientoPromedio":85},
                    "citas":[$citaJson]}""")
            }
        }
        val repo = ClinicaRepositorio(ClinicaApi(cliente), gestor)
        val ficha = repo.ficha(1)
        assertEquals(85, ficha.resumen.cumplimientoPromedio)
        assertEquals(1, ficha.citas.size)
        val rutina = repo.guardarRutina(1, RutinaSolicitud("Subir escaleras", 6, listOf(EjercicioAsignado(5, 3, 10, 30))))
        assertEquals(HttpMethod.Put, metodoRutina)
        assertEquals(101, rutina.id)
    }
}
