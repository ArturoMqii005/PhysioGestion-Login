package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.local.BaseDatosLocal
import com.terapid.physiogestion.datos.remoto.DatosInvalidos
import com.terapid.physiogestion.datos.remoto.DemasiadasSolicitudes
import com.terapid.physiogestion.datos.remoto.ENCABEZADO_IDEMPOTENCIA
import com.terapid.physiogestion.datos.remoto.ErrorDelServidor
import com.terapid.physiogestion.datos.remoto.ServidorNoDisponible
import com.terapid.physiogestion.datos.remoto.SesionApi
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.EjercicioRealizado
import com.terapid.physiogestion.modelo.ModificarSesionSolicitud
import com.terapid.physiogestion.modelo.ReemplazarSesionSolicitud
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Sesion
import com.terapid.physiogestion.modelo.Usuario
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Los cinco métodos HTTP y la cola de envío, contra un backend simulado
 * (MockEngine) que responde como el backend Ktor real, con SQLite real.
 */
class SesionesRepositorioTest {
    private val archivoBd = File.createTempFile("physiogestion-sesiones", ".db")
    private val bd = BaseDatosLocal(archivoBd.absolutePath)
    private val almacenamiento = AlmacenamientoLocal(bd)
    private val gestor = GestorSesion().apply {
        abrir(Sesion("token-prueba", Usuario(5, "paciente", "Jorge", Rol.PACIENTE, pacienteId = 1)))
    }

    /** Peticiones que llegaron al servidor simulado, con su cuerpo como texto. */
    private val peticiones = mutableListOf<Pair<HttpRequestData, String>>()
    private var responder: MockRequestHandler = { json(sesionJson(id = 10)) }

    private val cliente = clienteSimulado { peticion ->
        peticiones += peticion to peticion.body.toByteArray().decodeToString()
        responder(peticion)
    }
    private val repositorio = SesionesRepositorio(SesionApi(cliente), almacenamiento, gestor)

    @AfterTest
    fun limpiar() = runBlocking {
        bd.cerrar()
        archivoBd.delete()
        Unit
    }

    private fun sesionJson(id: Int, observacion: String? = "Bien") = """
        {"id":$id,"pacienteId":1,"idRutina":101,"fechaHoraInicio":"2026-10-14T10:00:00","fechaHoraFin":"2026-10-14T10:25:00",
         "dolorAntes":2,"dolorDespues":3,"dificultad":"MEDIA",${observacion?.let { "\"observacion\":\"$it\"," } ?: ""}
         "ejercicios":[{"idEjercicio":1,"repeticionesRealizadas":30,"anguloMaximo":88,"verificado":true}],
         "porcentajeCumplimiento":75,"semaforo":"VERDE"}
    """.trimIndent()

    private suspend fun guardarLocal(): Long = almacenamiento.insertarSesion(
        RegistroSesion(
            rutinaId = 101, inicioMillis = 1_790_000_000_000, finMillis = 1_790_000_001_500, claveDia = "2026-10-14",
            ejerciciosRealizados = listOf(1), ejerciciosAsignados = 4, cumplimiento = 25, dolorAntes = 2,
            dolorDespues = 3, dificultad = Dificultad.MEDIA, observacion = "Bien", puntos = 10, alertaDolor = false,
            detalle = listOf(EjercicioRealizado(1, 30, 88, verificado = true)),
        )
    )

