package com.terapid.physiogestion

import com.terapid.physiogestion.modelos.AltaUsuarioRespuesta
import com.terapid.physiogestion.modelos.AltaUsuarioSolicitud
import com.terapid.physiogestion.modelos.CambiarContrasenaSolicitud
import com.terapid.physiogestion.modelos.ConsentimientoSolicitud
import com.terapid.physiogestion.modelos.CorreoSolicitud
import com.terapid.physiogestion.modelos.ErrorRespuesta
import com.terapid.physiogestion.modelos.EstadoCuenta
import com.terapid.physiogestion.modelos.EstadoCuentaSolicitud
import com.terapid.physiogestion.modelos.Invitacion
import com.terapid.physiogestion.modelos.InvitacionSolicitud
import com.terapid.physiogestion.modelos.LoginRespuesta
import com.terapid.physiogestion.modelos.LoginSolicitud
import com.terapid.physiogestion.modelos.MensajeRespuesta
import com.terapid.physiogestion.modelos.RegistroSolicitud
import com.terapid.physiogestion.modelos.RestablecerSolicitud
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.Rutina
import com.terapid.physiogestion.modelos.VerificarCorreoSolicitud
import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.servicios.AutenticacionServicio
import com.terapid.physiogestion.servicios.CorreoSaliente
import com.terapid.physiogestion.servicios.EnviadorCorreo
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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Reloj que la prueba puede adelantar (vencimiento de códigos, bloqueo y sesiones). */
class RelojPrueba(var instante: Instant = Instant.parse("2026-11-04T15:00:00Z")) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant(): Instant = instante
    fun adelantar(d: Duration) { instante = instante.plus(d) }
}

/** Buzón simulado: guarda los correos para leer el código que «llegó». */
class BuzonPrueba : EnviadorCorreo {
    val enviados = mutableListOf<CorreoSaliente>()
    override fun enviar(correo: CorreoSaliente) { enviados += correo }
    fun ultimoCodigo(para: String): String =
        Regex("\\b(\\d{6})\\b").find(enviados.last { it.para == para }.asunto)!!.groupValues[1]
}

/** Registro, verificación, recuperación, restablecimiento, cambio de contraseña, bloqueo y alta de personal. */
class CuentasTest {
    private val buzon = BuzonPrueba()
    private val reloj = RelojPrueba()

    private fun ApplicationTestBuilder.arrancar(limite: Int = 1_000): HttpClient {
        environment { config = MapApplicationConfig() }
        application { modulo(correo = buzon, reloj = reloj, almacenDocumentos = AlmacenEnMemoria(), limitePorMinuto = limite) }
        return createClient { install(ContentNegotiation) { json() } }
    }

    private suspend fun HttpClient.enviar(ruta: String, cuerpo: Any, token: String? = null): HttpResponse = post(ruta) {
        contentType(ContentType.Application.Json)
        token?.let { bearerAuth(it) }
        setBody(cuerpo)
    }

    private suspend fun HttpClient.login(usuario: String, contrasena: String) = enviar("/api/v1/auth/login", LoginSolicitud(usuario, contrasena))

    private suspend fun HttpClient.tokenDe(usuario: String, contrasena: String) = login(usuario, contrasena).body<LoginRespuesta>().token

    private val registroMaria = RegistroSolicitud("demo2026", "maria.lopez", "maria@correo.com", "Rodilla2026", aceptaAvisoPrivacidad = true)

    @Test
    fun `las contrasenas de ejemplo se guardan con hash y siguen funcionando`() = testApplication {
        val cliente = arrancar()
        val texto = javaClass.getResource("/datos/usuarios.json")!!.readText()
        assertFalse(texto.contains("Admin2026"), "usuarios.json no debe tener contraseñas en texto")
        assertTrue(texto.contains("\$2a\$10\$"))
        assertEquals(HttpStatusCode.OK, cliente.login("admin", "Admin2026").status)
        // También se puede entrar con el correo.
        assertEquals(HttpStatusCode.OK, cliente.login("admin@physiogestion.test", "Admin2026").status)
    }

