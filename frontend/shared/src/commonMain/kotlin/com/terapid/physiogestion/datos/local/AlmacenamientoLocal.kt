package com.terapid.physiogestion.datos.local

import com.terapid.physiogestion.modelo.MedicionEjercicio
import com.terapid.physiogestion.modelo.PruebaEscaleras
import com.terapid.physiogestion.modelo.RegistroSesion
import kotlinx.serialization.json.Json

/** Versión del esquema de la base SQLite. Se incrementa cada vez que cambia alguna tabla. */
const val VERSION_ESQUEMA = 4

/** Rutina guardada en el dispositivo y el momento en que se descargó. */
class RutinaGuardada(val json: String, val descargadaEn: Long)

/**
 * ALMACENAMIENTO LOCAL · HISTORIAL (rutina descargada, sesiones, mediciones y
 * pruebas de escaleras).
 *
 * El código común solo conoce estas operaciones; cada plataforma decide dónde
 * se guardan:
 *  - Android, iOS y escritorio: base de datos SQLite (AlmacenamientoSqlite, en sqliteMain).
 *  - Navegador: en memoria mientras la pestaña está abierta (AlmacenamientoEnMemoria,
 *    en webMain). La web la usa el personal; el paciente registra sus sesiones en la app.
 */
interface AlmacenamientoLocal {

    // ------------------------------------------------------------ Rutina

    suspend fun guardarRutina(pacienteId: Int, json: String, descargadaEn: Long)

    suspend fun leerRutina(pacienteId: Int): RutinaGuardada?

    // ------------------------------------------------------------ Sesiones

    /** Guarda la sesión y devuelve su id local. */
    suspend fun insertarSesion(s: RegistroSesion): Long

    /** Las sesiones más recientes primero. */
    suspend fun listarSesiones(limite: Int = 20): List<RegistroSesion>

    /** Cola de envío: sesiones guardadas que el servidor todavía no tiene (RFX-03). */
    suspend fun sesionesPendientes(): List<RegistroSesion>

    /** El servidor aceptó la sesión: sale de la cola y se recuerda el id que le asignó. */
    suspend fun marcarSesionEnviada(idLocal: Long, servidorId: Int?)

    /** Se eliminó en el servidor (DELETE): también se quita la copia local. */
    suspend fun eliminarSesionDelServidor(servidorId: Int)

    /** Último nivel de dolor registrado (dolor después de la sesión más reciente). */
    suspend fun ultimoDolorRegistrado(): Int?

    suspend fun puntosDelDia(claveDia: String): Int

    suspend fun puntosTotales(): Int

    suspend fun contarSesiones(): Int

    suspend fun sesionesPendientesDeEnvio(): Int

    // ------------------------------------------------------------ Mediciones con sensores

    suspend fun insertarMedicion(m: MedicionEjercicio): Long

    /** Mediciones de un día; si un ejercicio se midió varias veces, la más reciente va primero. */
    suspend fun medicionesDelDia(claveDia: String): List<MedicionEjercicio>

    suspend fun medicionesPendientes(): List<MedicionEjercicio>

    suspend fun marcarMedicionEnviada(id: Long)

    suspend fun contarMediciones(): Int

    // ------------------------------------------------------------ Pruebas de escaleras (barómetro)

    suspend fun insertarPruebaEscaleras(p: PruebaEscaleras): Long

    /** Las pruebas más recientes primero. */
    suspend fun pruebasEscaleras(limite: Int = 10): List<PruebaEscaleras>

    suspend fun pruebasEscalerasPendientes(): List<PruebaEscaleras>

    suspend fun marcarPruebaEscalerasEnviada(id: Long)
}

/** Crea el almacenamiento local de esta plataforma (SQLite o, en el navegador, memoria). */
expect fun crearAlmacenamientoLocal(json: Json): AlmacenamientoLocal
