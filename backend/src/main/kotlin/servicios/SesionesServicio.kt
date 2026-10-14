package com.terapid.physiogestion.servicios

import com.terapid.physiogestion.modelos.CrearSesionSolicitud
import com.terapid.physiogestion.modelos.ModificarSesionSolicitud
import com.terapid.physiogestion.modelos.ReemplazarSesionSolicitud
import com.terapid.physiogestion.modelos.ResultadoEjercicio
import com.terapid.physiogestion.modelos.ResultadoEjercicioSolicitud
import com.terapid.physiogestion.modelos.Rutina
import com.terapid.physiogestion.modelos.Semaforo
import com.terapid.physiogestion.modelos.SesionTerapia
import com.terapid.physiogestion.plugins.Conflicto
import com.terapid.physiogestion.plugins.DatosNoProcesables
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Resultado de un POST: la sesión y si se creó ahora (201) o ya existía por la clave de idempotencia (200). */
data class Creacion(val sesion: SesionTerapia, val nueva: Boolean)

/**
 * Reglas del recurso /sesiones y de los resultados medidos con sensores.
 *
 * En la etapa local todo vive en memoria (igual que los tokens): al
 * reiniciar el servidor se vuelve a cargar sesiones.json. En la siguiente
 * etapa este servicio usará la base de datos.
 */
