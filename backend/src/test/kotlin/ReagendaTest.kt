package com.terapid.physiogestion

import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.modelos.BandejaAvisos
import com.terapid.physiogestion.modelos.Cita
import com.terapid.physiogestion.modelos.CitaSolicitud
import com.terapid.physiogestion.modelos.ErrorRespuesta
import com.terapid.physiogestion.modelos.EstadoCita
import com.terapid.physiogestion.modelos.EstadoSolicitud
import com.terapid.physiogestion.modelos.FisioterapeutaResumen
import com.terapid.physiogestion.modelos.LoginRespuesta
import com.terapid.physiogestion.modelos.LoginSolicitud
import com.terapid.physiogestion.modelos.NuevaSolicitudReagenda
import com.terapid.physiogestion.modelos.ReagendarSolicitud
import com.terapid.physiogestion.modelos.ReprogramarCitaSolicitud
import com.terapid.physiogestion.modelos.ResolverSolicitud
import com.terapid.physiogestion.modelos.SolicitudReagenda
import com.terapid.physiogestion.modelos.TipoAviso
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
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
 * Reagendar: el paciente mueve su cita o pide el cambio a recepción; el fisioterapeuta
 * que no puede atender la mueve o se la pasa a otro. Reloj fijo: miércoles 04/11/2026, 09:00.
 */
class ReagendaTest {
    private val reloj = RelojPrueba(Instant.parse("2026-11-04T15:00:00Z"))

    private fun ApplicationTestBuilder.arrancar(): HttpClient {
        environment { config = MapApplicationConfig() }
        application { modulo(correo = BuzonPrueba(), reloj = reloj, almacenDocumentos = AlmacenEnMemoria(), limitePorMinuto = 1_000) }
        return createClient { install(ContentNegotiation) { json() } }
    }

    private suspend fun HttpClient.token(usuario: String, contrasena: String) = post("/api/v1/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginSolicitud(usuario, contrasena))
    }.body<LoginRespuesta>().token

    private suspend fun HttpClient.enviar(ruta: String, cuerpo: Any, token: String): HttpResponse = post(ruta) {
        contentType(ContentType.Application.Json)
        bearerAuth(token)
        setBody(cuerpo)
    }

    private suspend fun HttpClient.parche(ruta: String, cuerpo: Any, token: String): HttpResponse = patch(ruta) {
        contentType(ContentType.Application.Json)
        bearerAuth(token)
        setBody(cuerpo)
    }

    private suspend fun HttpClient.avisos(token: String) = get("/api/v1/avisos") { bearerAuth(token) }.body<BandejaAvisos>().avisos

    /** Cita de Jorge (paciente 1) con Carlos (fisio 2). */
    private suspend fun HttpClient.citaDeJorge(recepcion: String, fecha: String, hora: String): Cita =
        enviar("/api/v1/citas", CitaSolicitud("", 1, 2, fecha, hora, 50, "Rehabilitación de rodilla"), recepcion).body()

    @Test
    fun `el paciente mueve su cita a una hora libre hasta dos veces`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val paciente = c.token("paciente", "Paciente2026")
        val cita = c.citaDeJorge(recepcion, "2026-11-06", "11:00")

        val libres = c.get("/api/v1/citas/${cita.id}/libres?fecha=2026-11-06") { bearerAuth(paciente) }.body<List<String>>()
        assertTrue("13:00" in libres)

        val movida = c.enviar("/api/v1/citas/${cita.id}/reagendar", ReagendarSolicitud("2026-11-06", "13:00"), paciente).body<Cita>()
        assertEquals("13:00", movida.hora)
        assertEquals(EstadoCita.PENDIENTE, movida.estado)
        assertEquals(1, movida.reagendasPaciente)
        assertEquals("Reagendada por el paciente (antes 06/11/2026 11:00)", movida.nota)

