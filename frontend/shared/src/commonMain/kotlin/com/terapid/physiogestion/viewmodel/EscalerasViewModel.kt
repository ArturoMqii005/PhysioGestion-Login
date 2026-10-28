package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.EscalerasRepositorio
import com.terapid.physiogestion.datos.RutinaRepositorio
import com.terapid.physiogestion.datos.sensores.LecturaPresion
import com.terapid.physiogestion.datos.sensores.SensorPasos
import com.terapid.physiogestion.datos.sensores.SensorPresion
import com.terapid.physiogestion.modelo.PruebaEscaleras
import com.terapid.physiogestion.plataforma.permisos.GestorPermisos
import com.terapid.physiogestion.plataforma.permisos.Permiso
import com.terapid.physiogestion.servicio.escaleras.AnalizadorEscaleras
import com.terapid.physiogestion.servicio.escaleras.CalibracionPresion
import com.terapid.physiogestion.servicio.escaleras.ResultadoEscaleras
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class FaseEscaleras { PREPARADO, CALIBRANDO, MIDIENDO, TERMINADO }

data class EscalerasUiState(
    val fase: FaseEscaleras = FaseEscaleras.PREPARADO,
    val hayBarometro: Boolean = true,
    val hayContadorPasos: Boolean = true,
    val metaFuncional: String? = null,
    /** Metros subidos (acumulado) y altura actual respecto del punto de partida. */
    val subida: Float = 0f,
    val alturaActual: Float = 0f,
    val pisos: Int = 0,
    val escalones: Int = 0,
    val pasos: Int? = null,
    val segundos: Int = 0,
    val mensajeError: String? = null,
    val guardando: Boolean = false,
    /** null mientras no termina; true si llegó al servidor; false si quedó en el teléfono. */
    val enviado: Boolean? = null,
    val resultado: ResultadoEscaleras? = null,
    /** Pruebas anteriores guardadas en el teléfono, la más reciente primero. */
    val historial: List<PruebaEscaleras> = emptyList(),
) {
    /** La mejor velocidad de subida antes de esta prueba (para comparar el avance). */
    val mejorAnterior: PruebaEscaleras?
        get() = historial.filter { it.duracionSegundos > 0 && it.alturaSubidaCm > 0 }.maxByOrNull { it.metrosPorMinuto }
}

/**
 * PRUEBA DE ESCALERAS con el barómetro y el contador de pasos.
 *
 * Mide lo que el paciente quiere recuperar («subir las escaleras de mi casa
 * sin dolor»): cuánto subió, en cuánto tiempo y a qué ritmo, para comparar
 * una semana con otra. Fases: habilitación (¿hay barómetro?), calibración
 * (3 s quieto al pie de la escalera), medición y resumen.
 */