    @Test
    fun `registro completo con invitacion, verificacion de correo y primer inicio de sesion`() = testApplication {
        val cliente = arrancar()
        val registro = cliente.enviar("/api/v1/auth/registro", registroMaria)
        assertEquals(HttpStatusCode.Created, registro.status)

        // Sin confirmar el correo no puede entrar, y la respuesta dice a qué correo se envió el código.
        val antes = cliente.login("maria.lopez", "Rodilla2026")
        assertEquals(HttpStatusCode.Forbidden, antes.status)
        val error = antes.body<ErrorRespuesta>()
        assertEquals("CORREO_NO_VERIFICADO", error.codigo)
        assertEquals("maria@correo.com", error.correo)

        val codigo = buzon.ultimoCodigo("maria@correo.com")
        val verificado = cliente.enviar("/api/v1/auth/verificar-correo", VerificarCorreoSolicitud("maria@correo.com", codigo))
        assertEquals(HttpStatusCode.OK, verificado.status)

        val sesion = cliente.login("maria.lopez", "Rodilla2026").body<LoginRespuesta>()
        assertEquals(Rol.PACIENTE, sesion.usuario.rol)
        assertEquals(2, sesion.usuario.pacienteId)
        assertEquals("María López Hernández", sesion.usuario.nombre)
        assertFalse(sesion.requiereConsentimiento, "aceptó el aviso al registrarse")
        // La paciente nueva ya ve la rutina que le asignó su fisioterapeuta.
        val rutina = cliente.get("/api/v1/pacientes/2/rutina") { bearerAuth(sesion.token) }.body<Rutina>()
        assertEquals("Ft. Mariana Soto Gil", rutina.fisioterapeuta)
    }

    @Test
    fun `la invitacion solo sirve una vez y el registro valida los datos`() = testApplication {
        val cliente = arrancar()
        assertEquals(HttpStatusCode.Created, cliente.enviar("/api/v1/auth/registro", registroMaria).status)
        val repetido = cliente.enviar("/api/v1/auth/registro", registroMaria.copy(usuario = "otra.persona", correo = "otra@correo.com"))
        assertEquals(HttpStatusCode.UnprocessableEntity, repetido.status)
        assertEquals("INVITACION_INVALIDA", repetido.body<ErrorRespuesta>().codigo)

        val casos = listOf(
            registroMaria.copy(contrasena = "corta1"),
            registroMaria.copy(contrasena = "SoloLetrasAqui"),
            registroMaria.copy(usuario = "Mal Usuario!"),
            registroMaria.copy(correo = "sin-arroba"),
            registroMaria.copy(aceptaAvisoPrivacidad = false),
        )
        for (caso in casos) {
            assertEquals(HttpStatusCode.UnprocessableEntity, cliente.enviar("/api/v1/auth/registro", caso).status, caso.toString())
        }
    }

    @Test
    fun `el codigo de verificacion vence a los 15 minutos y admite 5 intentos`() = testApplication {
        val cliente = arrancar()
        cliente.enviar("/api/v1/auth/registro", registroMaria)
        val codigo = buzon.ultimoCodigo("maria@correo.com")
        repeat(4) {
            val r = cliente.enviar("/api/v1/auth/verificar-correo", VerificarCorreoSolicitud("maria@correo.com", "000000"))
            assertEquals(HttpStatusCode.UnprocessableEntity, r.status)
        }
        val quinto = cliente.enviar("/api/v1/auth/verificar-correo", VerificarCorreoSolicitud("maria@correo.com", "000000"))
        assertEquals(HttpStatusCode.TooManyRequests, quinto.status)
        // El código correcto ya no sirve: se invalidó.
        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            cliente.enviar("/api/v1/auth/verificar-correo", VerificarCorreoSolicitud("maria@correo.com", codigo)).status,
        )

