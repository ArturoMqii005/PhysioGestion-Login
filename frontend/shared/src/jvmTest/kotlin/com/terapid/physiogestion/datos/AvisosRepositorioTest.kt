package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenPreferencias
import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.local.BaseDatosLocal
import com.terapid.physiogestion.datos.remoto.AvisosApi
import com.terapid.physiogestion.datos.remoto.ClinicaApi
import com.terapid.physiogestion.datos.remoto.SesionApi
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.Aviso
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Sesion
import com.terapid.physiogestion.modelo.TipoAviso
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.plataforma.Notificador
import com.terapid.physiogestion.servicio.RecordatorioCita
import com.terapid.physiogestion.servicio.SeguimientoServicio
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Notificador falso: guarda lo que la app habría mostrado o programado en el teléfono. */
class NotificadorPrueba : Notificador {
    override val disponible = true
    val avisos = mutableListOf<Aviso>()
    val pendientes = mutableListOf<Int>()
    var recordatorios: List<RecordatorioCita> = emptyList()
    override fun permitidas() = true
    override fun mostrarAvisos(avisos: List<Aviso>) { this.avisos += avisos }
    override fun mostrarPendientes(cantidad: Int) { pendientes += cantidad }
    override fun programarRecordatoriosDeCitas(recordatorios: List<RecordatorioCita>) { this.recordatorios = recordatorios }
    override fun programarRecordatorioRutina(minutosDelDia: Int?) = Unit
    override fun iniciarRevision(rol: Rol) = Unit
    override fun detenerTodo() = Unit
}

/** Avisos, notificaciones sin repetir y la revisión en segundo plano, contra un servidor simulado. */
class AvisosRepositorioTest {
    private val archivoBd = File.createTempFile("physiogestion-avisos", ".db")
    private val bd = BaseDatosLocal(archivoBd.absolutePath)
    private val almacenamiento = AlmacenamientoLocal(bd)
    private val preferencias = PreferenciasRepositorio(AlmacenPreferencias("prueba-${UUID.randomUUID()}"), SeguimientoServicio())
    private val notificador = NotificadorPrueba()

    /** Lo que responde el servidor simulado en GET /avisos. */
    private var bandejaJson = bandeja()
    private var responder: MockRequestHandler = { p ->
        when {
            p.url.encodedPath.endsWith("/avisos") -> json(bandejaJson)
            p.url.encodedPath.endsWith("/avisos/leidos") -> json(bandeja(avisoJson(9, leido = true)))
            p.url.encodedPath.endsWith("/citas") -> json(
                """[{"id":7,"hora":"10:00","paciente":"Jorge Ramírez Cruz","fisioterapeutaId":2,"fisioterapeuta":"Ft. Carlos Ortega Luna",
                   "motivo":"Rodilla","estado":"CONFIRMADA","fecha":"2099-01-05","pacienteId":1}]""",
            )
            // POST /sesiones: el servidor no responde (sin internet en el teléfono).
            else -> throw java.io.IOException("Sin conexión")
        }
    }
    private val cuerpos = mutableListOf<String>()
    private val cliente = clienteSimulado { p ->
        cuerpos += p.body.toByteArray().decodeToString()
        responder(p)
    }

    private fun gestorCon(usuario: Usuario) = GestorSesion().apply { abrir(Sesion("t-${usuario.id}", usuario)) }
    private val fisio = Usuario(2, "fisio", "Ft. Carlos Ortega Luna", Rol.FISIOTERAPEUTA)
    private val paciente = Usuario(5, "paciente", "Jorge Ramírez Cruz", Rol.PACIENTE, pacienteId = 1)

    private fun avisoJson(id: Int, leido: Boolean = false, tipo: TipoAviso = TipoAviso.DOLOR_ALTO) =
        """{"id":$id,"usuarioId":2,"tipo":"$tipo","titulo":"Dolor alto: Jorge Ramírez Cruz","mensaje":"Terminó con dolor 8/10",
           "creado":1793804400000${if (leido) ",\"leido\":true" else ""},"pacienteId":1,"sesionId":$id}"""

    private fun bandeja(vararg avisos: String): String {
        val sinLeer = avisos.count { "\"leido\":true" !in it }
        val ultimo = Regex("\"id\":(\\d+)").findAll(avisos.joinToString()).map { it.groupValues[1].toInt() }.maxOrNull() ?: 0
        return """{"avisos":[${avisos.joinToString(",")}],"sinLeer":$sinLeer,"ultimoId":$ultimo}"""
    }

    @AfterTest
    fun limpiar() = runBlocking {
        bd.cerrar()
        archivoBd.delete()
        Unit
    }

    @Test
    fun lasNotificacionesSalenUnaSolaVezYElContadorSeActualiza() = runBlocking {
        val repo = AvisosRepositorio(AvisosApi(cliente), gestorCon(fisio), preferencias)
        bandejaJson = bandeja(avisoJson(4))

        // Primera revisión en este teléfono: el aviso 4 ya existía, se cuenta pero no se notifica.
        assertTrue(repo.revisar().isEmpty())
        assertEquals(1, repo.resumen.value.sinLeer)
        assertEquals(4, preferencias.ultimoAvisoVisto(2))

        // Llega el 9: se notifica una vez; la siguiente revisión ya no lo repite.
        bandejaJson = bandeja(avisoJson(9), avisoJson(4))
        assertEquals(listOf(9), repo.revisar().map { it.id })
        assertTrue(repo.revisar().isEmpty())
        assertEquals(2, repo.resumen.value.sinLeer)
        assertEquals(9, repo.resumen.value.destacado?.id)
    }