class EscalerasViewModel(
    private val pacienteId: Int,
    private val rutinas: RutinaRepositorio,
    private val repositorio: EscalerasRepositorio,
    private val barometro: SensorPresion,
    private val sensorPasos: SensorPasos,
    private val permisos: GestorPermisos,
) : ViewModel() {

    private val analizador = AnalizadorEscaleras()

    private val _estado = MutableStateFlow(
        EscalerasUiState(hayBarometro = barometro.estaDisponible, hayContadorPasos = sensorPasos.estaDisponible)
    )
    val estado: StateFlow<EscalerasUiState> = _estado.asStateFlow()

    private var trabajoPresion: Job? = null
    private var trabajoPasos: Job? = null
    private var trabajoReloj: Job? = null
    private var inicio: TimeMark? = null
    private var pasosIniciales: Long? = null
    private var pasosActuales: Long? = null

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            try {
                val meta = rutinas.obtenerRutina(pacienteId).rutina.metaFuncional
                _estado.update { it.copy(metaFuncional = meta) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sin rutina la prueba funciona igual; solo no se muestra la meta.
            }
            actualizarHistorial()
        }
    }

    /** Botón «Comenzar»: pide (si hace falta) el permiso de actividad física y calibra 3 s. */
    fun comenzar() {
        if (!_estado.value.hayBarometro || _estado.value.fase != FaseEscaleras.PREPARADO) return
        viewModelScope.launch {
            val conPasos = sensorPasos.estaDisponible && permisos.solicitar(Permiso.ACTIVIDAD_FISICA)
            pasosIniciales = null
            pasosActuales = null
            _estado.update {
                it.copy(fase = FaseEscaleras.CALIBRANDO, mensajeError = null, resultado = null, enviado = null, pasos = null)
            }
            if (conPasos) {
                // Desde la calibración: así la primera lectura es la base y no se pierde ningún paso.
                trabajoPasos = launch {
                    sensorPasos.pasos().collect { total ->
                        if (pasosIniciales == null) pasosIniciales = total
                        pasosActuales = total
                        if (_estado.value.fase == FaseEscaleras.MIDIENDO) {
                            _estado.update { it.copy(pasos = pasosDurantePrueba()) }
                        }
                    }
                }
            }
            val lecturas = mutableListOf<LecturaPresion>()
            val recoleccion = launch { barometro.presion().collect { lecturas += it } }
            delay(AnalizadorEscaleras.DURACION_CALIBRACION_MS)
            recoleccion.cancel()
            when (analizador.calibrar(lecturas.toList())) {
                CalibracionPresion.CORRECTA -> comenzarMedicion()
                CalibracionPresion.POCAS_MUESTRAS -> volverAPreparado("El barómetro no respondió. Inténtalo de nuevo.")
                CalibracionPresion.INESTABLE ->
                    volverAPreparado("Te moviste mientras se calibraba. Quédate quieto al pie de la escalera y vuelve a intentarlo.")
            }
        }
    }

    private fun comenzarMedicion() {
        inicio = TimeSource.Monotonic.markNow()
        pasosIniciales = pasosActuales ?: pasosIniciales
        _estado.update { it.copy(fase = FaseEscaleras.MIDIENDO, subida = 0f, alturaActual = 0f, pisos = 0, escalones = 0, segundos = 0) }
        trabajoReloj = viewModelScope.launch {
            while (isActive) {
                _estado.update { it.copy(segundos = segundosTranscurridos()) }
                delay(1_000)
            }
        }
        trabajoPresion = viewModelScope.launch {
            barometro.presion().collect { lectura ->
                analizador.procesar(lectura)
                publicar()
            }
        }
    }

    /** Solo se redibuja cuando la altura cambia al menos 5 cm o cambia un conteo. */
    private fun publicar() {
        val actual = _estado.value
        val subida = analizador.subidaAcumulada
        if (abs(subida - actual.subida) < 0.05f && abs(analizador.alturaActual - actual.alturaActual) < 0.05f &&
            analizador.pisos == actual.pisos
        ) {
            return
        }
        _estado.update {
            it.copy(
                subida = subida,
                alturaActual = analizador.alturaActual,
                pisos = analizador.pisos,
                escalones = analizador.escalonesEstimados,
            )
        }
    }

    fun terminar() {
        if (_estado.value.fase != FaseEscaleras.MIDIENDO) return
        val duracion = segundosTranscurridos()
        val pasos = pasosDurantePrueba()
        detenerSensores()
        val resultado = analizador.resultado(pasos, duracion)
        _estado.update {
            it.copy(fase = FaseEscaleras.TERMINADO, guardando = true, resultado = resultado, segundos = duracion, pasos = pasos)
        }
        viewModelScope.launch {
            try {
                val envio = repositorio.registrar(resultado)
                _estado.update { it.copy(guardando = false, enviado = envio.enviada) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _estado.update { it.copy(guardando = false, enviado = false, mensajeError = "No pudimos guardar la prueba en el teléfono.") }
            }
        }
    }

    /** Vuelve a las instrucciones para otra prueba. El historial ya incluye la anterior. */
    fun reintentar() {
        detenerSensores()
        _estado.update {
            it.copy(fase = FaseEscaleras.PREPARADO, subida = 0f, alturaActual = 0f, pisos = 0, escalones = 0, pasos = null, segundos = 0)
        }
        viewModelScope.launch { actualizarHistorial() }
    }

    private suspend fun actualizarHistorial() {
        try {
            val historial = repositorio.historial()
            _estado.update { it.copy(historial = historial) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun volverAPreparado(mensaje: String) {
        detenerSensores()
        _estado.update { it.copy(fase = FaseEscaleras.PREPARADO, mensajeError = mensaje) }
    }

    private fun pasosDurantePrueba(): Int? {
        val desde = pasosIniciales ?: return null
        val hasta = pasosActuales ?: return null
        return (hasta - desde).toInt().coerceAtLeast(0)
    }

    private fun segundosTranscurridos(): Int = inicio?.elapsedNow()?.inWholeSeconds?.toInt() ?: 0

    private fun detenerSensores() {
        trabajoPresion?.cancel()
        trabajoPasos?.cancel()
        trabajoReloj?.cancel()
        trabajoPresion = null
        trabajoPasos = null
        trabajoReloj = null
        barometro.detener()
    }

    /** Si el paciente sale de la pantalla sin terminar, el barómetro se apaga igual. */
    override fun onCleared() {
        detenerSensores()
        super.onCleared()
    }
}