class SesionesServicio(
    private val rutinas: List<Rutina>,
    sesionesIniciales: List<SesionTerapia> = emptyList(),
) {
    private val sesiones = ConcurrentHashMap<Int, SesionTerapia>().apply {
        sesionesIniciales.forEach { put(it.id, it) }
    }
    private val siguienteId = AtomicInteger((sesionesIniciales.maxOfOrNull { it.id } ?: 0) + 1)

    /** "pacienteId:clave" -> id de la sesión creada con esa clave (Idempotency-Key). */
    private val clavesUsadas = ConcurrentHashMap<String, Int>()

    private val resultados = ConcurrentHashMap<Int, ResultadoEjercicio>()
    private val siguienteResultado = AtomicInteger(1)

    fun rutinaDelPaciente(pacienteId: Int): Rutina? = rutinas.firstOrNull { it.pacienteId == pacienteId }

    /** Pacientes cuya rutina asignó el fisioterapeuta indicado. */
    fun pacientesDe(nombreFisioterapeuta: String): Set<Int> =
        rutinas.filter { it.fisioterapeuta == nombreFisioterapeuta }.map { it.pacienteId }.toSet()

    // ---------------------------------------------------------------- GET

    /** Historial de un paciente, de la sesión más reciente a la más antigua, paginado. */
    fun listar(pacientes: Set<Int>, pagina: Int, tamano: Int): List<SesionTerapia> =
        sesiones.values
            .filter { it.pacienteId in pacientes }
            .sortedWith(compareByDescending<SesionTerapia> { it.fechaHoraInicio }.thenByDescending { it.id })
            .drop(pagina * tamano)
            .take(tamano)

    fun contar(pacientes: Set<Int>): Int = sesiones.values.count { it.pacienteId in pacientes }

    fun obtener(id: Int): SesionTerapia? = sesiones[id]

    // ---------------------------------------------------------------- POST

    /**
     * Registra una sesión terminada. Si llega otra vez la misma
     * [claveIdempotencia] (la app reintentó porque se cortó la conexión),
     * devuelve la sesión ya creada en lugar de duplicarla.
     */
    @Synchronized
    fun crear(pacienteId: Int, s: CrearSesionSolicitud, claveIdempotencia: String?): Creacion {
        val llave = claveIdempotencia?.takeIf { it.isNotBlank() }?.let { "$pacienteId:$it" }
        llave?.let { clavesUsadas[it] }?.let { id -> sesiones[id]?.let { return Creacion(it, nueva = false) } }

        val rutina = rutinaDelPaciente(pacienteId)
            ?.takeIf { it.id == s.idRutina }
            ?: throw DatosNoProcesables("La rutina ${s.idRutina} no corresponde a este paciente.")
        validarDolor(s.dolorAntes, s.dolorDespues)
        if (s.fechaHoraInicio.isBlank() || s.fechaHoraFin.isBlank() || s.fechaHoraFin < s.fechaHoraInicio) {
            throw DatosNoProcesables("La hora de fin debe ser igual o posterior a la hora de inicio.")
        }
        if (s.ejercicios.isEmpty()) throw DatosNoProcesables("La sesión debe incluir al menos un ejercicio.")
        val idsRutina = rutina.ejercicios.map { it.id }.toSet()
        s.ejercicios.forEach { e ->
            if (e.idEjercicio !in idsRutina) throw DatosNoProcesables("El ejercicio ${e.idEjercicio} no está en tu rutina.")
            if (e.repeticionesRealizadas < 0) throw DatosNoProcesables("Las repeticiones no pueden ser negativas.")
        }
        if (sesiones.values.any { it.pacienteId == pacienteId && it.fechaHoraInicio == s.fechaHoraInicio }) {
            throw Conflicto("Ya hay una sesión registrada con la misma hora de inicio.")
        }

        val realizados = s.ejercicios.map { it.idEjercicio }.distinct().size
        val porcentaje = realizados * 100 / rutina.ejercicios.size
        val sesion = SesionTerapia(
            id = siguienteId.getAndIncrement(),
            pacienteId = pacienteId,
            paciente = rutina.paciente,
            idRutina = rutina.id,
            fechaHoraInicio = s.fechaHoraInicio,
            fechaHoraFin = s.fechaHoraFin,
            dolorAntes = s.dolorAntes,
            dolorDespues = s.dolorDespues,
            dificultad = s.dificultad,
            observacion = s.observacion?.trim()?.take(280)?.ifBlank { null },
            ejercicios = s.ejercicios,
            porcentajeCumplimiento = porcentaje,
            semaforo = semaforo(porcentaje, s.dolorDespues, rutina.umbralDolor),
        )
        sesiones[sesion.id] = sesion
        llave?.let { clavesUsadas[it] = sesion.id }
        return Creacion(sesion, nueva = true)
    }

    // ---------------------------------------------------------------- PUT

    /** Reemplaza todos los datos editables. Lo que no se manda queda vacío (por eso la observación puede ser null). */
    fun reemplazar(actual: SesionTerapia, s: ReemplazarSesionSolicitud): SesionTerapia {
        validarDolor(s.dolorAntes, s.dolorDespues)
        return guardar(
            actual.copy(
                dolorAntes = s.dolorAntes,
                dolorDespues = s.dolorDespues,
                dificultad = s.dificultad,
                observacion = s.observacion?.trim()?.take(280)?.ifBlank { null },
            )
        )
    }

    // ---------------------------------------------------------------- PATCH

    /** Cambia solo los campos que llegaron; los nulos conservan su valor anterior. */
    fun modificar(actual: SesionTerapia, s: ModificarSesionSolicitud): SesionTerapia {
        if (s.dolorDespues == null && s.dificultad == null && s.observacion == null) {
            throw DatosNoProcesables("Indica al menos un campo para modificar.")
        }
        s.dolorDespues?.let { validarDolor(actual.dolorAntes, it) }
        return guardar(
            actual.copy(
                dolorDespues = s.dolorDespues ?: actual.dolorDespues,
                dificultad = s.dificultad ?: actual.dificultad,
                observacion = s.observacion?.trim()?.take(280)?.ifBlank { null } ?: actual.observacion,
            )
        )
    }

    // ---------------------------------------------------------------- DELETE

    /** true si existía y se borró; false si ya no estaba (DELETE es idempotente). */
    fun eliminar(id: Int): Boolean {
        val borrada = sesiones.remove(id) ?: return false
        clavesUsadas.entries.removeIf { it.value == borrada.id }
        return true
    }

    // ---------------------------------------------------------------- Sensores

    /** Guarda el resumen de un ejercicio medido con sensores (RFN-26). */
    fun registrarResultado(pacienteId: Int, s: ResultadoEjercicioSolicitud): ResultadoEjercicio {
        val rutina = rutinaDelPaciente(pacienteId)
            ?: throw DatosNoProcesables("Este paciente aún no tiene una rutina asignada.")
        if (rutina.ejercicios.none { it.id == s.idEjercicio }) {
            throw DatosNoProcesables("El ejercicio ${s.idEjercicio} no está en tu rutina.")
        }
        if (s.repeticiones < 0 || s.repeticionesObjetivo <= 0 || s.anguloMaximo !in 0..360 || s.duracionSegundos < 0) {
            throw DatosNoProcesables("Los valores medidos están fuera de rango.")
        }
        val cumple = s.repeticiones >= s.repeticionesObjetivo &&
            (s.anguloObjetivo == null || s.anguloMaximo >= s.anguloObjetivo)
        val resultado = ResultadoEjercicio(
            id = siguienteResultado.getAndIncrement(),
            pacienteId = pacienteId,
            idEjercicio = s.idEjercicio,
            repeticiones = s.repeticiones,
            repeticionesObjetivo = s.repeticionesObjetivo,
            anguloMaximo = s.anguloMaximo,
            anguloObjetivo = s.anguloObjetivo,
            duracionSegundos = s.duracionSegundos,
            cumpleObjetivo = cumple,
        )
        resultados[resultado.id] = resultado
        return resultado
    }

    fun resultadosDe(pacientes: Set<Int>): List<ResultadoEjercicio> =
        resultados.values.filter { it.pacienteId in pacientes }.sortedByDescending { it.id }

    // ---------------------------------------------------------------- Reglas

    private fun guardar(sesion: SesionTerapia): SesionTerapia {
        val umbral = rutinaDelPaciente(sesion.pacienteId)?.umbralDolor ?: UMBRAL_DOLOR_PREDETERMINADO
        val actualizada = sesion.copy(semaforo = semaforo(sesion.porcentajeCumplimiento, sesion.dolorDespues, umbral))
        sesiones[actualizada.id] = actualizada
        return actualizada
    }

    private fun validarDolor(vararg niveles: Int) {
        if (niveles.any { it !in 0..10 }) throw DatosNoProcesables("El dolor se registra en una escala de 0 a 10.")
    }

    companion object {
        /** Debajo de este porcentaje de cumplimiento el semáforo se pone en amarillo. */
        const val UMBRAL_CUMPLIMIENTO_BAJO = 60
        const val UMBRAL_DOLOR_PREDETERMINADO = 7

        /** Semáforo de evolución (RFN-47). El dolor alto manda sobre el cumplimiento. */
        fun semaforo(porcentaje: Int, dolorDespues: Int, umbralDolor: Int): Semaforo = when {
            dolorDespues >= umbralDolor -> Semaforo.ROJO
            porcentaje < UMBRAL_CUMPLIMIENTO_BAJO -> Semaforo.AMARILLO
            else -> Semaforo.VERDE
        }
    }
}
