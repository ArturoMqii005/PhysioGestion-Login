package com.terapid.physiogestion

import com.terapid.physiogestion.datos.AlmacenDocumentos
import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.modelos.AsignacionSolicitud
import com.terapid.physiogestion.modelos.BandejaAvisos
import com.terapid.physiogestion.modelos.Cita
import com.terapid.physiogestion.modelos.CitaSolicitud
import com.terapid.physiogestion.modelos.CrearSesionSolicitud
import com.terapid.physiogestion.modelos.Dificultad
import com.terapid.physiogestion.modelos.EjercicioAsignado
import com.terapid.physiogestion.modelos.EjercicioRealizado
import com.terapid.physiogestion.modelos.EstadoCita
import com.terapid.physiogestion.modelos.EstadoCitaSolicitud
import com.terapid.physiogestion.modelos.LoginRespuesta
import com.terapid.physiogestion.modelos.LoginSolicitud
import com.terapid.physiogestion.modelos.MarcarLeidosSolicitud
import com.terapid.physiogestion.modelos.ModificarSesionSolicitud
import com.terapid.physiogestion.modelos.ReprogramarCitaSolicitud
import com.terapid.physiogestion.modelos.RutinaSolicitud
import com.terapid.physiogestion.modelos.SesionTerapia
import com.terapid.physiogestion.modelos.TipoAviso
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Avisos (NOT-01..04 del lado del servidor): quién recibe cada aviso, que no se
 * dupliquen, la bandeja con «desde» y marcar como leídos. Usuarios de ejemplo:
 * fisio (id 2) atiende a Jorge (paciente 1, cuenta 5, límite de dolor 7);
 * fisio2 (id 3) atiende a María (paciente 2); recepcion es la cuenta 4.
 */
class AvisosTest {
    private val reloj = RelojPrueba(Instant.parse("2026-11-04T15:00:00Z"))
    private val manana = "2026-11-05"

    private fun ApplicationTestBuilder.arrancar(almacen: AlmacenDocumentos = AlmacenEnMemoria()): HttpClient {
        environment { config = MapApplicationConfig() }
        application { modulo(correo = BuzonPrueba(), reloj = reloj, almacenDocumentos = almacen, limitePorMinuto = 1_000) }
        return createClient { install(ContentNegotiation) { json() } }
    }

    private suspend fun HttpClient.token(usuario: String, contrasena: String) = post("/api/v1/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginSolicitud(usuario, contrasena))
    }.body<LoginRespuesta>().token

    private suspend fun HttpClient.enviar(ruta: String, cuerpo: Any, token: String, clave: String? = null): HttpResponse = post(ruta) {
        contentType(ContentType.Application.Json)
        bearerAuth(token)
        clave?.let { header("Idempotency-Key", it) }
        setBody(cuerpo)
    }

    private suspend fun HttpClient.bandeja(token: String, desde: Int? = null): BandejaAvisos =
        get("/api/v1/avisos" + (desde?.let { "?desde=$it" } ?: "")) { bearerAuth(token) }.body()

    private fun sesion(inicio: String, dolorAntes: Int, dolorDespues: Int, observacion: String? = null) = CrearSesionSolicitud(
        idRutina = 101,
        fechaHoraInicio = "2026-11-04T$inicio:00",
        fechaHoraFin = "2026-11-04T$inicio:30",
        dolorAntes = dolorAntes,
        dolorDespues = dolorDespues,
        dificultad = Dificultad.MEDIA,
        observacion = observacion,
        ejercicios = (1..4).map { EjercicioRealizado(it, 10) },
    )

    @Test
    fun `una sesion en rojo avisa solo a su fisioterapeuta y una sola vez`() = testApplication {
        val c = arrancar()
        val paciente = c.token("paciente", "Paciente2026")
        val fisio = c.token("fisio", "Fisio2026")
        val fisio2 = c.token("fisio2", "Fisio2026")

        // Dolor 3 después (límite 7): no hay aviso.
        assertEquals(HttpStatusCode.Created, c.enviar("/api/v1/sesiones", sesion("08:00", 2, 3), paciente).status)
        assertEquals(0, c.bandeja(fisio).avisos.size)

        // Dolor 8: aviso al fisioterapeuta de Jorge con el dato y el comentario.
        val roja = c.enviar("/api/v1/sesiones", sesion("18:00", 4, 8, "Me dolió al bajar"), paciente, clave = "s-1")
        assertEquals(HttpStatusCode.Created, roja.status)
        val bandeja = c.bandeja(fisio)
        assertEquals(1, bandeja.sinLeer)
        val aviso = bandeja.avisos.single()
        assertEquals(TipoAviso.DOLOR_ALTO, aviso.tipo)
        assertEquals(1, aviso.pacienteId)
        assertEquals(roja.body<SesionTerapia>().id, aviso.sesionId)
        assertEquals("Dolor alto: Jorge Ramírez Cruz", aviso.titulo)
        assertTrue("8/10" in aviso.mensaje && "límite es 7" in aviso.mensaje && "Me dolió al bajar" in aviso.mensaje, aviso.mensaje)

        // El reintento con la misma clave no crea otra sesión ni otro aviso.
        assertEquals(HttpStatusCode.OK, c.enviar("/api/v1/sesiones", sesion("18:00", 4, 8, "Me dolió al bajar"), paciente, clave = "s-1").status)
        assertEquals(1, c.bandeja(fisio).avisos.size)

        // Otra fisioterapeuta y el propio paciente no reciben nada.
        assertEquals(0, c.bandeja(fisio2).avisos.size)
        assertEquals(0, c.bandeja(paciente).avisos.size)
    }

