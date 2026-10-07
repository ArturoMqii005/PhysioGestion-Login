package com.terapid.physiogestion

import com.terapid.physiogestion.modelos.Cita
import com.terapid.physiogestion.modelos.LoginRespuesta
import com.terapid.physiogestion.modelos.LoginSolicitud
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.Rutina
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {

    /** Cliente de prueba con el mismo serializador JSON que usa la app. */
    private fun ApplicationTestBuilder.clienteJson(): HttpClient = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun HttpClient.iniciarSesion(usuario: String, contrasena: String) =
        post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginSolicitud(usuario, contrasena))
        }

    @Test
    fun `la raiz del servidor responde`() = testApplication {
        configure()
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun `cada usuario del JSON inicia sesion con su rol`() = testApplication {
        configure()
        val cliente = clienteJson()
        val esperados = mapOf(
            "admin" to ("Admin2026" to Rol.ADMINISTRADOR),
            "fisio" to ("Fisio2026" to Rol.FISIOTERAPEUTA),
            "recepcion" to ("Recepcion2026" to Rol.RECEPCIONISTA),
            "paciente" to ("Paciente2026" to Rol.PACIENTE),
        )
        for ((usuario, datos) in esperados) {
            val respuesta = cliente.iniciarSesion(usuario, datos.first)
            assertEquals(HttpStatusCode.OK, respuesta.status, "login de $usuario")
            val sesion = respuesta.body<LoginRespuesta>()
            assertEquals(datos.second, sesion.usuario.rol)
            assertTrue(sesion.token.isNotBlank())
        }
    }

    @Test
    fun `contrasena incorrecta responde 401`() = testApplication {
        configure()
        val respuesta = clienteJson().iniciarSesion("admin", "otra")
        assertEquals(HttpStatusCode.Unauthorized, respuesta.status)
    }

    @Test
    fun `sin token no se puede consultar la agenda`() = testApplication {
        configure()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/citas").status)
    }

    @Test
    fun `el fisioterapeuta solo recibe sus citas`() = testApplication {
        configure()
        val cliente = clienteJson()
        val sesion = cliente.iniciarSesion("fisio", "Fisio2026").body<LoginRespuesta>()
        val citas = cliente.get("/api/v1/citas") { bearerAuth(sesion.token) }.body<List<Cita>>()
        assertTrue(citas.isNotEmpty())
        assertTrue(citas.all { it.fisioterapeutaId == sesion.usuario.id })
    }

    @Test
    fun `el paciente no puede ver la lista de usuarios`() = testApplication {
        configure()
        val cliente = clienteJson()
        val sesion = cliente.iniciarSesion("paciente", "Paciente2026").body<LoginRespuesta>()
        val respuesta = cliente.get("/api/v1/usuarios") { bearerAuth(sesion.token) }
        assertEquals(HttpStatusCode.Forbidden, respuesta.status)
    }

    @Test
    fun `el paciente consulta su rutina`() = testApplication {
        configure()
        val cliente = clienteJson()
        val sesion = cliente.iniciarSesion("paciente", "Paciente2026").body<LoginRespuesta>()
        val id = sesion.usuario.pacienteId
        val rutina = cliente.get("/api/v1/pacientes/$id/rutina") { bearerAuth(sesion.token) }.body<Rutina>()
        assertEquals(id, rutina.pacienteId)
        assertEquals(4, rutina.ejercicios.size)
    }
}
