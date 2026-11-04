package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.AutenticacionApi
import com.terapid.physiogestion.datos.remoto.CorreoSinVerificar
import com.terapid.physiogestion.datos.remoto.CuentaApi
import com.terapid.physiogestion.datos.remoto.CuentaDesactivada
import com.terapid.physiogestion.datos.remoto.DatosInvalidos
import com.terapid.physiogestion.datos.remoto.DemasiadasSolicitudes
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Sesion
import com.terapid.physiogestion.modelo.Usuario
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Registro, verificación, recuperación, aviso de privacidad y contraseña contra un backend simulado. */
class CuentaRepositorioTest {

    @Test
    fun elRegistroEnviaLosDatosNormalizados() = runBlocking {
        var ruta = ""
        var cuerpo = ""
        val cliente = clienteSimulado { p ->
            ruta = p.url.encodedPath
            cuerpo = p.body.toByteArray().decodeToString()
            json("""{"mensaje":"Tu cuenta se creó.","correoEnmascarado":"ma***@correo.com"}""", HttpStatusCode.Created)
        }
        val repo = CuentaRepositorio(CuentaApi(cliente), GestorSesion())
        val r = repo.registrar("DEMO-2026", " Maria.Lopez ", "Maria@Correo.com ", "Rodilla2026", aceptaAviso = true)

        assertTrue(ruta.endsWith("/api/v1/auth/registro"))
        val json = jsonPrueba.parseToJsonElement(cuerpo).jsonObject
        assertEquals("maria.lopez", json["usuario"]!!.jsonPrimitive.content)
        assertEquals("maria@correo.com", json["correo"]!!.jsonPrimitive.content)
        assertTrue(json["aceptaAvisoPrivacidad"]!!.jsonPrimitive.boolean)
        assertEquals("ma***@correo.com", r.correoEnmascarado)
    }

    @Test
    fun loginSinCorreoConfirmadoLlevaALaVerificacion() = runBlocking {
        val cliente = clienteSimulado {
            json(
                """{"mensaje":"Confirma tu correo","codigo":"CORREO_NO_VERIFICADO","correo":"maria@correo.com"}""",
                HttpStatusCode.Forbidden,
            )
        }
        val gestor = GestorSesion()
        val error = assertFailsWith<CorreoSinVerificar> {
            AutenticacionRepositorio(AutenticacionApi(cliente), gestor).iniciarSesion("maria.lopez", "Rodilla2026")
        }
        assertEquals("maria@correo.com", error.correo)
        assertEquals(null, gestor.sesion.value)
    }

    @Test
    fun cuentaDesactivadaYBloqueoMuestranElMensajeDelServidor() = runBlocking {
        var respuesta = """{"mensaje":"Tu cuenta está desactivada.","codigo":"CUENTA_DESACTIVADA"}""" to HttpStatusCode.Forbidden
        val cliente = clienteSimulado { json(respuesta.first, respuesta.second) }
        val repo = AutenticacionRepositorio(AutenticacionApi(cliente), GestorSesion())
        assertFailsWith<CuentaDesactivada> { repo.iniciarSesion("sofia", "x") }

        respuesta = """{"mensaje":"Demasiados intentos fallidos. Espera 5 minutos.","codigo":"DEMASIADOS_INTENTOS"}""" to HttpStatusCode.TooManyRequests
        val bloqueo = assertFailsWith<DemasiadasSolicitudes> { repo.iniciarSesion("sofia", "x") }
        assertEquals("Demasiados intentos fallidos. Espera 5 minutos.", bloqueo.message)
    }

    @Test
    fun elLoginTraeLoQueFaltaYAceptarElAvisoLoQuita() = runBlocking {
        val cliente = clienteSimulado { p ->
            when {
                p.url.encodedPath.endsWith("/auth/login") -> json(
                    """{"token":"t1","usuario":{"id":5,"usuario":"paciente","nombre":"Jorge","rol":"PACIENTE","pacienteId":1},
                        "requiereConsentimiento":true}""",
                )
                p.url.encodedPath.endsWith("/usuarios/yo/consentimiento") -> {
                    assertEquals("Bearer t1", p.headers["Authorization"])
                    json("""{"mensaje":"Gracias."}""")
                }
                else -> error("ruta inesperada ${p.url}")
            }
        }
        val gestor = GestorSesion()
        AutenticacionRepositorio(AutenticacionApi(cliente), gestor).iniciarSesion("paciente", "Paciente2026")
        assertTrue(gestor.sesion.value!!.requiereConsentimiento)
        assertFalse(gestor.sesion.value!!.lista)

        CuentaRepositorio(CuentaApi(cliente), gestor).aceptarAviso("2026-11")
        assertFalse(gestor.sesion.value!!.requiereConsentimiento)
        assertTrue(gestor.sesion.value!!.lista)
    }

    @Test
    fun cambiarLaContrasenaTemporalQuitaLaMarca() = runBlocking {
        var metodo: HttpMethod? = null
        val cliente = clienteSimulado { p ->
            metodo = p.method
            assertTrue(p.url.encodedPath.endsWith("/api/v1/usuarios/yo/contrasena"))
            json("""{"mensaje":"Tu contraseña se cambió."}""")
        }
        val gestor = GestorSesion().apply {
            abrir(Sesion("t2", Usuario(9, "sofia.rios", "Sofía", Rol.FISIOTERAPEUTA), debeCambiarContrasena = true))
        }
        val mensaje = CuentaRepositorio(CuentaApi(cliente), gestor).cambiarContrasena("Temporal123", "Sofia2026nueva")
        assertEquals(HttpMethod.Patch, metodo)
        assertEquals("Tu contraseña se cambió.", mensaje)
        assertFalse(gestor.sesion.value!!.debeCambiarContrasena)
    }

    @Test
    fun codigoIncorrectoMuestraCuantosIntentosQuedan() = runBlocking {
        val cliente = clienteSimulado {
            json("""{"mensaje":"El código no es correcto. Te quedan 4 intentos."}""", HttpStatusCode.UnprocessableEntity)
        }
        val repo = CuentaRepositorio(CuentaApi(cliente), GestorSesion())
        val error = assertFailsWith<DatosInvalidos> { repo.restablecer("maria@correo.com", "000000", "Rodilla2027") }
        assertEquals("El código no es correcto. Te quedan 4 intentos.", error.message)
    }

    @Test
    fun recepcionCreaUnaInvitacionConSuToken() = runBlocking {
        val cliente = clienteSimulado { p ->
            assertEquals("Bearer t3", p.headers["Authorization"])
            assertTrue(p.body.toByteArray().decodeToString().contains("Pedro Sánchez Ruiz"))
            json(
                """{"codigo":"K7QM-4R2X","pacienteId":3,"nombrePaciente":"Pedro Sánchez Ruiz","creadaPor":"Ana",
                    "creadaEn":"2026-11-04T15:00:00Z","venceEn":"2026-11-11T15:00:00Z"}""",
                HttpStatusCode.Created,
            )
        }
        val gestor = GestorSesion().apply { abrir(Sesion("t3", Usuario(4, "recepcion", "Ana", Rol.RECEPCIONISTA))) }
        val inv = CuentaRepositorio(CuentaApi(cliente), gestor).crearInvitacion("  Pedro Sánchez Ruiz ")
        assertEquals("K7QM-4R2X", inv.codigo)
        assertFalse(inv.usada)
    }
}