    @Test
    fun `si el paciente corrige el dolor y la sesion pasa a rojo tambien se avisa`() = testApplication {
        val c = arrancar()
        val paciente = c.token("paciente", "Paciente2026")
        val fisio = c.token("fisio", "Fisio2026")
        val creada = c.enviar("/api/v1/sesiones", sesion("09:00", 3, 4), paciente).body<SesionTerapia>()
        assertEquals(0, c.bandeja(fisio).avisos.size)

        suspend fun corregir(dolor: Int) = c.patch("/api/v1/sesiones/${creada.id}") {
            contentType(ContentType.Application.Json)
            bearerAuth(paciente)
            setBody(ModificarSesionSolicitud(dolorDespues = dolor))
        }.status
        assertEquals(HttpStatusCode.OK, corregir(9))
        assertEquals(TipoAviso.DOLOR_ALTO, c.bandeja(fisio).avisos.single().tipo)
        // Bajarlo y volver a subirlo no repite el aviso de esa sesión.
        corregir(2)
        corregir(9)
        assertEquals(1, c.bandeja(fisio).avisos.size)
    }

    @Test
    fun `las citas avisan al paciente y la cancelacion del paciente avisa al personal`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val paciente = c.token("paciente", "Paciente2026")
        val fisio = c.token("fisio", "Fisio2026")

        val cita = c.enviar(
            "/api/v1/citas",
            CitaSolicitud("Jorge Ramírez Cruz", 1, 2, manana, "10:00", 50, "Revisión de rodilla"),
            recepcion,
        ).body<Cita>()
        val nueva = c.bandeja(paciente).avisos.single()
        assertEquals(TipoAviso.CITA_NUEVA, nueva.tipo)
        assertTrue("10:00" in nueva.mensaje && "Ft. Carlos Ortega Luna" in nueva.mensaje, nueva.mensaje)

        // Reprogramar: avisa al paciente y al fisioterapeuta.
        val movida = c.patch("/api/v1/citas/${cita.id}") {
            contentType(ContentType.Application.Json)
            bearerAuth(recepcion)
            setBody(ReprogramarCitaSolicitud(hora = "12:00"))
        }
        assertEquals(HttpStatusCode.OK, movida.status)
        assertEquals(TipoAviso.CITA_REPROGRAMADA, c.bandeja(paciente).avisos.first().tipo)
        assertEquals(TipoAviso.CITA_REPROGRAMADA, c.bandeja(fisio).avisos.first().tipo)