        // Se avisa al fisioterapeuta y a recepción, no al propio paciente.
        assertTrue(c.avisos(c.token("fisio", "Fisio2026")).any { it.tipo == TipoAviso.CITA_REPROGRAMADA && it.citaId == cita.id })
        assertTrue(c.avisos(recepcion).any { it.titulo.startsWith("Cita movida") && it.citaId == cita.id })
        assertTrue(c.avisos(paciente).none { it.tipo == TipoAviso.CITA_REPROGRAMADA && it.citaId == cita.id })

        assertEquals(HttpStatusCode.OK, c.enviar("/api/v1/citas/${cita.id}/reagendar", ReagendarSolicitud("2026-11-07", "10:00"), paciente).status)
        val tercera = c.enviar("/api/v1/citas/${cita.id}/reagendar", ReagendarSolicitud("2026-11-09", "10:00"), paciente)
        assertEquals(HttpStatusCode.UnprocessableEntity, tercera.status)
        assertEquals("REQUIERE_SOLICITUD", tercera.body<ErrorRespuesta>().codigo)
    }

    @Test
    fun `con menos de 24 horas el paciente manda una solicitud y recepcion la resuelve`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val paciente = c.token("paciente", "Paciente2026")
        val cita = c.citaDeJorge(recepcion, "2026-11-05", "08:00") // faltan 23 horas

        val directo = c.enviar("/api/v1/citas/${cita.id}/reagendar", ReagendarSolicitud("2026-11-06", "10:00"), paciente)
        assertEquals("REQUIERE_SOLICITUD", directo.body<ErrorRespuesta>().codigo)

        val enviada = c.enviar("/api/v1/citas/${cita.id}/solicitudes", NuevaSolicitudReagenda("2026-11-06", "10:00", "Me surgió trabajo temprano"), paciente)
        assertEquals(HttpStatusCode.Created, enviada.status)
        val solicitud = enviada.body<SolicitudReagenda>()
        assertEquals(EstadoSolicitud.PENDIENTE, solicitud.estado)
        // Solo una pendiente por cita.
        assertEquals(HttpStatusCode.Conflict, c.enviar("/api/v1/citas/${cita.id}/solicitudes", NuevaSolicitudReagenda(mensaje = "Otra vez"), paciente).status)

        assertTrue(c.avisos(recepcion).any { it.tipo == TipoAviso.SOLICITUD_REAGENDA })
        val pendientes = c.get("/api/v1/solicitudes-reagenda?estado=PENDIENTE") { bearerAuth(recepcion) }.body<List<SolicitudReagenda>>()
        assertEquals(listOf(solicitud.id), pendientes.map { it.id })
        // El paciente no puede resolverla.
        assertEquals(HttpStatusCode.Forbidden, c.enviar("/api/v1/solicitudes-reagenda/${solicitud.id}/resolver", ResolverSolicitud(aprobar = true), paciente).status)

        val aprobada = c.enviar("/api/v1/solicitudes-reagenda/${solicitud.id}/resolver", ResolverSolicitud(aprobar = true), recepcion).body<SolicitudReagenda>()
        assertEquals(EstadoSolicitud.APROBADA, aprobada.estado)
        val movida = c.get("/api/v1/citas") { bearerAuth(paciente) }.body<List<Cita>>().first { it.id == cita.id }
        assertEquals("2026-11-06" to "10:00", movida.fecha to movida.hora)
        assertTrue(c.avisos(paciente).any { it.tipo == TipoAviso.SOLICITUD_RESUELTA && it.titulo.startsWith("Aceptamos") })

        // Rechazar exige un motivo, que le llega al paciente.
        val otra = c.enviar("/api/v1/citas/${cita.id}/solicitudes", NuevaSolicitudReagenda(mensaje = "Mejor por la tarde"), paciente).body<SolicitudReagenda>()
        assertEquals(HttpStatusCode.UnprocessableEntity, c.enviar("/api/v1/solicitudes-reagenda/${otra.id}/resolver", ResolverSolicitud(aprobar = false), recepcion).status)
        val rechazada = c.enviar("/api/v1/solicitudes-reagenda/${otra.id}/resolver", ResolverSolicitud(aprobar = false, nota = "No hay horas libres esa tarde"), recepcion).body<SolicitudReagenda>()
        assertEquals(EstadoSolicitud.RECHAZADA, rechazada.estado)
        assertTrue(c.avisos(paciente).any { it.titulo == "No se pudo cambiar tu cita" && it.mensaje.contains("No hay horas libres") })
    }

    @Test
    fun `el paciente no puede mover citas de otra persona`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val ajena = c.enviar("/api/v1/citas", CitaSolicitud("Patricia Hernández Gómez", null, 2, "2026-11-06", "11:00", 50, "Dolor lumbar"), recepcion).body<Cita>()
        val paciente = c.token("paciente", "Paciente2026")
        assertEquals(HttpStatusCode.Forbidden, c.enviar("/api/v1/citas/${ajena.id}/reagendar", ReagendarSolicitud("2026-11-06", "13:00"), paciente).status)
        assertEquals(HttpStatusCode.Forbidden, c.get("/api/v1/citas/${ajena.id}/libres?fecha=2026-11-06") { bearerAuth(paciente) }.status)
    }

    @Test
    fun `el fisioterapeuta que no puede atender mueve la cita o se la pasa a otro`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val carlos = c.token("fisio", "Fisio2026")
        val paciente = c.token("paciente", "Paciente2026")
        val cita = c.citaDeJorge(recepcion, "2026-11-06", "11:00")

        // Sin explicar por qué, no.
        assertEquals(HttpStatusCode.UnprocessableEntity, c.parche("/api/v1/citas/${cita.id}", ReprogramarCitaSolicitud(hora = "16:00"), carlos).status)
        val movida = c.parche("/api/v1/citas/${cita.id}", ReprogramarCitaSolicitud(hora = "16:00", nota = "Tengo capacitación en la mañana"), carlos).body<Cita>()
        assertEquals("16:00", movida.hora)
        assertTrue(movida.nota!!.startsWith("Reprogramada por Ft. Carlos Ortega Luna: Tengo capacitación"))
        assertTrue(c.avisos(paciente).any { it.titulo == "Tu cita cambió de horario" && it.mensaje.contains("capacitación") })
        assertTrue(c.avisos(recepcion).any { it.titulo.startsWith("Cita movida") })

        // Fisioterapeutas libres a esa hora y pase a Mariana (fisio 3) en el mismo horario.
        val libres = c.get("/api/v1/citas/${cita.id}/disponibles") { bearerAuth(carlos) }.body<List<FisioterapeutaResumen>>()
        assertTrue(libres.any { it.id == 3 } && libres.none { it.id == 2 })
        val pasada = c.parche("/api/v1/citas/${cita.id}", ReprogramarCitaSolicitud(fisioterapeutaId = 3, nota = "Me enfermé"), carlos).body<Cita>()
        assertEquals(3, pasada.fisioterapeutaId)
        assertEquals("16:00", pasada.hora)
        assertTrue(c.avisos(paciente).any { it.titulo == "Cambió tu fisioterapeuta" })
        assertTrue(c.avisos(c.token("fisio2", "Fisio2026")).any { it.titulo.startsWith("Te pasaron una cita") })

        // Ya no es suya: no la puede volver a tocar.
        assertEquals(HttpStatusCode.Forbidden, c.parche("/api/v1/citas/${cita.id}", ReprogramarCitaSolicitud(hora = "17:00", nota = "Ya puedo"), carlos).status)
        // El paciente no usa la ruta del personal.
        assertEquals(HttpStatusCode.Forbidden, c.parche("/api/v1/citas/${cita.id}", ReprogramarCitaSolicitud(hora = "17:00"), paciente).status)
    }
}
