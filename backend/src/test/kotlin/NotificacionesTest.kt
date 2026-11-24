package com.terapid.physiogestion

import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.modelos.Anuncio
import com.terapid.physiogestion.modelos.AnuncioSolicitud
import com.terapid.physiogestion.modelos.BandejaAvisos
import com.terapid.physiogestion.modelos.CambiarContrasenaSolicitud
import com.terapid.physiogestion.modelos.CitaSolicitud
import com.terapid.physiogestion.modelos.Destinatarios
import com.terapid.physiogestion.modelos.DispositivoRegistrado
import com.terapid.physiogestion.modelos.DispositivoSolicitud
import com.terapid.physiogestion.modelos.EstadoServidor
import com.terapid.physiogestion.modelos.LoginRespuesta
import com.terapid.physiogestion.modelos.LoginSolicitud
import com.terapid.physiogestion.modelos.ResultadoRevision
import com.terapid.physiogestion.modelos.TipoAviso
import com.terapid.physiogestion.servicios.PushEnRegistro
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Notificaciones push con un FCM simulado ([PushEnRegistro]) que guarda lo que «salió».
 * Las push se mandan en el mismo hilo para revisarlas enseguida.
 */
class NotificacionesTest {
    private val reloj = RelojPrueba(Instant.parse("2026-11-04T15:00:00Z"))
    private val fcm = PushEnRegistro()

