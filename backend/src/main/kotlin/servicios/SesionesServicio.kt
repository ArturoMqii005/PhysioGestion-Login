package com.terapid.physiogestion.servicios

import com.terapid.physiogestion.datos.AlmacenDocumentos
import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.datos.Coleccion
import com.terapid.physiogestion.modelos.CrearSesionSolicitud
import com.terapid.physiogestion.modelos.MetodoMedicion
import com.terapid.physiogestion.modelos.ModificarSesionSolicitud
import com.terapid.physiogestion.modelos.PruebaEscalerasRegistrada
import com.terapid.physiogestion.modelos.PruebaEscalerasSolicitud
import com.terapid.physiogestion.modelos.ReemplazarSesionSolicitud
import com.terapid.physiogestion.modelos.ResultadoEjercicio
import com.terapid.physiogestion.modelos.ResultadoEjercicioSolicitud
import com.terapid.physiogestion.modelos.Rutina
import com.terapid.physiogestion.modelos.Semaforo
import com.terapid.physiogestion.modelos.SesionTerapia
import com.terapid.physiogestion.plugins.Conflicto
import com.terapid.physiogestion.plugins.DatosNoProcesables
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Clave de idempotencia ya usada y la sesión que creó (se guarda para que sobreviva a un reinicio). */
@Serializable
data class ClaveUsada(val llave: String, val idSesion: Int)

/** Resultado de un POST: la sesión y si se creó ahora (201) o ya existía por la clave de idempotencia (200). */
data class Creacion(val sesion: SesionTerapia, val nueva: Boolean)

/**
 * Reglas del recurso /sesiones y de los resultados medidos con sensores.
 *
 * En la etapa local todo vive en memoria (igual que los tokens): al
 * reiniciar el servidor se vuelve a cargar sesiones.json. Con [almacen] en
 * PostgreSQL (SRV-08), cada cambio se escribe en la base y se recupera al arrancar.
 */
