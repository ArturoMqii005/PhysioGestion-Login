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

/** Versión del esquema. Se incrementa cada vez que cambia alguna tabla. */
const val VERSION_ESQUEMA = 2

/**
 * ALMACENAMIENTO LOCAL · BASE DE DATOS SQLite dentro del teléfono (RI-06).
 *
 * Las preferencias guardan valores sueltos; el historial no: son muchos
 * registros que se consultan por fecha, se suman y se filtran. Por eso vive
 * en una base de datos relacional.
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
                    prepararEsquema(it)
                    conexion = it
                }
                bloque(actual)
            }
        }

    suspend fun cerrar() = candado.withLock {
        conexion?.close()
        conexion = null
    }

    /** Crea las tablas que falten y migra una base creada por una versión anterior de la app. */
    private fun prepararEsquema(c: SQLiteConnection) {
        crearTablas(c)
        val version = c.prepare("PRAGMA user_version").use { it.step(); it.getLong(0).toInt() }
        if (version < 2) {
            // Versión 1 (examen del login): la tabla sesion no tenía estas columnas.
            val columnas = c.prepare("PRAGMA table_info(sesion)").use { st ->
                buildList { while (st.step()) add(st.getText(1)) }
            }
            if ("servidor_id" !in columnas) c.execSQL("ALTER TABLE sesion ADD COLUMN servidor_id INTEGER")
            if ("detalle_ejercicios" !in columnas) {
                c.execSQL("ALTER TABLE sesion ADD COLUMN detalle_ejercicios TEXT NOT NULL DEFAULT '[]'")
            }
        }
        c.execSQL("PRAGMA user_version = $VERSION_ESQUEMA")
        // Índices: el historial se ordena por fecha y la cola de envío se busca por pendiente_envio.
        c.execSQL("CREATE INDEX IF NOT EXISTS idx_sesion_fin ON sesion (fin DESC)")
        c.execSQL("CREATE INDEX IF NOT EXISTS idx_sesion_pendientes ON sesion (pendiente_envio)")
        c.execSQL("CREATE INDEX IF NOT EXISTS idx_medicion_dia ON medicion_ejercicio (clave_dia, ejercicio_id)")
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
                id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                rutina_id          INTEGER NOT NULL,
                inicio             INTEGER NOT NULL,
                fin                INTEGER NOT NULL,
                clave_dia          TEXT    NOT NULL,
                ejercicios         TEXT    NOT NULL,
                asignados          INTEGER NOT NULL,
                cumplimiento       INTEGER NOT NULL,
                dolor_antes        INTEGER NOT NULL CHECK (dolor_antes BETWEEN 0 AND 10),
                dolor_despues      INTEGER NOT NULL CHECK (dolor_despues BETWEEN 0 AND 10),
                dificultad         TEXT    NOT NULL,
                observacion        TEXT    NOT NULL,
                puntos             INTEGER NOT NULL,
                alerta_dolor       INTEGER NOT NULL,
                pendiente_envio    INTEGER NOT NULL DEFAULT 1,
                servidor_id        INTEGER,
                detalle_ejercicios TEXT    NOT NULL DEFAULT '[]'
            )
            """.trimIndent()
        )
        // Resumen de cada ejercicio medido con sensores (nunca las lecturas crudas).
        c.execSQL(
            """
            CREATE TABLE IF NOT EXISTS medicion_ejercicio (
                id                    INTEGER PRIMARY KEY AUTOINCREMENT,
                ejercicio_id          INTEGER NOT NULL,
                clave_dia             TEXT    NOT NULL,
                medido_en             INTEGER NOT NULL,
                repeticiones          INTEGER NOT NULL,
                repeticiones_objetivo INTEGER NOT NULL,
                angulo_maximo         INTEGER NOT NULL,
                angulo_objetivo       INTEGER,
                duracion_segundos     INTEGER NOT NULL,
                enviado               INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
