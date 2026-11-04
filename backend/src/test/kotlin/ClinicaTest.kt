package com.terapid.physiogestion

import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.modelos.AsignacionSolicitud
import com.terapid.physiogestion.modelos.Cita
import com.terapid.physiogestion.modelos.CitaSolicitud
import com.terapid.physiogestion.modelos.EjercicioAsignado
import com.terapid.physiogestion.modelos.ErrorRespuesta
import com.terapid.physiogestion.modelos.EstadoCita
import com.terapid.physiogestion.modelos.EstadoCitaSolicitud
import com.terapid.physiogestion.modelos.EstadoCuentaSolicitud
import com.terapid.physiogestion.modelos.FichaPaciente
import com.terapid.physiogestion.modelos.Invitacion
import com.terapid.physiogestion.modelos.InvitacionSolicitud
import com.terapid.physiogestion.modelos.LoginRespuesta
import com.terapid.physiogestion.modelos.LoginSolicitud
import com.terapid.physiogestion.modelos.PacienteResumen
import com.terapid.physiogestion.modelos.ReprogramarCitaSolicitud
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.Rutina
import com.terapid.physiogestion.modelos.RutinaSolicitud
import com.terapid.physiogestion.modelos.UsuarioAdministrado
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fase 2: citas con fecha (agendar, reprogramar, cancelar, asistencia), pacientes,
 * asignación, rutinas editables y edición de usuarios. El reloj está fijo en el
 * miércoles 04/11/2026 a las 09:00 de Tulancingo.
 */
class ClinicaTest {
    private val reloj = RelojPrueba(Instant.parse("2026-11-04T15:00:00Z"))
    private val hoy = "2026-11-04"
    private val manana = "2026-11-05"

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

    private fun cita(hora: String, fisio: Int = 2, fecha: String = manana, pacienteId: Int? = null, duracion: Int = 50) =
        CitaSolicitud("Patricia Hernández Gómez", pacienteId, fisio, fecha, hora, duracion, "Dolor lumbar")

    @Test
    fun `recepcion agenda sin choques y las horas ocupadas desaparecen`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val creada = c.enviar("/api/v1/citas", cita("10:00"), recepcion)
        assertEquals(HttpStatusCode.Created, creada.status)
        assertEquals(EstadoCita.PENDIENTE, creada.body<Cita>().estado)

        // 10:30 choca con la de 10:00 (dura 50 minutos) del mismo fisioterapeuta…
        val choque = c.enviar("/api/v1/citas", cita("10:30"), recepcion)
        assertEquals(HttpStatusCode.Conflict, choque.status)
        assertTrue(choque.body<ErrorRespuesta>().mensaje.contains("10:00 a 10:50"))
        // …pero otra fisioterapeuta sí está libre a esa hora.
        assertEquals(HttpStatusCode.Created, c.enviar("/api/v1/citas", cita("10:30", fisio = 3), recepcion).status)

        val libres = c.get("/api/v1/citas/libres?fisioterapeutaId=2&fecha=$manana&duracion=50") { bearerAuth(recepcion) }.body<List<String>>()
        assertFalse("10:00" in libres)
        assertFalse("10:30" in libres)
        assertTrue("11:00" in libres)
        assertTrue("07:00" in libres)
        assertFalse("20:30" in libres, "terminaría después de las 21:00")

