package com.terapid.physiogestion.datos.local

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.MedicionEjercicio
import com.terapid.physiogestion.modelo.RegistroSesion
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Un teléfono que ya tenía la app del examen (esquema versión 1) no debe
 * perder sus sesiones al actualizar: la base se migra a la versión 2.
 */
class BaseDatosMigracionTest {
    private val archivo = File.createTempFile("physiogestion-v1", ".db")

    @AfterTest
    fun borrar() {
        archivo.delete()
    }

    @Test
    fun migraLaVersionAnteriorSinPerderDatos() = runBlocking {
        // Base creada como la creaba la versión 1.0 de la app.
        BundledSQLiteDriver().open(archivo.absolutePath).use { c ->
            c.execSQL(
                """
                CREATE TABLE sesion (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, rutina_id INTEGER NOT NULL, inicio INTEGER NOT NULL,
                    fin INTEGER NOT NULL, clave_dia TEXT NOT NULL, ejercicios TEXT NOT NULL, asignados INTEGER NOT NULL,
                    cumplimiento INTEGER NOT NULL, dolor_antes INTEGER NOT NULL, dolor_despues INTEGER NOT NULL,
                    dificultad TEXT NOT NULL, observacion TEXT NOT NULL, puntos INTEGER NOT NULL,
                    alerta_dolor INTEGER NOT NULL, pendiente_envio INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            c.execSQL(
                "INSERT INTO sesion (rutina_id, inicio, fin, clave_dia, ejercicios, asignados, cumplimiento, dolor_antes, " +
                    "dolor_despues, dificultad, observacion, puntos, alerta_dolor) VALUES (101, 1, 2, '2026-10-07', '1,2', 4, 50, 3, 4, 'MEDIA', 'Del examen', 20, 0)"
            )
        }

        val bd = BaseDatosLocal(archivo.absolutePath)
        val almacen = AlmacenamientoLocal(bd)
        val antigua = almacen.listarSesiones().single()
        assertEquals("Del examen", antigua.observacion)
        assertNull(antigua.servidorId)
        assertEquals(listOf(1, 2), antigua.ejerciciosRealizados)

        // Las tablas y columnas nuevas ya funcionan.
        almacen.insertarSesion(
            RegistroSesion(
                rutinaId = 101, inicioMillis = 3, finMillis = 4, claveDia = "2026-10-14", ejerciciosRealizados = listOf(3),
                ejerciciosAsignados = 4, cumplimiento = 25, dolorAntes = 1, dolorDespues = 1, dificultad = Dificultad.BAJA,
                observacion = "", puntos = 10, alertaDolor = false, servidorId = 99,
            )
        )
        almacen.insertarMedicion(
            MedicionEjercicio(idEjercicio = 1, claveDia = "2026-10-14", medidoEn = 5, repeticiones = 30, repeticionesObjetivo = 30,
                anguloMaximo = 91, anguloObjetivo = 90, duracionSegundos = 120)
        )
        assertEquals(2, almacen.contarSesiones())
        assertEquals(99, almacen.listarSesiones().first().servidorId)
        assertEquals(1, almacen.medicionesDelDia("2026-10-14").size)
        val version = bd.usar { c -> c.prepare("PRAGMA user_version").use { it.step(); it.getLong(0).toInt() } }
        assertEquals(VERSION_ESQUEMA, version)
        bd.cerrar()
    }
}