    @Test
    fun postEnviaLaSesionConClaveDeIdempotenciaYSaleDeLaCola() = runBlocking {
        val idLocal = guardarLocal()
        responder = { respond(sesionJson(id = 10), HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json")) }

        val resultado = repositorio.enviarPendientes()

        assertEquals(1, resultado.enviadas)
        assertEquals(0, resultado.pendientes)
        val (peticion, cuerpo) = peticiones.single()
        assertEquals(HttpMethod.Post, peticion.method)
        assertTrue(peticion.url.encodedPath.endsWith("/api/v1/sesiones"))
        assertEquals("sesion-local-$idLocal-1790000000000", peticion.headers[ENCABEZADO_IDEMPOTENCIA])
        assertEquals("Bearer token-prueba", peticion.headers[HttpHeaders.Authorization])
        assertTrue(cuerpo.contains("\"idRutina\":101") && cuerpo.contains("\"verificado\":true"))
        assertTrue(resultado.detalle!!.startsWith("POST /api/v1/sesiones → 201"))
        assertEquals(10, almacenamiento.listarSesiones().single().servidorId)
    }

    @Test
    fun sinConexionLaSesionSeQuedaEnLaColaYSeReintentaConLaMismaClave() = runBlocking {
        guardarLocal()
        responder = { throw IOException("Connection refused") }
        val fallido = repositorio.enviarPendientes()
        assertIs<ServidorNoDisponible>(fallido.error)
        assertEquals(1, fallido.pendientes)

        responder = { respond(sesionJson(id = 11), HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json")) }
        val exitoso = repositorio.enviarPendientes()
        assertEquals(0, exitoso.pendientes)
        val claves = peticiones.map { it.first.headers[ENCABEZADO_IDEMPOTENCIA] }
        assertEquals(2, claves.size)
        assertEquals(claves[0], claves[1]) // misma clave: el servidor no la duplicaría
    }

    @Test
    fun siElServidorYaLaTiene409NoSeReintenta() = runBlocking {
        guardarLocal()
        responder = { json("""{"mensaje":"Ya hay una sesión registrada con la misma hora de inicio."}""", HttpStatusCode.Conflict) }
        assertEquals(0, repositorio.enviarPendientes().pendientes)
    }

    @Test
    fun getListaConParametrosYLeeElTotalDelEncabezado() = runBlocking {
        responder = {
            respond("[${sesionJson(10)}]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/json"), "X-Total-Count" to listOf("23")))
        }
        val pagina = repositorio.historial(pagina = 1, tamano = 20).datos
        assertEquals(23, pagina.total)
        assertEquals(10, pagina.sesiones.single().id)
        val url = peticiones.single().first.url
        assertEquals("1", url.parameters["idPaciente"])
        assertEquals("1", url.parameters["pagina"])
        assertEquals("20", url.parameters["tamano"])
    }

    @Test
    fun patchSoloMandaLosCamposQueCambianYPutMandaTodo() = runBlocking {
        repositorio.modificar(10, ModificarSesionSolicitud(observacion = "Mejor"))
        repositorio.reemplazar(10, ReemplazarSesionSolicitud(3, 5, Dificultad.ALTA, observacion = null))

        val (patch, cuerpoPatch) = peticiones[0]
        val (put, cuerpoPut) = peticiones[1]
        assertEquals(HttpMethod.Patch, patch.method)
        assertEquals(setOf("observacion"), jsonPrueba.parseToJsonElement(cuerpoPatch).jsonObject.keys)
        assertEquals(HttpMethod.Put, put.method)
        assertEquals(
            setOf("dolorAntes", "dolorDespues", "dificultad", "observacion"),
            jsonPrueba.parseToJsonElement(cuerpoPut).jsonObject.keys,
        )
        assertTrue(cuerpoPut.contains("\"observacion\":null"))
    }

    @Test
    fun deleteResponde204YBorraLaCopiaDelTelefono() = runBlocking {
        val idLocal = guardarLocal()
        almacenamiento.marcarSesionEnviada(idLocal, servidorId = 10)
        responder = { respond("", HttpStatusCode.NoContent) }

        val respuesta = repositorio.eliminar(10)

        assertEquals(HttpMethod.Delete, peticiones.single().first.method)
        assertTrue(respuesta.detalle.endsWith("204 No Content"))
        assertTrue(almacenamiento.listarSesiones().isEmpty())
    }

    @Test
    fun cadaCodigoDeErrorSeTraduceAUnMensajeClaro() = runBlocking {
        responder = { json("""{"mensaje":"El dolor se registra en una escala de 0 a 10."}""", HttpStatusCode.UnprocessableEntity) }
        val invalido = assertFailsWith<DatosInvalidos> { repositorio.modificar(10, ModificarSesionSolicitud(dolorDespues = 11)) }
        assertEquals("El dolor se registra en una escala de 0 a 10.", invalido.message)

        responder = { json("{}", HttpStatusCode.TooManyRequests) }
        assertFailsWith<DemasiadasSolicitudes> { repositorio.obtener(10) }

        responder = { json("{}", HttpStatusCode.InternalServerError) }
        assertFailsWith<ErrorDelServidor> { repositorio.obtener(10) }
        assertFalse(gestor.sesion.value == null) // ninguno de estos errores cierra la sesión
    }
}
