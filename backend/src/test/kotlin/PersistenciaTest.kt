package com.terapid.physiogestion

import com.terapid.physiogestion.datos.AlmacenJdbc
import com.terapid.physiogestion.modelos.Cita
import com.terapid.physiogestion.modelos.CitaSolicitud
import com.terapid.physiogestion.modelos.CrearSesionSolicitud
import com.terapid.physiogestion.modelos.Dificultad
import com.terapid.physiogestion.modelos.EjercicioRealizado
import com.terapid.physiogestion.modelos.EstadoServidor
import com.terapid.physiogestion.modelos.LoginRespuesta
import com.terapid.physiogestion.modelos.LoginSolicitud
import com.terapid.physiogestion.modelos.RegistroSolicitud
import com.terapid.physiogestion.modelos.SesionTerapia
import com.terapid.physiogestion.modelos.VerificarCorreoSolicitud
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SRV-08: lo registrado sobrevive a un reinicio del servidor. Se prueba con SQLite
 * (mismo SQL que PostgreSQL) arrancando el servidor dos veces sobre el mismo archivo.
 */
class PersistenciaTest {
    private val archivo = File.createTempFile("physiogestion-servidor", ".db")
    private val buzon = BuzonPrueba()

    @AfterTest
    fun borrar() {
        archivo.delete()
    }

    private fun ApplicationTestBuilder.arrancar(): HttpClient {
        val almacen = AlmacenJdbc("jdbc:sqlite:${archivo.absolutePath}")
        environment { config = MapApplicationConfig() }
        application { modulo(correo = buzon, almacenDocumentos = almacen, limitePorMinuto = 1_000) }
        return createClient { install(ContentNegotiation) { json() } }
    }

    private suspend fun HttpClient.json(ruta: String, cuerpo: Any, token: String? = null) = post(ruta) {
        contentType(ContentType.Application.Json)
        token?.let { bearerAuth(it) }
        setBody(cuerpo)
    }

    @Test
    fun `cuentas, tokens y sesiones sobreviven al reinicio`() {
        var token = ""
        var idSesion = 0
        var dia = java.time.LocalDate.now(java.time.ZoneId.of("America/Mexico_City")).plusDays(1)
        if (dia.dayOfWeek == java.time.DayOfWeek.SUNDAY) dia = dia.plusDays(1)
        testApplication {
            val c = arrancar()
            c.json("/api/v1/auth/registro", RegistroSolicitud("DEMO-2026", "maria.lopez", "maria@correo.com", "Rodilla2026", true))
            c.json("/api/v1/auth/verificar-correo", VerificarCorreoSolicitud("maria@correo.com", buzon.ultimoCodigo("maria@correo.com")))
            token = c.json("/api/v1/auth/login", LoginSolicitud("maria.lopez", "Rodilla2026")).body<LoginRespuesta>().token
            val creada = c.json(
                "/api/v1/sesiones",
                CrearSesionSolicitud(
                    idRutina = 102,
                    fechaHoraInicio = "2026-11-04T09:00:00",
                    fechaHoraFin = "2026-11-04T09:25:00",
                    dolorAntes = 4,
                    dolorDespues = 3,
                    dificultad = Dificultad.MEDIA,
                    ejercicios = listOf(EjercicioRealizado(1, 10), EjercicioRealizado(2, 12)),
                ),
                token,
            )
            assertEquals(HttpStatusCode.Created, creada.status)
            idSesion = creada.body<SesionTerapia>().id
            assertEquals("sqlite", c.get("/api/v1/salud").body<EstadoServidor>().baseDatos)
            // También se guardan la cita agendada por recepción y la rutina que modifica el fisioterapeuta.
            val recepcion = c.json("/api/v1/auth/login", LoginSolicitud("recepcion", "Recepcion2026")).body<LoginRespuesta>().token
            val cita = c.json("/api/v1/citas", CitaSolicitud("", 2, 3, dia.toString(), "10:00", 50, "Valoración inicial"), recepcion)
            assertEquals(HttpStatusCode.Created, cita.status)
        }

        // Segundo arranque, como cuando Railway despierta el contenedor.
        testApplication {
            val c = arrancar()
            assertEquals(HttpStatusCode.OK, c.json("/api/v1/auth/login", LoginSolicitud("maria.lopez", "Rodilla2026")).status)
            val sesion = c.get("/api/v1/sesiones/$idSesion") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, sesion.status, "el token anterior sigue valiendo y la sesión sigue ahí")
            // La invitación ya usada no se «revive» con los datos de ejemplo.
            val otra = c.json("/api/v1/auth/registro", RegistroSolicitud("DEMO-2026", "otra.cuenta", "otra@correo.com", "Rodilla2026", true))
            assertEquals(HttpStatusCode.UnprocessableEntity, otra.status)
            val recepcion = c.json("/api/v1/auth/login", LoginSolicitud("recepcion", "Recepcion2026")).body<LoginRespuesta>().token
            val agenda = c.get("/api/v1/citas?fecha=$dia") { bearerAuth(recepcion) }.body<List<Cita>>()
            assertEquals("María López Hernández", agenda.single { it.motivo == "Valoración inicial" }.paciente)
            // Las citas de ejemplo no se duplican al volver a arrancar.
            assertEquals(1, c.get("/api/v1/pacientes") { bearerAuth(recepcion) }.body<List<com.terapid.physiogestion.modelos.PacienteResumen>>().count { it.pacienteId == 2 })
        }
    }

    @Test
    fun `la URL de Railway se convierte a JDBC`() {
        val (jdbc, usuario, contrasena) = AlmacenJdbc.convertirUrl("postgresql://postgres:s3cr%40to@postgres.railway.internal:5432/railway")
        assertEquals("jdbc:postgresql://postgres.railway.internal:5432/railway", jdbc)
        assertEquals("postgres", usuario)
        assertEquals("s3cr@to", contrasena)
        assertTrue(AlmacenJdbc.convertirUrl("postgres://u:p@h/db").first.startsWith("jdbc:postgresql://h:5432/db"))
    }
}
