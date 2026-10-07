package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.AutenticacionApi
import com.terapid.physiogestion.datos.remoto.ClinicaApi
import com.terapid.physiogestion.datos.remoto.CredencialesInvalidas
import com.terapid.physiogestion.datos.remoto.ServidorNoDisponible
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.Rol
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Login y sesión contra un backend simulado que responde como el backend Ktor real. */
class AutenticacionRepositorioTest {

    private val respuestaLogin = """
        {"token":"abc-123","usuario":{"id":2,"usuario":"fisio","nombre":"Ft. Carlos Ortega Luna","rol":"FISIOTERAPEUTA"}}
    """.trimIndent()

    @Test
    fun loginCorrectoAbreLaSesionConElRol() = runBlocking {
        var cuerpoEnviado = ""
        val cliente = clienteSimulado { peticion ->
            assertTrue(peticion.url.encodedPath.endsWith("/api/v1/auth/login"))
            cuerpoEnviado = peticion.body.toByteArray().decodeToString()
            json(respuestaLogin)
        }
        val gestor = GestorSesion()
        val repo = AutenticacionRepositorio(AutenticacionApi(cliente), gestor)

        val sesion = repo.iniciarSesion("fisio", "Fisio2026")

        // El objeto LoginSolicitud viajó como JSON gracias al serializador.
        assertTrue(cuerpoEnviado.contains("\"usuario\":\"fisio\""))
        assertTrue(cuerpoEnviado.contains("\"contrasena\":\"Fisio2026\""))
        assertEquals(Rol.FISIOTERAPEUTA, sesion.usuario.rol)
        assertEquals("abc-123", gestor.token())
    }

    @Test
    fun contrasenaIncorrectaNoAbreSesion() = runBlocking {
        val cliente = clienteSimulado { json("""{"mensaje":"Usuario o contraseña incorrectos."}""", HttpStatusCode.Unauthorized) }
        val gestor = GestorSesion()
        val repo = AutenticacionRepositorio(AutenticacionApi(cliente), gestor)

        assertFailsWith<CredencialesInvalidas> { repo.iniciarSesion("admin", "mal") }
        assertNull(gestor.sesion.value)
    }

    @Test
    fun servidorApagadoDaMensajeClaro() = runBlocking {
        val cliente = clienteSimulado { throw IOException("Connection refused") }
        val repo = AutenticacionRepositorio(AutenticacionApi(cliente), GestorSesion())

        val error = assertFailsWith<ServidorNoDisponible> { repo.iniciarSesion("admin", "Admin2026") }
        assertTrue(error.message!!.contains("servidor"))
    }

    @Test
    fun tokenRechazadoCierraLaSesion() = runBlocking {
        val cliente = clienteSimulado { peticion ->
            if (peticion.url.encodedPath.endsWith("/auth/login")) json(respuestaLogin)
            else json("""{"mensaje":"Sesión no válida"}""", HttpStatusCode.Unauthorized)
        }
        val gestor = GestorSesion()
        AutenticacionRepositorio(AutenticacionApi(cliente), gestor).iniciarSesion("fisio", "Fisio2026")
        val clinica = ClinicaRepositorio(ClinicaApi(cliente), gestor)

        assertFailsWith<SesionVencida> { clinica.citas() }
        assertNull(gestor.sesion.value) // la navegación regresará al login
    }
}