        cliente.enviar("/api/v1/auth/reenviar-verificacion", CorreoSolicitud("maria@correo.com"))
        val nuevo = buzon.ultimoCodigo("maria@correo.com")
        reloj.adelantar(Duration.ofMinutes(16))
        val vencido = cliente.enviar("/api/v1/auth/verificar-correo", VerificarCorreoSolicitud("maria@correo.com", nuevo))
        assertEquals("CODIGO_VENCIDO", vencido.body<ErrorRespuesta>().codigo)
    }

    @Test
    fun `recuperacion y restablecimiento cierran las sesiones abiertas`() = testApplication {
        val cliente = arrancar()
        val tokenViejo = cliente.tokenDe("fisio", "Fisio2026")

        // La respuesta es idéntica exista o no el correo.
        val existe = cliente.enviar("/api/v1/auth/recuperacion", CorreoSolicitud("carlos.ortega@physiogestion.test"))
        val noExiste = cliente.enviar("/api/v1/auth/recuperacion", CorreoSolicitud("nadie@correo.com"))
        assertEquals(HttpStatusCode.Accepted, existe.status)
        assertEquals(existe.status, noExiste.status)
        assertEquals(existe.body<MensajeRespuesta>().mensaje, noExiste.body<MensajeRespuesta>().mensaje)
        assertTrue(buzon.enviados.none { it.para == "nadie@correo.com" })

        val codigo = buzon.ultimoCodigo("carlos.ortega@physiogestion.test")
        val debil = cliente.enviar("/api/v1/auth/restablecer", RestablecerSolicitud("carlos.ortega@physiogestion.test", codigo, "corta"))
        assertEquals(HttpStatusCode.UnprocessableEntity, debil.status)
        val bien = cliente.enviar("/api/v1/auth/restablecer", RestablecerSolicitud("carlos.ortega@physiogestion.test", codigo, "Rehabilita2026"))
        assertEquals(HttpStatusCode.OK, bien.status)

        // El código es de un solo uso, el token anterior ya no sirve y la contraseña vieja tampoco.
        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            cliente.enviar("/api/v1/auth/restablecer", RestablecerSolicitud("carlos.ortega@physiogestion.test", codigo, "OtraClave2026")).status,
        )
        assertEquals(HttpStatusCode.Unauthorized, cliente.get("/api/v1/citas") { bearerAuth(tokenViejo) }.status)
        assertEquals(HttpStatusCode.Unauthorized, cliente.login("fisio", "Fisio2026").status)
        assertEquals(HttpStatusCode.OK, cliente.login("fisio", "Rehabilita2026").status)
        assertTrue(buzon.enviados.any { it.asunto.contains("cambió") })
    }

    @Test
    fun `cinco intentos fallidos bloquean el acceso cinco minutos`() = testApplication {
        val cliente = arrancar()
        repeat(4) { assertEquals(HttpStatusCode.Unauthorized, cliente.login("recepcion", "mala$it").status) }
        val quinto = cliente.login("recepcion", "mala5")
        assertEquals(HttpStatusCode.TooManyRequests, quinto.status)
        assertNotNull(quinto.headers[HttpHeaders.RetryAfter])
        // Ni con la contraseña correcta entra mientras dura el bloqueo.
        assertEquals(HttpStatusCode.TooManyRequests, cliente.login("recepcion", "Recepcion2026").status)
        reloj.adelantar(Duration.ofMinutes(6))
        assertEquals(HttpStatusCode.OK, cliente.login("recepcion", "Recepcion2026").status)
    }

    @Test
    fun `el token vence tras 30 dias sin uso y cada uso lo renueva`() = testApplication {
        val cliente = arrancar()
        val sesion = cliente.login("admin", "Admin2026").body<LoginRespuesta>()
        assertNotNull(sesion.expiraEn)
        reloj.adelantar(Duration.ofDays(20))
        assertEquals(HttpStatusCode.OK, cliente.get("/api/v1/usuarios") { bearerAuth(sesion.token) }.status)
        reloj.adelantar(Duration.ofDays(20)) // 40 días desde el login, pero solo 20 desde el último uso
        assertEquals(HttpStatusCode.OK, cliente.get("/api/v1/usuarios") { bearerAuth(sesion.token) }.status)
        reloj.adelantar(Duration.ofDays(31))
        assertEquals(HttpStatusCode.Unauthorized, cliente.get("/api/v1/usuarios") { bearerAuth(sesion.token) }.status)
    }

    @Test
    fun `cambio de contrasena desde el perfil conserva solo la sesion actual`() = testApplication {
        val cliente = arrancar()
        val enCelular = cliente.tokenDe("fisio2", "Fisio2026")
        val enComputadora = cliente.tokenDe("fisio2", "Fisio2026")
        suspend fun cambiar(actual: String, nueva: String) = cliente.patch("/api/v1/usuarios/yo/contrasena") {
            contentType(ContentType.Application.Json)
            bearerAuth(enCelular)
            setBody(CambiarContrasenaSolicitud(actual, nueva))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, cambiar("incorrecta", "Mariana2026").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, cambiar("Fisio2026", "Fisio2026").status)
        assertEquals(HttpStatusCode.OK, cambiar("Fisio2026", "Mariana2026").status)
        assertEquals(HttpStatusCode.OK, cliente.get("/api/v1/usuarios/yo") { bearerAuth(enCelular) }.status)
        assertEquals(HttpStatusCode.Unauthorized, cliente.get("/api/v1/usuarios/yo") { bearerAuth(enComputadora) }.status)
        assertEquals(HttpStatusCode.OK, cliente.login("fisio2", "Mariana2026").status)
    }

    @Test
    fun `el paciente de ejemplo debe aceptar el aviso de privacidad`() = testApplication {
        val cliente = arrancar()
        val sesion = cliente.login("paciente", "Paciente2026").body<LoginRespuesta>()
        assertTrue(sesion.requiereConsentimiento)
        val aviso = cliente.get("/api/v1/aviso-privacidad").body<com.terapid.physiogestion.modelos.AvisoPrivacidad>()
        assertEquals(AutenticacionServicio.VERSION_AVISO, aviso.version)
        assertTrue(aviso.secciones.size >= 5)
        val r = cliente.enviar("/api/v1/usuarios/yo/consentimiento", ConsentimientoSolicitud(aviso.version), sesion.token)
        assertEquals(HttpStatusCode.OK, r.status)
        val cuenta = cliente.get("/api/v1/usuarios/yo") { bearerAuth(sesion.token) }.body<EstadoCuenta>()
        assertFalse(cuenta.requiereConsentimiento)
        assertFalse(cliente.login("paciente", "Paciente2026").body<LoginRespuesta>().requiereConsentimiento)
    }

    @Test
    fun `recepcion genera invitaciones y el paciente no puede`() = testApplication {
        val cliente = arrancar()
        val recepcion = cliente.tokenDe("recepcion", "Recepcion2026")
        val r = cliente.enviar("/api/v1/invitaciones", InvitacionSolicitud("Pedro Sánchez Ruiz"), recepcion)
        assertEquals(HttpStatusCode.Created, r.status)
        val invitacion = r.body<Invitacion>()
        assertTrue(Regex("^[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$").matches(invitacion.codigo), invitacion.codigo)
        assertEquals(3, invitacion.pacienteId) // 1 y 2 ya existen
        assertEquals("Ana Torres Vega", invitacion.creadaPor)

        val paciente = cliente.tokenDe("paciente", "Paciente2026")
        assertEquals(HttpStatusCode.Forbidden, cliente.enviar("/api/v1/invitaciones", InvitacionSolicitud("Alguien Más"), paciente).status)

        // Con la invitación nueva, Pedro se registra (la escribe en minúsculas y sin guion).
        val registro = RegistroSolicitud(invitacion.codigo.lowercase().replace("-", ""), "pedro.s", "pedro@correo.com", "Hombro2026", true)
        assertEquals(HttpStatusCode.Created, cliente.enviar("/api/v1/auth/registro", registro).status)
    }

    @Test
    fun `el administrador da de alta personal con contrasena temporal y puede desactivarlo`() = testApplication {
        val cliente = arrancar()
        val admin = cliente.tokenDe("admin", "Admin2026")
        val alta = cliente.enviar(
            "/api/v1/usuarios",
            AltaUsuarioSolicitud("sofia.rios", "Ft. Sofía Ríos Pérez", Rol.FISIOTERAPEUTA, "sofia@correo.com"),
            admin,
        )
        assertEquals(HttpStatusCode.Created, alta.status)
        val creada = alta.body<AltaUsuarioRespuesta>()
        assertEquals(12, creada.contrasenaTemporal.length)

        val primera = cliente.login("sofia.rios", creada.contrasenaTemporal).body<LoginRespuesta>()
        assertTrue(primera.debeCambiarContrasena)

        // Un paciente no se da de alta así.
        val paciente = cliente.enviar("/api/v1/usuarios", AltaUsuarioSolicitud("x.paciente", "Paciente Nuevo", Rol.PACIENTE, "p@correo.com"), admin)
        assertEquals(HttpStatusCode.UnprocessableEntity, paciente.status)

        val desactivar = cliente.patch("/api/v1/usuarios/${creada.usuario.id}") {
            contentType(ContentType.Application.Json)
            bearerAuth(admin)
            setBody(EstadoCuentaSolicitud(activo = false))
        }
        assertEquals(HttpStatusCode.OK, desactivar.status)
        assertEquals(HttpStatusCode.Unauthorized, cliente.get("/api/v1/citas") { bearerAuth(primera.token) }.status)
        val bloqueada = cliente.login("sofia.rios", creada.contrasenaTemporal)
        assertEquals(HttpStatusCode.Forbidden, bloqueada.status)
        assertEquals("CUENTA_DESACTIVADA", bloqueada.body<ErrorRespuesta>().codigo)
    }

    @Test
    fun `el limite de peticiones responde 429`() = testApplication {
        val cliente = arrancar(limite = 3)
        repeat(3) { cliente.enviar("/api/v1/auth/recuperacion", CorreoSolicitud("x@correo.com")) }
        assertEquals(HttpStatusCode.TooManyRequests, cliente.enviar("/api/v1/auth/recuperacion", CorreoSolicitud("x@correo.com")).status)
    }
}
