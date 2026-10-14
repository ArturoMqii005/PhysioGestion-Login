package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.local.BaseDatosLocal
import com.terapid.physiogestion.datos.remoto.EjercicioApi
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

    private val cliente = clienteSimulado { peticion ->
        if (!hayConexion) throw IOException("Sin red")
        assertTrue(peticion.url.encodedPath.endsWith("/api/v1/resultados-ejercicio"))
        cuerpos += peticion.body.toByteArray().decodeToString()
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
}