        // El paciente cancela: avisa al fisioterapeuta y a recepción, no a él mismo.
        val antesPaciente = c.bandeja(paciente).avisos.size
        assertEquals(HttpStatusCode.OK, c.enviar("/api/v1/citas/${cita.id}/estado", EstadoCitaSolicitud(EstadoCita.CANCELADA), paciente).status)
        assertEquals(TipoAviso.CITA_CANCELADA, c.bandeja(fisio).avisos.first().tipo)
        val aRecepcion = c.bandeja(recepcion).avisos.single()
        assertEquals("Cita cancelada: Jorge Ramírez Cruz", aRecepcion.titulo)
        assertEquals(antesPaciente, c.bandeja(paciente).avisos.size)
    }

    @Test
    fun `la clinica cancela y el paciente recibe el motivo`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val paciente = c.token("paciente", "Paciente2026")
        val cita = c.enviar("/api/v1/citas", CitaSolicitud("Jorge Ramírez Cruz", 1, 2, manana, "16:00", 50, "Revisión"), recepcion).body<Cita>()
        c.enviar("/api/v1/citas/${cita.id}/estado", EstadoCitaSolicitud(EstadoCita.CANCELADA, "El fisioterapeuta tiene curso"), recepcion)
        val aviso = c.bandeja(paciente).avisos.first()
        assertEquals(TipoAviso.CITA_CANCELADA, aviso.tipo)
        assertTrue("El fisioterapeuta tiene curso" in aviso.mensaje, aviso.mensaje)
        assertEquals(0, c.bandeja(recepcion).avisos.size)
    }

    @Test
    fun `rutina nueva avisa al paciente y la asignacion avisa al fisioterapeuta`() = testApplication {
        val c = arrancar()
        val fisio = c.token("fisio", "Fisio2026")
        val paciente = c.token("paciente", "Paciente2026")
        val recepcion = c.token("recepcion", "Recepcion2026")

        val puesta = c.put("/api/v1/pacientes/1/rutina") {
            contentType(ContentType.Application.Json)
            bearerAuth(fisio)
            setBody(RutinaSolicitud("Subir escaleras sin dolor", 6, listOf(EjercicioAsignado(1, 3, 12, 45), EjercicioAsignado(5, 3, 10, 30))))
        }
        assertEquals(HttpStatusCode.OK, puesta.status)
        val aviso = c.bandeja(paciente).avisos.single()
        assertEquals(TipoAviso.RUTINA_ACTUALIZADA, aviso.tipo)
        assertTrue("2 ejercicios" in aviso.mensaje, aviso.mensaje)

        // María (paciente 2) pasa de fisio2 a fisio: él recibe el aviso.
        val asignada = c.patch("/api/v1/pacientes/2/fisioterapeuta") {
            contentType(ContentType.Application.Json)
            bearerAuth(recepcion)
            setBody(AsignacionSolicitud(2))
        }
        assertEquals(HttpStatusCode.OK, asignada.status)
        val nuevo = c.bandeja(fisio).avisos.first()
        assertEquals(TipoAviso.PACIENTE_ASIGNADO, nuevo.tipo)
        assertEquals(2, nuevo.pacienteId)
    }

    @Test
    fun `desde filtra lo nuevo y solo se marcan como leidos los avisos propios`() = testApplication {
        val c = arrancar()
        val paciente = c.token("paciente", "Paciente2026")
        val fisio = c.token("fisio", "Fisio2026")
        val recepcion = c.token("recepcion", "Recepcion2026")
        c.enviar("/api/v1/sesiones", sesion("07:00", 5, 9), paciente)
        c.enviar("/api/v1/sesiones", sesion("19:00", 5, 8), paciente)
        val todos = c.bandeja(fisio)
        assertEquals(2, todos.sinLeer)
        val (reciente, anterior) = todos.avisos
        assertEquals(todos.ultimoId, reciente.id)
        assertEquals(listOf(reciente.id), c.bandeja(fisio, desde = anterior.id).avisos.map { it.id })
        assertEquals(0, c.bandeja(fisio, desde = todos.ultimoId).avisos.size)

        // Recepción no puede marcar los avisos del fisioterapeuta.
        c.enviar("/api/v1/avisos/leidos", MarcarLeidosSolicitud(ids = listOf(reciente.id)), recepcion)
        assertEquals(2, c.bandeja(fisio).sinLeer)

        val trasUno = c.enviar("/api/v1/avisos/leidos", MarcarLeidosSolicitud(ids = listOf(anterior.id)), fisio).body<BandejaAvisos>()
        assertEquals(1, trasUno.sinLeer)
        assertTrue(trasUno.avisos.first { it.id == anterior.id }.leido)
        assertEquals(0, c.enviar("/api/v1/avisos/leidos", MarcarLeidosSolicitud(todos = true), fisio).body<BandejaAvisos>().sinLeer)
        assertEquals(HttpStatusCode.BadRequest, c.enviar("/api/v1/avisos/leidos", MarcarLeidosSolicitud(), fisio).status)
        assertEquals(HttpStatusCode.Unauthorized, c.get("/api/v1/avisos").status)
    }

    @Test
    fun `los avisos sobreviven a un reinicio del servidor`() {
        val almacen = AlmacenEnMemoria()
        testApplication {
            val c = arrancar(almacen)
            c.enviar("/api/v1/sesiones", sesion("18:00", 4, 8), c.token("paciente", "Paciente2026"))
        }
        testApplication {
            val c = arrancar(almacen)
            val bandeja = c.bandeja(c.token("fisio", "Fisio2026"))
            assertEquals(1, bandeja.sinLeer)
            assertEquals(TipoAviso.DOLOR_ALTO, bandeja.avisos.single().tipo)
        }
    }
}
