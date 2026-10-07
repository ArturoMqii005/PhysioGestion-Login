package com.terapid.physiogestion.datos.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ALMACENAMIENTO LOCAL: base de datos SQLite dentro del teléfono (RI-06).
 *
 * Usa el mismo SQLite en Android, iOS y escritorio (BundledSQLiteDriver),
 * así que las tablas y consultas se escriben una sola vez en código común.
 * La conexión se abre la primera vez que se usa y todas las operaciones
 * pasan por un candado (Mutex) en un hilo de entrada/salida.
 */
class BaseDatosLocal(
    private val ruta: String,
    private val driver: SQLiteDriver = BundledSQLiteDriver(),
) {
    private val candado = Mutex()
    private var conexion: SQLiteConnection? = null

    suspend fun <T> usar(bloque: (SQLiteConnection) -> T): T =
        withContext(Dispatchers.IO) {
            candado.withLock {
                val actual = conexion ?: driver.open(ruta).also {
                    crearTablas(it)
                    conexion = it
                }
                bloque(actual)
            }
        }

    suspend fun cerrar() = candado.withLock {
        conexion?.close()
        conexion = null
    }

    private fun crearTablas(c: SQLiteConnection) {
        // Copia de la última rutina descargada, para usarla sin conexión (RFN-21).
        c.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rutina_descargada (
                paciente_id    INTEGER PRIMARY KEY,
                contenido_json TEXT    NOT NULL,
                descargada_en  INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // Sesiones realizadas; pendiente_envio = 1 forma la cola de envío (RFX-03).
        c.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sesion (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                rutina_id       INTEGER NOT NULL,
                inicio          INTEGER NOT NULL,
                fin             INTEGER NOT NULL,
                clave_dia       TEXT    NOT NULL,
                ejercicios      TEXT    NOT NULL,
                asignados       INTEGER NOT NULL,
                cumplimiento    INTEGER NOT NULL,
                dolor_antes     INTEGER NOT NULL CHECK (dolor_antes BETWEEN 0 AND 10),
                dolor_despues   INTEGER NOT NULL CHECK (dolor_despues BETWEEN 0 AND 10),
                dificultad      TEXT    NOT NULL,
                observacion     TEXT    NOT NULL,
                puntos          INTEGER NOT NULL,
                alerta_dolor    INTEGER NOT NULL,
                pendiente_envio INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
    }
}