class SesionesServicio(
    /** Rutinas vigentes; vienen de [ClinicaServicio], que permite modificarlas (RUT-08). */
    private val rutinas: () -> List<Rutina>,
    sesionesIniciales: List<SesionTerapia> = emptyList(),
    almacen: AlmacenDocumentos = AlmacenEnMemoria(),
    json: Json = Json { ignoreUnknownKeys = true },
    /** Se llama cuando una sesión queda en ROJO (dolor alto) para avisar al fisioterapeuta (NOT-03). */
    private val alQuedarEnRojo: (SesionTerapia) -> Unit = {},
) {
    private val colSesiones = Coleccion(almacen, "sesiones", SesionTerapia.serializer(), json) { it.id.toString() }
    private val colClaves = Coleccion(almacen, "claves_idempotencia", ClaveUsada.serializer(), json) { it.llave }
    private val colResultados = Coleccion(almacen, "resultados_ejercicio", ResultadoEjercicio.serializer(), json) { it.id.toString() }
    private val colEscaleras = Coleccion(almacen, "pruebas_escaleras", PruebaEscalerasRegistrada.serializer(), json) { it.id.toString() }

    private val sesiones = ConcurrentHashMap<Int, SesionTerapia>().apply {
        colSesiones.cargar(sesionesIniciales).forEach { put(it.id, it) }
    }
    private val siguienteId = AtomicInteger((sesiones.keys.maxOrNull() ?: 0) + 1)

    /** "pacienteId:clave" -> id de la sesión creada con esa clave (Idempotency-Key). */
    private val clavesUsadas = ConcurrentHashMap<String, Int>().apply {
        colClaves.cargar().forEach { put(it.llave, it.idSesion) }
    }

    private val resultados = ConcurrentHashMap<Int, ResultadoEjercicio>().apply {
        colResultados.cargar().forEach { put(it.id, it) }
    }
    private val siguienteResultado = AtomicInteger((resultados.keys.maxOrNull() ?: 0) + 1)

    private val pruebasEscaleras = ConcurrentHashMap<Int, PruebaEscalerasRegistrada>().apply {
        colEscaleras.cargar().forEach { put(it.id, it) }
    }
    private val siguientePrueba = AtomicInteger((pruebasEscaleras.keys.maxOrNull() ?: 0) + 1)

    fun rutinaDelPaciente(pacienteId: Int): Rutina? = rutinas().firstOrNull { it.pacienteId == pacienteId }

    /** Pacientes cuya rutina asignó el fisioterapeuta indicado. */
    fun pacientesDe(nombreFisioterapeuta: String): Set<Int> =
        rutinas().filter { it.fisioterapeuta == nombreFisioterapeuta }.map { it.pacienteId }.toSet()

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
        colSesiones.guardar(sesion)
        llave?.let {
            clavesUsadas[it] = sesion.id
            colClaves.guardar(ClaveUsada(it, sesion.id))
        }
        if (sesion.semaforo == Semaforo.ROJO) avisarRojo(sesion)
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
        colSesiones.borrar(id.toString())
        clavesUsadas.entries.filter { it.value == borrada.id }.forEach {
            clavesUsadas.remove(it.key)
            colClaves.borrar(it.key)
        }
        return true
    }

    // ---------------------------------------------------------------- Sensores

    /** Guarda el resumen de un ejercicio medido con sensores (RFN-26) o verificado con cámara (RFN-27). */
    fun registrarResultado(pacienteId: Int, s: ResultadoEjercicioSolicitud): ResultadoEjercicio {
        val rutina = rutinaDelPaciente(pacienteId)
            ?: throw DatosNoProcesables("Este paciente aún no tiene una rutina asignada.")
        val ejercicio = rutina.ejercicios.firstOrNull { it.id == s.idEjercicio }
            ?: throw DatosNoProcesables("El ejercicio ${s.idEjercicio} no está en tu rutina.")
        if (s.metodo == MetodoMedicion.CAMARA && !ejercicio.admiteVerificacionPorCamara) {
            throw DatosNoProcesables("El ejercicio ${s.idEjercicio} no admite verificación con cámara.")
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
            metodo = s.metodo,
        )
        resultados[resultado.id] = resultado
        colResultados.guardar(resultado)
        return resultado
    }

    fun resultadosDe(pacientes: Set<Int>): List<ResultadoEjercicio> =
        resultados.values.filter { it.pacienteId in pacientes }.sortedByDescending { it.id }

    // ---------------------------------------------------------------- Prueba de escaleras (barómetro)

    /** Guarda una prueba de escaleras. Rechaza (422) valores que ningún barómetro de una casa puede dar. */
    fun registrarPruebaEscaleras(pacienteId: Int, s: PruebaEscalerasSolicitud): PruebaEscalerasRegistrada {
        val rutina = rutinaDelPaciente(pacienteId)
        if (s.fecha.isBlank()) throw DatosNoProcesables("Falta la fecha de la prueba.")
        if (s.alturaSubidaCm !in 0..ALTURA_MAXIMA_CM || s.pisos !in 0..MAXIMO_PISOS || s.escalonesEstimados < 0 ||
            (s.pasos != null && s.pasos < 0) || s.duracionSegundos !in 1..DURACION_MAXIMA_S
        ) {
            throw DatosNoProcesables("Los valores de la prueba de escaleras están fuera de rango.")
        }
        val prueba = PruebaEscalerasRegistrada(
            id = siguientePrueba.getAndIncrement(),
            pacienteId = pacienteId,
            paciente = rutina?.paciente ?: "",
            fecha = s.fecha,
            alturaSubidaCm = s.alturaSubidaCm,
            pisos = s.pisos,
            escalonesEstimados = s.escalonesEstimados,
            pasos = s.pasos,
            duracionSegundos = s.duracionSegundos,
        )
        pruebasEscaleras[prueba.id] = prueba
        colEscaleras.guardar(prueba)
        return prueba
    }

    fun pruebasEscalerasDe(pacientes: Set<Int>): List<PruebaEscalerasRegistrada> =
        pruebasEscaleras.values.filter { it.pacienteId in pacientes }.sortedByDescending { it.id }

    // ---------------------------------------------------------------- Reglas

    private fun guardar(sesion: SesionTerapia): SesionTerapia {
        val umbral = rutinaDelPaciente(sesion.pacienteId)?.umbralDolor ?: UMBRAL_DOLOR_PREDETERMINADO
        val actualizada = sesion.copy(semaforo = semaforo(sesion.porcentajeCumplimiento, sesion.dolorDespues, umbral))
        sesiones[actualizada.id] = actualizada
        colSesiones.guardar(actualizada)
        // Si el paciente corrige el dolor y la sesión pasa a rojo, también se avisa (una vez por sesión).
        if (actualizada.semaforo == Semaforo.ROJO && sesion.semaforo != Semaforo.ROJO) avisarRojo(actualizada)
        return actualizada
    }

    private fun avisarRojo(sesion: SesionTerapia) {
        runCatching { alQuedarEnRojo(sesion) }
    }

    private fun validarDolor(vararg niveles: Int) {
        if (niveles.any { it !in 0..10 }) throw DatosNoProcesables("El dolor se registra en una escala de 0 a 10.")
    }

    companion object {
        /** Debajo de este porcentaje de cumplimiento el semáforo se pone en amarillo. */
        const val UMBRAL_CUMPLIMIENTO_BAJO = 60
        const val UMBRAL_DOLOR_PREDETERMINADO = 7

        /** Límites de la prueba de escaleras: 200 m de subida, 60 pisos y una hora. */
        const val ALTURA_MAXIMA_CM = 20_000
        const val MAXIMO_PISOS = 60
        const val DURACION_MAXIMA_S = 3_600

        /** Semáforo de evolución (RFN-47). El dolor alto manda sobre el cumplimiento. */
        fun semaforo(porcentaje: Int, dolorDespues: Int, umbralDolor: Int): Semaforo = when {
            dolorDespues >= umbralDolor -> Semaforo.ROJO
            porcentaje < UMBRAL_CUMPLIMIENTO_BAJO -> Semaforo.AMARILLO
            else -> Semaforo.VERDE
        }
    }
}