    @Test
    fun laTarjetaDestacaElDolorAltoAunqueHayaAvisosMasRecientes() = runBlocking {
        val repo = AvisosRepositorio(AvisosApi(cliente), gestorCon(fisio), preferencias)
        bandejaJson = bandeja(avisoJson(6, tipo = TipoAviso.PACIENTE_ASIGNADO), avisoJson(5), avisoJson(4, tipo = TipoAviso.CITA_CANCELADA))
        repo.bandeja()
        assertEquals(3, repo.resumen.value.sinLeer)
        assertEquals(5, repo.resumen.value.destacado?.id)
    }

    @Test
    fun marcarComoLeidoEnviaLosIdsYBajaElContador() = runBlocking {
        val repo = AvisosRepositorio(AvisosApi(cliente), gestorCon(fisio), preferencias)
        bandejaJson = bandeja(avisoJson(9))
        repo.bandeja()
        assertEquals(1, repo.resumen.value.sinLeer)

        repo.marcarLeidos(listOf(9))
        assertTrue(cuerpos.last().contains("\"ids\":[9]"), cuerpos.last())
        assertEquals(0, repo.resumen.value.sinLeer)
        assertNull(repo.resumen.value.destacado)
    }

    @Test
    fun elFisioterapeutaRecibeLaNotificacionDeDolorAlto() = runBlocking {
        val gestor = gestorCon(fisio)
        val revisor = revisor(gestor)
        bandejaJson = bandeja()
        revisor.revisar() // marca la línea base (sin avisos)
        bandejaJson = bandeja(avisoJson(1))

        val resultado = revisor.revisar()

        assertEquals(1, resultado.notificados)
        assertEquals(TipoAviso.DOLOR_ALTO, notificador.avisos.single().tipo)
        assertTrue(notificador.avisos.single().urgente)
        // Al fisioterapeuta no se le programan recordatorios de citas propias del paciente.
        assertTrue(notificador.recordatorios.isEmpty())
    }

    @Test
    fun alPacienteSeLeAvisaDeSesionesSinEnviarUnaVezAlDiaYSeProgramanSusCitas() = runBlocking {
        val gestor = gestorCon(paciente)
        almacenamiento.insertarSesion(
            RegistroSesion(
                rutinaId = 101, inicioMillis = 1_790_000_000_000, finMillis = 1_790_000_001_500, claveDia = "2026-10-14",
                ejerciciosRealizados = listOf(1), ejerciciosAsignados = 4, cumplimiento = 25, dolorAntes = 2,
                dolorDespues = 3, dificultad = Dificultad.MEDIA, observacion = "", puntos = 10, alertaDolor = false,
            ),
        )
        val revisor = revisor(gestor)

        val primera = revisor.revisar()
        assertEquals(1, primera.pendientesSinEnviar)
        assertEquals(listOf(1), notificador.pendientes)
        assertEquals(listOf("cita-7-1440", "cita-7-60"), notificador.recordatorios.map { it.clave })

        // Una segunda vuelta el mismo día no repite el aviso de pendientes.
        revisor.revisar()
        assertEquals(listOf(1), notificador.pendientes)
    }

    @Test
    fun siElServidorYaNoReconoceElTokenSeCierraLaSesion() = runBlocking {
        val gestor = gestorCon(fisio)
        responder = { json("""{"mensaje":"Sesión vencida"}""", HttpStatusCode.Unauthorized) }
        assertFailsWith<SesionVencida> { revisor(gestor).revisar() }
        assertNull(gestor.sesion.value)
        // Sin sesión, la siguiente revisión no hace nada.
        assertEquals(false, revisor(gestor).revisar().sesionAbierta)
    }

    private fun revisor(gestor: GestorSesion) = RevisorSegundoPlano(
        avisos = AvisosRepositorio(AvisosApi(cliente), gestor, preferencias),
        clinica = ClinicaRepositorio(ClinicaApi(cliente), gestor),
        sesiones = SesionesRepositorio(SesionApi(cliente), almacenamiento, gestor),
        gestor = gestor,
        preferencias = preferencias,
        notificador = { notificador },
        hoy = { "2026-11-04" },
    )

    @Test
    fun laBandejaPideLosAvisosConElToken() = runBlocking {
        var metodo: HttpMethod? = null
        var autorizacion: String? = null
        val c = clienteSimulado { p ->
            metodo = p.method
            autorizacion = p.headers["Authorization"]
            json(bandeja(avisoJson(3)))
        }
        val b = AvisosRepositorio(AvisosApi(c), gestorCon(fisio), preferencias).bandeja()
        assertEquals(HttpMethod.Get, metodo)
        assertEquals("Bearer t-2", autorizacion)
        assertEquals(3, b.ultimoId)
    }
}
