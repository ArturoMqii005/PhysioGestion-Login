package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.local.BaseDatosLocal
import com.terapid.physiogestion.datos.remoto.EjercicioApi
import com.terapid.physiogestion.modelo.MetodoMedicion
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Sesion
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.servicio.movimiento.ResultadoEjercicio
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** El resultado de los sensores se guarda primero en el teléfono y después se envía (solo el resumen). */
class SensorRepositorioTest {
    private val archivoBd = File.createTempFile("physiogestion-sensores", ".db")
    private val bd = BaseDatosLocal(archivoBd.absolutePath)
    private val almacenamiento = AlmacenamientoLocal(bd)
    private val gestor = GestorSesion().apply {
        abrir(Sesion("token-prueba", Usuario(5, "paciente", "Jorge", Rol.PACIENTE, pacienteId = 1)))
    }
    private var hayConexion = true
    private val cuerpos = mutableListOf<String>()

    /** Ejercicios cuyas mediciones rechaza el servidor con 422. */
    private val rechazados = mutableSetOf<Int>()

    private val cliente = clienteSimulado { peticion ->
        if (!hayConexion) throw IOException("Sin red")
        assertTrue(peticion.url.encodedPath.endsWith("/api/v1/resultados-ejercicio"))
        val texto = peticion.body.toByteArray().decodeToString()
        val ejercicio = jsonPrueba.parseToJsonElement(texto).jsonObject["idEjercicio"].toString().toInt()
        if (ejercicio in rechazados) {
            return@clienteSimulado json("""{"mensaje":"El ejercicio no admite verificación con cámara."}""", HttpStatusCode.UnprocessableEntity)
        }
        cuerpos += texto
        json("""{"id":1,"pacienteId":1,"idEjercicio":1,"repeticiones":30,"repeticionesObjetivo":30,
                 "anguloMaximo":91,"anguloObjetivo":90,"duracionSegundos":140,"cumpleObjetivo":true}""", HttpStatusCode.Created)
    }

    private val repositorio = SensorRepositorio(
        EjercicioApi(cliente), almacenamiento, gestor, reloj = { 1_792_000_000_000 },
    )

    private val resultado = ResultadoEjercicio(
        idEjercicio = 1, repeticionesDetectadas = 30, repeticionesObjetivo = 30,
        anguloMaximo = 91.4f, anguloObjetivo = 90, duracionSegundos = 140,
    )

    @AfterTest
    fun limpiar() = runBlocking {
        bd.cerrar()
        archivoBd.delete()
        Unit
    }

    @Test
    fun soloViajaElResumenDeSeisCampos() = runBlocking {
        val envio = repositorio.registrar(resultado)
        assertTrue(envio.enviada)
        val campos = jsonPrueba.parseToJsonElement(cuerpos.single()).jsonObject.keys
        assertEquals(
            setOf("idEjercicio", "repeticiones", "repeticionesObjetivo", "anguloMaximo", "anguloObjetivo", "duracionSegundos"),
            campos,
        )
        assertEquals(91, repositorio.medicionesDeHoy()[1]?.anguloMaximo)
    }

    @Test
    fun sinConexionSeGuardaYSeEnviaDespues() = runBlocking {
        hayConexion = false
        val envio = repositorio.registrar(resultado)
        assertFalse(envio.enviada)
        assertEquals(1, almacenamiento.medicionesPendientes().size) // no se perdió

        hayConexion = true
        assertEquals(1, repositorio.enviarPendientes())
        assertTrue(almacenamiento.medicionesPendientes().isEmpty())
    }

    @Test
    fun unaMedicionRechazadaPorElServidorNoTapaLaCola() = runBlocking {
        hayConexion = false
        repositorio.registrar(resultado.copy(idEjercicio = 3, metodo = MetodoMedicion.CAMARA)) // 422 en el servidor
        repositorio.registrar(resultado)
        hayConexion = true
        rechazados += 3
        assertEquals(1, repositorio.enviarPendientes())
        assertTrue(almacenamiento.medicionesPendientes().isEmpty())
        assertEquals(2, almacenamiento.contarMediciones())
    }

    @Test
    fun loVerificadoConCamaraConservaSuMetodoAunqueSeEnvieDespues() = runBlocking {
        hayConexion = false
        repositorio.registrar(resultado.copy(metodo = MetodoMedicion.CAMARA))
        assertEquals(MetodoMedicion.CAMARA, repositorio.medicionesDeHoy()[1]?.metodo) // así se guardó en SQLite

        hayConexion = true
        assertEquals(1, repositorio.enviarPendientes())
        val cuerpo = jsonPrueba.parseToJsonElement(cuerpos.single()).jsonObject
        assertEquals("\"CAMARA\"", cuerpo["metodo"].toString())
    }
}
