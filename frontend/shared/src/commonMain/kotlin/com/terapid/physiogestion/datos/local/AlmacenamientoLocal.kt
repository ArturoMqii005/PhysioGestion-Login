package com.terapid.physiogestion.datos.local

import androidx.sqlite.SQLiteStatement
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.EjercicioRealizado
import com.terapid.physiogestion.modelo.MedicionEjercicio
import com.terapid.physiogestion.modelo.RegistroSesion
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Rutina guardada en el teléfono y el momento en que se descargó. */
class RutinaGuardada(val json: String, val descargadaEn: Long)

/**
 * Operaciones de lectura y escritura sobre [BaseDatosLocal].
 * Aquí viven todas las sentencias SQL de la app: el resto del proyecto
 * nunca escribe SQL, pide lo que necesita con estas funciones.
 */
class AlmacenamientoLocal(
    private val bd: BaseDatosLocal,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val serializadorDetalle = ListSerializer(EjercicioRealizado.serializer())

    // ------------------------------------------------------------ Rutina

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

    // ------------------------------------------------------------ Sesiones

    suspend fun insertarSesion(s: RegistroSesion): Long = bd.usar { c ->
        c.prepare(
            """
            INSERT INTO sesion (rutina_id, inicio, fin, clave_dia, ejercicios, asignados, cumplimiento,
                dolor_antes, dolor_despues, dificultad, observacion, puntos, alerta_dolor, pendiente_envio,
                servidor_id, detalle_ejercicios)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            if (s.servidorId != null) st.bindLong(15, s.servidorId.toLong()) else st.bindNull(15)
            st.bindText(16, json.encodeToString(serializadorDetalle, s.detalle))
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

    /** Cola de envío: sesiones guardadas que el servidor todavía no tiene (RFX-03). */
    suspend fun sesionesPendientes(): List<RegistroSesion> = bd.usar { c ->
        c.prepare("SELECT * FROM sesion WHERE pendiente_envio = 1 ORDER BY id").use { st ->
            buildList { while (st.step()) add(st.aRegistroSesion()) }
        }
    }

    /** El servidor aceptó la sesión: sale de la cola y se recuerda el id que le asignó. */
    suspend fun marcarSesionEnviada(idLocal: Long, servidorId: Int?) = bd.usar { c ->
        c.prepare("UPDATE sesion SET pendiente_envio = 0, servidor_id = ? WHERE id = ?").use { st ->
            if (servidorId != null) st.bindLong(1, servidorId.toLong()) else st.bindNull(1)
            st.bindLong(2, idLocal)
            st.step()
        }
        Unit
    }

    /** Se eliminó en el servidor (DELETE): también se quita la copia del teléfono. */
    suspend fun eliminarSesionDelServidor(servidorId: Int) = bd.usar { c ->
        c.prepare("DELETE FROM sesion WHERE servidor_id = ?").use { st ->
            st.bindLong(1, servidorId.toLong())
            st.step()
        }
        Unit
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

    suspend fun puntosTotales(): Int = contar("SELECT COALESCE(SUM(puntos), 0) FROM sesion")

    suspend fun contarSesiones(): Int = contar("SELECT COUNT(*) FROM sesion")

    suspend fun sesionesPendientesDeEnvio(): Int = contar("SELECT COUNT(*) FROM sesion WHERE pendiente_envio = 1")

    // ------------------------------------------------------------ Mediciones con sensores

    suspend fun insertarMedicion(m: MedicionEjercicio): Long = bd.usar { c ->
        c.prepare(
            """
            INSERT INTO medicion_ejercicio (ejercicio_id, clave_dia, medido_en, repeticiones, repeticiones_objetivo,
                angulo_maximo, angulo_objetivo, duracion_segundos, enviado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { st ->
            st.bindLong(1, m.idEjercicio.toLong())
            st.bindText(2, m.claveDia)
            st.bindLong(3, m.medidoEn)
            st.bindLong(4, m.repeticiones.toLong())
            st.bindLong(5, m.repeticionesObjetivo.toLong())
            st.bindLong(6, m.anguloMaximo.toLong())
            if (m.anguloObjetivo != null) st.bindLong(7, m.anguloObjetivo.toLong()) else st.bindNull(7)
            st.bindLong(8, m.duracionSegundos.toLong())
            st.bindLong(9, if (m.enviado) 1L else 0L)
            st.step()
        }
        c.prepare("SELECT last_insert_rowid()").use { st ->
            st.step()
            st.getLong(0)
        }
    }

    /** Mediciones de un día; si un ejercicio se midió varias veces, la más reciente va primero. */
    suspend fun medicionesDelDia(claveDia: String): List<MedicionEjercicio> = bd.usar { c ->
        c.prepare("SELECT * FROM medicion_ejercicio WHERE clave_dia = ? ORDER BY medido_en DESC, id DESC").use { st ->
            st.bindText(1, claveDia)
            buildList { while (st.step()) add(st.aMedicion()) }
        }
    }

    suspend fun medicionesPendientes(): List<MedicionEjercicio> = bd.usar { c ->
        c.prepare("SELECT * FROM medicion_ejercicio WHERE enviado = 0 ORDER BY id").use { st ->
            buildList { while (st.step()) add(st.aMedicion()) }
        }
    }

    suspend fun marcarMedicionEnviada(id: Long) = bd.usar { c ->
        c.prepare("UPDATE medicion_ejercicio SET enviado = 1 WHERE id = ?").use { st ->
            st.bindLong(1, id)
            st.step()
        }
        Unit
    }

    suspend fun contarMediciones(): Int = contar("SELECT COUNT(*) FROM medicion_ejercicio")

    // ------------------------------------------------------------ Conversión de filas

    private suspend fun contar(sql: String): Int = bd.usar { c ->
        c.prepare(sql).use { st ->
            st.step()
            st.getLong(0).toInt()
        }
    }

    private fun SQLiteStatement.aRegistroSesion(): RegistroSesion {
        val columnas = getColumnNames()
        fun texto(nombre: String) = getText(columnas.indexOf(nombre))
        fun entero(nombre: String) = getLong(columnas.indexOf(nombre))
        val indiceServidor = columnas.indexOf("servidor_id")
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
            servidorId = if (isNull(indiceServidor)) null else getLong(indiceServidor).toInt(),
            detalle = try {
                json.decodeFromString(serializadorDetalle, texto("detalle_ejercicios"))
            } catch (_: Exception) {
                emptyList()
            },
        )
    }

    private fun SQLiteStatement.aMedicion(): MedicionEjercicio {
        val columnas = getColumnNames()
        fun entero(nombre: String) = getLong(columnas.indexOf(nombre))
        val indiceObjetivo = columnas.indexOf("angulo_objetivo")
        return MedicionEjercicio(
            id = entero("id"),
            idEjercicio = entero("ejercicio_id").toInt(),
            claveDia = getText(columnas.indexOf("clave_dia")),
            medidoEn = entero("medido_en"),
            repeticiones = entero("repeticiones").toInt(),
            repeticionesObjetivo = entero("repeticiones_objetivo").toInt(),
            anguloMaximo = entero("angulo_maximo").toInt(),
            anguloObjetivo = if (isNull(indiceObjetivo)) null else getLong(indiceObjetivo).toInt(),
            duracionSegundos = entero("duracion_segundos").toInt(),
            enviado = entero("enviado") == 1L,
        )
    }
}