        val agenda = c.get("/api/v1/citas?fecha=$manana") { bearerAuth(recepcion) }.body<List<Cita>>()
        assertEquals(2, agenda.size)
    }

    @Test
    fun `no se agenda en dias pasados, domingo, fuera de horario ni con duracion rara`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val casos = listOf(
            cita("10:00", fecha = "2026-11-03"),
            cita("10:00", fecha = "2026-11-08"), // domingo
            cita("06:30"),
            cita("20:30"),
            cita("08:00", fecha = hoy), // ya pasó
            cita("10:00", duracion = 40),
        )
        for (caso in casos) assertEquals(HttpStatusCode.UnprocessableEntity, c.enviar("/api/v1/citas", caso, recepcion).status, caso.toString())
        assertEquals(HttpStatusCode.Forbidden, c.enviar("/api/v1/citas", cita("10:00"), c.token("fisio", "Fisio2026")).status)
    }

    @Test
    fun `reprogramar mueve la cita, la deja pendiente y anota la fecha anterior`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val id = c.enviar("/api/v1/citas", cita("12:00"), recepcion).body<Cita>().id
        c.enviar("/api/v1/citas/$id/estado", EstadoCitaSolicitud(EstadoCita.CONFIRMADA), recepcion)

        val movida = c.parche("/api/v1/citas/$id", ReprogramarCitaSolicitud(fecha = "2026-11-06", hora = "16:30"), recepcion).body<Cita>()
        assertEquals("2026-11-06", movida.fecha)
        assertEquals("16:30", movida.hora)
        assertEquals(EstadoCita.PENDIENTE, movida.estado)
        assertEquals("Reprogramada (antes 05/11/2026 12:00)", movida.nota)

        c.enviar("/api/v1/citas/$id/estado", EstadoCitaSolicitud(EstadoCita.CANCELADA, "El paciente avisó por teléfono"), recepcion)
        assertEquals(HttpStatusCode.Conflict, c.parche("/api/v1/citas/$id", ReprogramarCitaSolicitud(hora = "17:00"), recepcion).status)
    }

    @Test
    fun `el fisioterapeuta marca asistencia solo en sus citas de hoy`() = testApplication {
        val c = arrancar()
        val fisio = c.token("fisio", "Fisio2026")
        val deHoy = c.get("/api/v1/citas") { bearerAuth(fisio) }.body<List<Cita>>()
        assertTrue(deHoy.isNotEmpty() && deHoy.all { it.fisioterapeutaId == 2 && it.fecha == hoy })
        val confirmada = deHoy.first { it.estado == EstadoCita.CONFIRMADA }

        val atendida = c.enviar("/api/v1/citas/${confirmada.id}/estado", EstadoCitaSolicitud(EstadoCita.ATENDIDA), fisio)
        assertEquals(EstadoCita.ATENDIDA, atendida.body<Cita>().estado)
        // Una cita terminada ya no cambia.
        assertEquals(HttpStatusCode.Conflict, c.enviar("/api/v1/citas/${confirmada.id}/estado", EstadoCitaSolicitud(EstadoCita.NO_ASISTIO), fisio).status)

        // No puede cancelar ni tocar citas de otra fisioterapeuta.
        val pendiente = deHoy.first { it.estado == EstadoCita.PENDIENTE }
        assertEquals(HttpStatusCode.Forbidden, c.enviar("/api/v1/citas/${pendiente.id}/estado", EstadoCitaSolicitud(EstadoCita.CANCELADA), fisio).status)
        val deMariana = c.get("/api/v1/citas") { bearerAuth(c.token("fisio2", "Fisio2026")) }.body<List<Cita>>().first()
        assertEquals(HttpStatusCode.Forbidden, c.enviar("/api/v1/citas/${deMariana.id}/estado", EstadoCitaSolicitud(EstadoCita.ATENDIDA), fisio).status)

        // Una cita de mañana todavía no se puede marcar como atendida.
        val manianaId = c.enviar("/api/v1/citas", cita("09:00"), c.token("recepcion", "Recepcion2026")).body<Cita>().id
        assertEquals(HttpStatusCode.UnprocessableEntity, c.enviar("/api/v1/citas/$manianaId/estado", EstadoCitaSolicitud(EstadoCita.ATENDIDA), fisio).status)
    }

    @Test
    fun `el paciente ve sus proximas citas y puede cancelarlas`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        c.enviar("/api/v1/citas", cita("11:00", pacienteId = 1).copy(paciente = ""), recepcion)
        c.enviar("/api/v1/citas", cita("11:00", fisio = 3), recepcion) // de otra persona

        val paciente = c.token("paciente", "Paciente2026")
        val mias = c.get("/api/v1/citas") { bearerAuth(paciente) }.body<List<Cita>>()
        assertTrue(mias.all { it.pacienteId == 1 })
        val manianaCita = mias.first { it.fecha == manana }
        assertEquals("Jorge Ramírez Cruz", manianaCita.paciente) // el nombre sale del expediente

        val cancelada = c.enviar("/api/v1/citas/${manianaCita.id}/estado", EstadoCitaSolicitud(EstadoCita.CANCELADA, "Tengo trabajo"), paciente).body<Cita>()
        assertEquals(EstadoCita.CANCELADA, cancelada.estado)
        assertEquals("Cancelada por el paciente: Tengo trabajo", cancelada.nota)
        // Al cancelarse, la hora vuelve a quedar libre.
        val libres = c.get("/api/v1/citas/libres?fisioterapeutaId=2&fecha=$manana") { bearerAuth(recepcion) }.body<List<String>>()
        assertTrue("11:00" in libres)
        // Y no puede confirmarse a sí mismo una cita.
        val otra = mias.firstOrNull { it.id != manianaCita.id && it.estado != EstadoCita.ATENDIDA }
        if (otra != null) {
            assertEquals(HttpStatusCode.Forbidden, c.enviar("/api/v1/citas/${otra.id}/estado", EstadoCitaSolicitud(EstadoCita.CONFIRMADA), paciente).status)
        }
    }

    @Test
    fun `cada fisioterapeuta ve solo a sus pacientes y recepcion no ve datos de salud`() = testApplication {
        val c = arrancar()
        val deCarlos = c.get("/api/v1/pacientes") { bearerAuth(c.token("fisio", "Fisio2026")) }.body<List<PacienteResumen>>()
        assertEquals(listOf(1), deCarlos.map { it.pacienteId })
        assertTrue(deCarlos.single().tieneRutina)

        val recepcion = c.token("recepcion", "Recepcion2026")
        val todos = c.get("/api/v1/pacientes") { bearerAuth(recepcion) }.body<List<PacienteResumen>>()
        assertEquals(setOf(1, 2), todos.map { it.pacienteId }.toSet())
        assertFalse(todos.first { it.pacienteId == 2 }.tieneCuenta) // María solo tiene su invitación

        val fichaRecepcion = c.get("/api/v1/pacientes/1") { bearerAuth(recepcion) }.body<FichaPaciente>()
        assertNull(fichaRecepcion.rutina)
        assertTrue(fichaRecepcion.sesionesRecientes.isEmpty())
        val fichaFisio = c.get("/api/v1/pacientes/1") { bearerAuth(c.token("fisio", "Fisio2026")) }.body<FichaPaciente>()
        assertEquals(101, fichaFisio.rutina?.id)
        assertTrue(fichaFisio.sesionesRecientes.isNotEmpty())
        assertEquals(HttpStatusCode.Forbidden, c.get("/api/v1/pacientes/1") { bearerAuth(c.token("fisio2", "Fisio2026")) }.status)
    }

    @Test
    fun `recepcion asigna otro fisioterapeuta y la rutina cambia de responsable`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val r = c.parche("/api/v1/pacientes/2/fisioterapeuta", AsignacionSolicitud(2), recepcion)
        assertEquals("Ft. Carlos Ortega Luna", r.body<PacienteResumen>().fisioterapeuta)

        val fisio = c.token("fisio", "Fisio2026")
        assertEquals(setOf(1, 2), c.get("/api/v1/pacientes") { bearerAuth(fisio) }.body<List<PacienteResumen>>().map { it.pacienteId }.toSet())
        assertEquals("Ft. Carlos Ortega Luna", c.get("/api/v1/pacientes/2/rutina") { bearerAuth(fisio) }.body<Rutina>().fisioterapeuta)
        assertEquals(HttpStatusCode.Forbidden, c.get("/api/v1/pacientes/2/rutina") { bearerAuth(c.token("fisio2", "Fisio2026")) }.status)
    }

    @Test
    fun `el fisioterapeuta arma la rutina con el catalogo y el paciente la recibe`() = testApplication {
        val c = arrancar()
        val fisio = c.token("fisio", "Fisio2026")
        val catalogo = c.get("/api/v1/ejercicios") { bearerAuth(fisio) }.body<List<com.terapid.physiogestion.modelos.Ejercicio>>()
        assertTrue(catalogo.size >= 12)

        val nueva = RutinaSolicitud(
            "Subir las escaleras de mi casa sin dolor",
            6,
            listOf(EjercicioAsignado(1, 3, 12, 45, 95), EjercicioAsignado(5, 3, 10, 30), EjercicioAsignado(6, 2, 8, 60)),
        )
        val guardada = c.put("/api/v1/pacientes/1/rutina") {
            contentType(ContentType.Application.Json)
            bearerAuth(fisio)
            setBody(nueva)
        }.body<Rutina>()
        assertEquals(101, guardada.id) // conserva el id: las sesiones anteriores siguen ligadas
        assertEquals(listOf(1, 5, 6), guardada.ejercicios.map { it.id })
        assertEquals(95, guardada.ejercicios.first().anguloObjetivo)
        assertEquals("Extensión de rodilla sentado", guardada.ejercicios[1].nombre) // nombre e indicaciones salen del catálogo

        val paciente = c.token("paciente", "Paciente2026")
        val vista = c.get("/api/v1/pacientes/1/rutina") { bearerAuth(paciente) }.body<Rutina>()
        assertEquals(3, vista.ejercicios.size)
        assertEquals(6, vista.umbralDolor)

        suspend fun poner(s: RutinaSolicitud, token: String = fisio) = c.put("/api/v1/pacientes/1/rutina") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(s)
        }.status
        assertEquals(HttpStatusCode.UnprocessableEntity, poner(nueva.copy(ejercicios = listOf(EjercicioAsignado(99, 3, 10, 30)))))
        assertEquals(HttpStatusCode.UnprocessableEntity, poner(nueva.copy(ejercicios = listOf(EjercicioAsignado(1, 0, 10, 30)))))
        assertEquals(HttpStatusCode.UnprocessableEntity, poner(nueva.copy(umbralDolor = 11)))
        assertEquals(HttpStatusCode.Forbidden, poner(nueva, c.token("fisio2", "Fisio2026")))
    }

    @Test
    fun `un paciente nuevo sin fisioterapeuta queda asignado al primero que le arma la rutina`() = testApplication {
        val c = arrancar()
        val inv = c.enviar("/api/v1/invitaciones", InvitacionSolicitud("Pedro Sánchez Ruiz"), c.token("recepcion", "Recepcion2026")).body<Invitacion>()
        val fisio2 = c.token("fisio2", "Fisio2026")
        val r = c.put("/api/v1/pacientes/${inv.pacienteId}/rutina") {
            contentType(ContentType.Application.Json)
            bearerAuth(fisio2)
            setBody(RutinaSolicitud("Volver a cargar las bolsas del mandado", 7, listOf(EjercicioAsignado(10, 3, 12, 30))))
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(102 + 1, r.body<Rutina>().id)
        assertTrue(inv.pacienteId in c.get("/api/v1/pacientes") { bearerAuth(fisio2) }.body<List<PacienteResumen>>().map { it.pacienteId })
    }

    @Test
    fun `el administrador edita usuarios y protege los compromisos del fisioterapeuta`() = testApplication {
        val c = arrancar()
        val admin = c.token("admin", "Admin2026")
        val renombrado = c.parche("/api/v1/usuarios/2", EstadoCuentaSolicitud(nombre = "Ft. Carlos Ortega López"), admin).body<UsuarioAdministrado>()
        assertEquals("Ft. Carlos Ortega López", renombrado.nombre)
        val fisio = c.token("fisio", "Fisio2026")
        assertTrue(c.get("/api/v1/citas") { bearerAuth(fisio) }.body<List<Cita>>().all { it.fisioterapeuta == "Ft. Carlos Ortega López" })
        assertEquals("Ft. Carlos Ortega López", c.get("/api/v1/pacientes/1/rutina") { bearerAuth(fisio) }.body<Rutina>().fisioterapeuta)

        assertEquals(HttpStatusCode.Conflict, c.parche("/api/v1/usuarios/2", EstadoCuentaSolicitud(rol = Rol.RECEPCIONISTA), admin).status)
        assertEquals(HttpStatusCode.Conflict, c.parche("/api/v1/usuarios/2", EstadoCuentaSolicitud(activo = false), admin).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, c.parche("/api/v1/usuarios/5", EstadoCuentaSolicitud(rol = Rol.RECEPCIONISTA), admin).status)
        val recep = c.parche("/api/v1/usuarios/4", EstadoCuentaSolicitud(correo = "ana.torres@correo.com"), admin).body<UsuarioAdministrado>()
        assertEquals("ana.torres@correo.com", recep.correo)
    }
}