    private fun ApplicationTestBuilder.arrancar(clave: String? = "clave-de-prueba"): HttpClient {
        environment { config = MapApplicationConfig() }
        application {
            modulo(
                correo = BuzonPrueba(), reloj = reloj, almacenDocumentos = AlmacenEnMemoria(), limitePorMinuto = 1_000,
                enviadorPush = fcm, claveTareas = clave, ejecutorPush = { it.run() },
            )
        }
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

    private suspend fun HttpClient.avisos(token: String) = get("/api/v1/avisos") { bearerAuth(token) }.body<BandejaAvisos>().avisos

    private fun pushA(token: String) = fcm.enviados.filter { it.destino == token }

    // Los tokens de FCM son largos; estos imitan su forma.
    private val telefonoJorge = "fcm-jorge-pixel:" + "a".repeat(40)
    private val otroTelefono = "fcm-jorge-nuevo:" + "b".repeat(40)

    @Test
    fun `cada aviso sale como push de datos sin el texto`() = testApplication {
        val c = arrancar()
        val paciente = c.token("paciente", "Paciente2026")
        val registro = c.enviar("/api/v1/dispositivos", DispositivoSolicitud(telefonoJorge), paciente).body<DispositivoRegistrado>()
        assertEquals(listOf("dev-pacientes"), registro.temas)
        assertTrue(registro.nuevo)
        assertTrue(telefonoJorge in fcm.temas["dev-pacientes"].orEmpty())

        // Recepción agenda una cita: el aviso CITA_NUEVA llega al teléfono de Jorge.
        val recepcion = c.token("recepcion", "Recepcion2026")
        c.enviar("/api/v1/citas", CitaSolicitud("", 1, 2, "2026-11-06", "11:00", 50, "Rehabilitación de rodilla"), recepcion)
        val aviso = c.avisos(paciente).first { it.tipo == TipoAviso.CITA_NUEVA }
        val push = pushA(telefonoJorge).single()
        assertEquals(mapOf("tipo" to "CITA_NUEVA", "avisoId" to aviso.id.toString()), push.datos)

        // Al cerrar sesión el teléfono ya no recibe nada.
        assertEquals(HttpStatusCode.NoContent, c.delete("/api/v1/dispositivos/$telefonoJorge") { bearerAuth(paciente) }.status)
        assertFalse(telefonoJorge in fcm.temas["dev-pacientes"].orEmpty())
        c.enviar("/api/v1/citas", CitaSolicitud("", 1, 2, "2026-11-09", "10:00", 50, "Control"), recepcion)
        assertEquals(1, pushA(telefonoJorge).size)

        // El chequeo de salud dice si Firebase está configurado.
        assertFalse(c.get("/api/v1/salud").body<EstadoServidor>().pushConfigurado)
    }

    @Test
    fun `un telefono nuevo y el cambio de contrasena avisan en los otros telefonos`() = testApplication {
        val c = arrancar()
        val paciente = c.token("paciente", "Paciente2026")
        c.enviar("/api/v1/dispositivos", DispositivoSolicitud(telefonoJorge), paciente)

        val segundo = c.token("paciente", "Paciente2026")
        assertTrue(c.enviar("/api/v1/dispositivos", DispositivoSolicitud(otroTelefono), segundo).body<DispositivoRegistrado>().nuevo)
        // Llega al teléfono que ya estaba, con el texto (no lleva datos de salud), y no al nuevo.
        val alerta = pushA(telefonoJorge).single()
        assertEquals("SEGURIDAD", alerta.datos["tipo"])
        assertEquals("Entraron a tu cuenta en otro dispositivo", alerta.datos["titulo"])
        assertTrue(pushA(otroTelefono).isEmpty())

        // Volver a entrar en un teléfono que ya se conocía no es «dispositivo nuevo».
        c.delete("/api/v1/dispositivos/$otroTelefono") { bearerAuth(segundo) }
        assertFalse(c.enviar("/api/v1/dispositivos", DispositivoSolicitud(otroTelefono), segundo).body<DispositivoRegistrado>().nuevo)
        assertEquals(1, c.avisos(paciente).count { it.tipo == TipoAviso.SEGURIDAD })

        val cambio = c.patch("/api/v1/usuarios/yo/contrasena") {
            contentType(ContentType.Application.Json)
            bearerAuth(segundo)
            setBody(CambiarContrasenaSolicitud("Paciente2026", "OtraClave2026!"))
        }
        assertEquals(HttpStatusCode.OK, cambio.status)
        assertTrue(pushA(telefonoJorge).any { it.datos["titulo"] == "Tu contraseña cambió" })
        assertTrue(c.avisos(segundo).any { it.tipo == TipoAviso.SEGURIDAD && it.titulo == "Tu contraseña cambió" })
    }

    @Test
    fun `FCM da de baja un token y se deja de usar`() = testApplication {
        val c = arrancar()
        val paciente = c.token("paciente", "Paciente2026")
        c.enviar("/api/v1/dispositivos", DispositivoSolicitud(telefonoJorge), paciente)
        fcm.bajas = setOf(telefonoJorge) // la app se desinstaló
        val recepcion = c.token("recepcion", "Recepcion2026")
        c.enviar("/api/v1/citas", CitaSolicitud("", 1, 2, "2026-11-06", "11:00", 50, "Valoración"), recepcion)
        c.enviar("/api/v1/citas", CitaSolicitud("", 1, 2, "2026-11-06", "12:00", 50, "Valoración"), recepcion)
        // Solo el primer intento sale; después el token ya está desactivado.
        assertEquals(1, pushA(telefonoJorge).size)
        assertFalse(telefonoJorge in fcm.temas["dev-pacientes"].orEmpty())
    }

    @Test
    fun `recepcion publica anuncios por tema con limite diario`() = testApplication {
        val c = arrancar()
        val recepcion = c.token("recepcion", "Recepcion2026")
        val paciente = c.token("paciente", "Paciente2026")
        val fisio = c.token("fisio", "Fisio2026")

        val r = c.enviar("/api/v1/anuncios", AnuncioSolicitud("Cerramos el lunes", "El lunes 16 de noviembre no hay consulta por el día festivo.", Destinatarios.PACIENTES), recepcion)
        assertEquals(HttpStatusCode.Created, r.status)
        val anuncio = r.body<Anuncio>()
        assertEquals(1, anuncio.personas)

        val tema = fcm.enviados.single { it.destino == "/topics/dev-pacientes" }
        assertEquals("Cerramos el lunes", tema.datos["titulo"])
        assertTrue(c.avisos(paciente).any { it.tipo == TipoAviso.ANUNCIO && it.titulo == "Cerramos el lunes" })
        assertTrue(c.avisos(fisio).none { it.tipo == TipoAviso.ANUNCIO })

        // El paciente no publica; los textos tienen límite; máximo 3 en 24 horas.
        assertEquals(HttpStatusCode.Forbidden, c.enviar("/api/v1/anuncios", AnuncioSolicitud("Hola", "Texto de prueba", Destinatarios.TODOS), paciente).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, c.enviar("/api/v1/anuncios", AnuncioSolicitud("Ok", "Texto", Destinatarios.TODOS), recepcion).status)
        repeat(2) { c.enviar("/api/v1/anuncios", AnuncioSolicitud("Junta $it", "Junta del personal a las 8:00.", Destinatarios.PERSONAL), recepcion) }
        val cuarto = c.enviar("/api/v1/anuncios", AnuncioSolicitud("Uno más", "Ya no debería salir.", Destinatarios.TODOS), recepcion)
        assertEquals(HttpStatusCode.TooManyRequests, cuarto.status)
        assertEquals(3, c.get("/api/v1/anuncios") { bearerAuth(recepcion) }.body<List<Anuncio>>().size)
    }

    @Test
    fun `la revision diaria avisa al fisioterapeuta una vez por dia`() = testApplication {
        val c = arrancar()
        assertEquals(HttpStatusCode.Forbidden, c.post("/api/v1/tareas/revision-diaria") { header("X-Tarea-Clave", "otra") }.status)

        // Jorge (paciente de Carlos) no registra sesiones desde el 13/10.
        val r = c.post("/api/v1/tareas/revision-diaria") { header("X-Tarea-Clave", "clave-de-prueba") }.body<ResultadoRevision>()
        assertEquals(ResultadoRevision(fisioterapeutasAvisados = 1, pacientesSinActividad = 1), r)
        val fisio = c.token("fisio", "Fisio2026")
        val aviso = c.avisos(fisio).single { it.tipo == TipoAviso.PACIENTES_INACTIVOS }
        assertEquals("Jorge Ramírez Cruz no ha hecho su rutina", aviso.titulo)
        assertTrue(aviso.mensaje.startsWith("Lleva 22 días"))
        assertEquals(1, aviso.pacienteId)
        assertTrue(c.avisos(c.token("fisio2", "Fisio2026")).none { it.tipo == TipoAviso.PACIENTES_INACTIVOS })

        // Si GitHub Actions la vuelve a llamar el mismo día, no se repite.
        assertEquals(0, c.post("/api/v1/tareas/revision-diaria") { header("X-Tarea-Clave", "clave-de-prueba") }.body<ResultadoRevision>().fisioterapeutasAvisados)
    }

    @Test
    fun `sin TAREA_CLAVE la tarea responde 503`() = testApplication {
        val c = arrancar(clave = null)
        assertEquals(HttpStatusCode.ServiceUnavailable, c.post("/api/v1/tareas/revision-diaria") { header("X-Tarea-Clave", "x") }.status)
    }
}
