package com.terapid.physiogestion.datos.local

import com.terapid.physiogestion.modelo.MedicionEjercicio
import com.terapid.physiogestion.modelo.PruebaEscaleras
import com.terapid.physiogestion.modelo.RegistroSesion
import kotlinx.serialization.json.Json

/** En el navegador el historial vive en memoria (ver [AlmacenamientoEnMemoria]). */
actual fun crearAlmacenamientoLocal(json: Json): AlmacenamientoLocal = AlmacenamientoEnMemoria()

/**
 * [AlmacenamientoLocal] para el navegador, que no tiene SQLite. Guarda lo mismo
 * que la base de datos del teléfono, pero solo mientras la pestaña está abierta:
 * la web la usa el personal y el historial de cada paciente ya está en el servidor.
 * Respeta el mismo orden y las mismas reglas que las consultas SQL.
 */
class AlmacenamientoEnMemoria : AlmacenamientoLocal {

    private val rutinas = mutableMapOf<Int, RutinaGuardada>()
    private val sesiones = mutableListOf<RegistroSesion>()
    private val mediciones = mutableListOf<MedicionEjercicio>()
    private val pruebas = mutableListOf<PruebaEscaleras>()
    private var siguienteId = 1L

    // ------------------------------------------------------------ Rutina

    override suspend fun guardarRutina(pacienteId: Int, json: String, descargadaEn: Long) {
        rutinas[pacienteId] = RutinaGuardada(json, descargadaEn)
    }

    override suspend fun leerRutina(pacienteId: Int): RutinaGuardada? = rutinas[pacienteId]

    // ------------------------------------------------------------ Sesiones

    override suspend fun insertarSesion(s: RegistroSesion): Long {
        val id = siguienteId++
        sesiones += s.copy(id = id)
        return id
    }

    override suspend fun listarSesiones(limite: Int): List<RegistroSesion> =
        sesiones.sortedWith(compareByDescending<RegistroSesion> { it.finMillis }.thenByDescending { it.id }).take(limite)

    override suspend fun sesionesPendientes(): List<RegistroSesion> =
        sesiones.filter { it.pendienteEnvio }.sortedBy { it.id }

    override suspend fun marcarSesionEnviada(idLocal: Long, servidorId: Int?) {
        sesiones.reemplazar({ it.id == idLocal }) { it.copy(pendienteEnvio = false, servidorId = servidorId) }
    }

    override suspend fun eliminarSesionDelServidor(servidorId: Int) {
        sesiones.removeAll { it.servidorId == servidorId }
    }

    override suspend fun ultimoDolorRegistrado(): Int? = listarSesiones(1).firstOrNull()?.dolorDespues

    override suspend fun puntosDelDia(claveDia: String): Int =
        sesiones.filter { it.claveDia == claveDia }.sumOf { it.puntos }

    override suspend fun puntosTotales(): Int = sesiones.sumOf { it.puntos }

    override suspend fun contarSesiones(): Int = sesiones.size

    override suspend fun sesionesPendientesDeEnvio(): Int = sesiones.count { it.pendienteEnvio }

    // ------------------------------------------------------------ Mediciones con sensores

    override suspend fun insertarMedicion(m: MedicionEjercicio): Long {
        val id = siguienteId++
        mediciones += m.copy(id = id)
        return id
    }

    override suspend fun medicionesDelDia(claveDia: String): List<MedicionEjercicio> =
        mediciones.filter { it.claveDia == claveDia }
            .sortedWith(compareByDescending<MedicionEjercicio> { it.medidoEn }.thenByDescending { it.id })

    override suspend fun medicionesPendientes(): List<MedicionEjercicio> =
        mediciones.filter { !it.enviado }.sortedBy { it.id }

    override suspend fun marcarMedicionEnviada(id: Long) {
        mediciones.reemplazar({ it.id == id }) { it.copy(enviado = true) }
    }

    override suspend fun contarMediciones(): Int = mediciones.size

    // ------------------------------------------------------------ Pruebas de escaleras

    override suspend fun insertarPruebaEscaleras(p: PruebaEscaleras): Long {
        val id = siguienteId++
        pruebas += p.copy(id = id)
        return id
    }

    override suspend fun pruebasEscaleras(limite: Int): List<PruebaEscaleras> =
        pruebas.sortedWith(compareByDescending<PruebaEscaleras> { it.medidoEn }.thenByDescending { it.id }).take(limite)

    override suspend fun pruebasEscalerasPendientes(): List<PruebaEscaleras> =
        pruebas.filter { !it.enviado }.sortedBy { it.id }

    override suspend fun marcarPruebaEscalerasEnviada(id: Long) {
        pruebas.reemplazar({ it.id == id }) { it.copy(enviado = true) }
    }
}

/** Cambia el primer elemento que cumple [condicion] (como un UPDATE ... WHERE id = ?). */
private fun <T> MutableList<T>.reemplazar(condicion: (T) -> Boolean, cambio: (T) -> T) {
    val i = indexOfFirst(condicion)
    if (i >= 0) this[i] = cambio(this[i])
}
