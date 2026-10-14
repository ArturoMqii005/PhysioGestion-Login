package com.terapid.physiogestion

import com.terapid.physiogestion.modelos.CrearSesionSolicitud
import com.terapid.physiogestion.modelos.Dificultad
import com.terapid.physiogestion.modelos.EjercicioRealizado
import com.terapid.physiogestion.modelos.LoginRespuesta
import com.terapid.physiogestion.modelos.LoginSolicitud
import com.terapid.physiogestion.modelos.ModificarSesionSolicitud
import com.terapid.physiogestion.modelos.ReemplazarSesionSolicitud
import com.terapid.physiogestion.modelos.ResultadoEjercicio
import com.terapid.physiogestion.modelos.ResultadoEjercicioSolicitud
import com.terapid.physiogestion.modelos.Semaforo
import com.terapid.physiogestion.modelos.SesionTerapia
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pruebas de los cinco métodos HTTP sobre /api/v1/sesiones y de los resultados de sensores. */
class SesionesTest {

    private fun ApplicationTestBuilder.clienteJson(): HttpClient = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun HttpClient.token(usuario: String, contrasena: String): String =
        post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginSolicitud(usuario, contrasena))
        }.body<LoginRespuesta>().token

    private fun nueva(inicio: String = "2026-10-14T10:00:00", dolorDespues: Int = 3) = CrearSesionSolicitud(
        idRutina = 101,
        fechaHoraInicio = inicio,
        fechaHoraFin = inicio.dropLast(5) + "45:00",
        dolorAntes = 2,
        dolorDespues = dolorDespues,
        dificultad = Dificultad.MEDIA,
        observacion = "Todo bien",
        ejercicios = listOf(
            EjercicioRealizado(1, 30, anguloMaximo = 88, verificado = true),
            EjercicioRealizado(2, 36),
            EjercicioRealizado(3, 45),
        ),
    )

    private suspend fun HttpClient.crear(token: String, cuerpo: CrearSesionSolicitud, clave: String? = null) =
        post("/api/v1/sesiones") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            clave?.let { header("Idempotency-Key", it) }
            setBody(cuerpo)
        }

    @Test
    fun `POST crea la sesion con 201 y Location, y GET la devuelve`() = testApplication {
        configure()
        val cliente = clienteJson()
        val token = cliente.token("paciente", "Paciente2026")

        val respuesta = cliente.crear(token, nueva())
        assertEquals(HttpStatusCode.Created, respuesta.status)
        val creada = respuesta.body<SesionTerapia>()
        assertEquals("/api/v1/sesiones/${creada.id}", respuesta.headers[HttpHeaders.Location])
        assertEquals(75, creada.porcentajeCumplimiento) // 3 de 4 ejercicios
        assertEquals(Semaforo.VERDE, creada.semaforo)

        val leida = cliente.get("/api/v1/sesiones/${creada.id}") { bearerAuth(token) }.body<SesionTerapia>()
        assertEquals(creada, leida)

        val historial = cliente.get("/api/v1/sesiones?idPaciente=1") { bearerAuth(token) }
        assertEquals("3", historial.headers["X-Total-Count"]) // 2 de sesiones.json + la nueva
        assertEquals(creada.id, historial.body<List<SesionTerapia>>().first().id) // la más reciente primero
    }

    @Test
    fun `reintentar el POST con la misma clave no duplica la sesion`() = testApplication {
        configure()
        val cliente = clienteJson()
        val token = cliente.token("paciente", "Paciente2026")

        val primera = cliente.crear(token, nueva(), clave = "local-7")
        val segunda = cliente.crear(token, nueva(), clave = "local-7")

        assertEquals(HttpStatusCode.Created, primera.status)
        assertEquals(HttpStatusCode.OK, segunda.status)
        assertEquals(primera.body<SesionTerapia>().id, segunda.body<SesionTerapia>().id)
        val total = cliente.get("/api/v1/sesiones") { bearerAuth(token) }.headers["X-Total-Count"]
        assertEquals("3", total)
    }

    @Test
    fun `misma hora de inicio sin clave responde 409 y dolor fuera de escala 422`() = testApplication {
        configure()
        val cliente = clienteJson()
        val token = cliente.token("paciente", "Paciente2026")

        cliente.crear(token, nueva())
        assertEquals(HttpStatusCode.Conflict, cliente.crear(token, nueva()).status)
        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            cliente.crear(token, nueva(inicio = "2026-10-14T10:30:00", dolorDespues = 11)).status,
        )
    }

    @Test
    fun `JSON mal formado responde 400`() = testApplication {
        configure()
        val cliente = clienteJson()
        val token = cliente.token("paciente", "Paciente2026")
        val respuesta = cliente.post("/api/v1/sesiones") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"idRutina": "no es número"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, respuesta.status)
    }

    @Test
    fun `PATCH cambia solo la nota y PUT reemplaza todo`() = testApplication {
        configure()
        val cliente = clienteJson()
        val token = cliente.token("paciente", "Paciente2026")
        val creada = cliente.crear(token, nueva()).body<SesionTerapia>()

        val parcheada = cliente.patch("/api/v1/sesiones/${creada.id}") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ModificarSesionSolicitud(observacion = "Sentí la rodilla más firme"))
        }.body<SesionTerapia>()
        assertEquals("Sentí la rodilla más firme", parcheada.observacion)
        assertEquals(creada.dolorDespues, parcheada.dolorDespues) // lo que no se mandó se conserva
        assertEquals(creada.dificultad, parcheada.dificultad)

        val reemplazada = cliente.put("/api/v1/sesiones/${creada.id}") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ReemplazarSesionSolicitud(dolorAntes = 3, dolorDespues = 8, dificultad = Dificultad.ALTA, observacion = null))
        }.body<SesionTerapia>()
        assertEquals(8, reemplazada.dolorDespues)
        assertEquals(Dificultad.ALTA, reemplazada.dificultad)
        assertNull(reemplazada.observacion) // PUT reemplaza: lo que va nulo queda vacío
        assertEquals(Semaforo.ROJO, reemplazada.semaforo) // dolor 8 ≥ umbral 7
    }

    @Test
    fun `DELETE responde 204 y la segunda vez 404`() = testApplication {
        configure()
        val cliente = clienteJson()
        val token = cliente.token("paciente", "Paciente2026")
        val creada = cliente.crear(token, nueva()).body<SesionTerapia>()

        assertEquals(HttpStatusCode.NoContent, cliente.delete("/api/v1/sesiones/${creada.id}") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, cliente.delete("/api/v1/sesiones/${creada.id}") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, cliente.get("/api/v1/sesiones/${creada.id}") { bearerAuth(token) }.status)
    }

    @Test
    fun `cada quien ve solo las sesiones que le corresponden`() = testApplication {
        configure()
        val cliente = clienteJson()
        val paciente = cliente.token("paciente", "Paciente2026")
        val fisio = cliente.token("fisio", "Fisio2026")   // asignó la rutina del paciente 1
        val fisio2 = cliente.token("fisio2", "Fisio2026") // no lo atiende
        val admin = cliente.token("admin", "Admin2026")

        assertEquals(HttpStatusCode.Forbidden, cliente.get("/api/v1/sesiones?idPaciente=2") { bearerAuth(paciente) }.status)
        assertEquals(HttpStatusCode.OK, cliente.get("/api/v1/sesiones?idPaciente=1") { bearerAuth(fisio) }.status)
        assertEquals(HttpStatusCode.Forbidden, cliente.get("/api/v1/sesiones?idPaciente=1") { bearerAuth(fisio2) }.status)
        assertEquals(HttpStatusCode.Forbidden, cliente.get("/api/v1/sesiones") { bearerAuth(admin) }.status)
        // El fisioterapeuta puede consultar, pero no registrar sesiones a nombre del paciente.
        assertEquals(HttpStatusCode.Forbidden, cliente.crear(fisio, nueva()).status)
    }

    @Test
    fun `el resumen medido con sensores se registra con 201`() = testApplication {
        configure()
        val cliente = clienteJson()
        val token = cliente.token("paciente", "Paciente2026")
        val respuesta = cliente.post("/api/v1/resultados-ejercicio") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ResultadoEjercicioSolicitud(1, repeticiones = 30, repeticionesObjetivo = 30, anguloMaximo = 92, anguloObjetivo = 90, duracionSegundos = 140))
        }
        assertEquals(HttpStatusCode.Created, respuesta.status)
        assertTrue(respuesta.body<ResultadoEjercicio>().cumpleObjetivo)

        val fuera = cliente.post("/api/v1/resultados-ejercicio") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ResultadoEjercicioSolicitud(99, 5, 10, 40, null, 20))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, fuera.status) // el ejercicio 99 no está en su rutina
    }
}
