package com.terapid.physiogestion.datos.local

import androidx.sqlite.SQLiteStatement
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.RegistroSesion

/** Rutina guardada en el teléfono y el momento en que se descargó. */
class RutinaGuardada(val json: String, val descargadaEn: Long)

/**
 * Operaciones de lectura y escritura sobre [BaseDatosLocal].
 * Aquí viven todas las sentencias SQL de la app.
 */
class AlmacenamientoLocal(private val bd: BaseDatosLocal) {

    suspend fun guardarRutina(pacienteId: Int, json: String, descargadaEn: Long) = bd.usar { c ->
        c.prepare(
            "INSERT OR REPLACE INTO rutina_descargada (paciente_id, contenido_json, descargada_en) VALUES (?, ?, ?)"
        ).use { st ->
            st.bindLong(1, pacienteId.toLong())
            st.bindText(2, json)
            st.bindLong(3, descargadaEn)
            st.step()
        }
        Unit
    }

    suspend fun leerRutina(pacienteId: Int): RutinaGuardada? = bd.usar { c ->
        c.prepare(
            "SELECT contenido_json, descargada_en FROM rutina_descargada WHERE paciente_id = ?"
        ).use { st ->
            st.bindLong(1, pacienteId.toLong())
            if (st.step()) RutinaGuardada(st.getText(0), st.getLong(1)) else null
        }
    }

    suspend fun insertarSesion(s: RegistroSesion): Long = bd.usar { c ->
        c.prepare(
            """
            INSERT INTO sesion (rutina_id, inicio, fin, clave_dia, ejercicios, asignados, cumplimiento,
                dolor_antes, dolor_despues, dificultad, observacion, puntos, alerta_dolor, pendiente_envio)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { st ->
            st.bindLong(1, s.rutinaId.toLong())
            st.bindLong(2, s.inicioMillis)
            st.bindLong(3, s.finMillis)
            st.bindText(4, s.claveDia)
            st.bindText(5, s.ejerciciosRealizados.joinToString(","))
            st.bindLong(6, s.ejerciciosAsignados.toLong())
            st.bindLong(7, s.cumplimiento.toLong())
            st.bindLong(8, s.dolorAntes.toLong())
            st.bindLong(9, s.dolorDespues.toLong())
            st.bindText(10, s.dificultad.name)
            st.bindText(11, s.observacion)
            st.bindLong(12, s.puntos.toLong())
            st.bindLong(13, if (s.alertaDolor) 1L else 0L)
            st.bindLong(14, if (s.pendienteEnvio) 1L else 0L)
            st.step()
        }
        c.prepare("SELECT last_insert_rowid()").use { st ->
            st.step()
            st.getLong(0)
        }
    }

    suspend fun listarSesiones(limite: Int = 20): List<RegistroSesion> = bd.usar { c ->
        c.prepare("SELECT * FROM sesion ORDER BY fin DESC, id DESC LIMIT ?").use { st ->
            st.bindLong(1, limite.toLong())
            buildList { while (st.step()) add(st.aRegistroSesion()) }
        }
    }

    /** Último nivel de dolor registrado (dolor después de la sesión más reciente). */
    suspend fun ultimoDolorRegistrado(): Int? = bd.usar { c ->
        c.prepare("SELECT dolor_despues FROM sesion ORDER BY fin DESC, id DESC LIMIT 1").use { st ->
            if (st.step()) st.getLong(0).toInt() else null
        }
    }

    suspend fun puntosDelDia(claveDia: String): Int = bd.usar { c ->
        c.prepare("SELECT COALESCE(SUM(puntos), 0) FROM sesion WHERE clave_dia = ?").use { st ->
            st.bindText(1, claveDia)
            st.step()
            st.getLong(0).toInt()
        }
    }

    suspend fun puntosTotales(): Int = bd.usar { c ->
        c.prepare("SELECT COALESCE(SUM(puntos), 0) FROM sesion").use { st ->
            st.step()
            st.getLong(0).toInt()
        }
    }

    suspend fun sesionesPendientesDeEnvio(): Int = bd.usar { c ->
        c.prepare("SELECT COUNT(*) FROM sesion WHERE pendiente_envio = 1").use { st ->
            st.step()
            st.getLong(0).toInt()
        }
    }

    private fun SQLiteStatement.aRegistroSesion(): RegistroSesion {
        val columnas = getColumnNames()
        fun texto(nombre: String) = getText(columnas.indexOf(nombre))
        fun entero(nombre: String) = getLong(columnas.indexOf(nombre))
        return RegistroSesion(
            id = entero("id"),
            rutinaId = entero("rutina_id").toInt(),
            inicioMillis = entero("inicio"),
            finMillis = entero("fin"),
            claveDia = texto("clave_dia"),
            ejerciciosRealizados = texto("ejercicios").split(",").mapNotNull { it.toIntOrNull() },
            ejerciciosAsignados = entero("asignados").toInt(),
            cumplimiento = entero("cumplimiento").toInt(),
            dolorAntes = entero("dolor_antes").toInt(),
            dolorDespues = entero("dolor_despues").toInt(),
            dificultad = Dificultad.valueOf(texto("dificultad")),
            observacion = texto("observacion"),
            puntos = entero("puntos").toInt(),
            alertaDolor = entero("alerta_dolor") == 1L,
            pendienteEnvio = entero("pendiente_envio") == 1L,
        )
    }
}
