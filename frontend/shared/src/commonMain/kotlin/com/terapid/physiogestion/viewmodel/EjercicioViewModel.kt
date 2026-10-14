package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.RutinaRepositorio
import com.terapid.physiogestion.datos.SensorRepositorio
import com.terapid.physiogestion.datos.sensores.Calibracion
import com.terapid.physiogestion.datos.sensores.FiltroSenal
import com.terapid.physiogestion.datos.sensores.LecturaEje
import com.terapid.physiogestion.datos.sensores.PeriodosMuestreo
import com.terapid.physiogestion.datos.sensores.SensorMovimiento
import com.terapid.physiogestion.servicio.movimiento.AnalizadorMovimiento
import com.terapid.physiogestion.servicio.movimiento.RangoArticular
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

/**
 * Coordina la medición de un ejercicio con los sensores del teléfono y
 * recorre las cuatro fases de la gestión de sensores:
 *
 *  1. HABILITACIÓN: revisa que haya acelerómetro (y giroscopio).
 *  2. CALIBRACIÓN: promedia 1.5 s de lecturas con el teléfono quieto.
 *  3. OBTENCIÓN: recoge los dos flujos de lecturas a 50 Hz.
 *  4. PROCESAMIENTO: filtra cada lectura y se la entrega al analizador.
 *
 * Su responsabilidad más importante es que el sensor se apague siempre, tanto
 * si el paciente toca «Terminar» como si sale de la pantalla (onCleared).
 */
class EjercicioViewModel(
    private val idEjercicio: Int,
    private val pacienteId: Int,
    private val rutinas: RutinaRepositorio,
    private val repositorio: SensorRepositorio,
    private val sensor: SensorMovimiento,
) : ViewModel() {

    private val filtro = FiltroSenal()
    private var analizador = AnalizadorMovimiento(anguloObjetivo = null)

    private val _estado = MutableStateFlow(
        EjercicioUiState(hayAcelerometro = sensor.hayAcelerometro, hayGiroscopio = sensor.hayGiroscopio)
    )
    val estado: StateFlow<EjercicioUiState> = _estado.asStateFlow()

    private var trabajoAcelerometro: Job? = null
    private var trabajoGiroscopio: Job? = null
    private var trabajoReloj: Job? = null

    /** Marcador monotónico: no cambia aunque el usuario ajuste la hora del teléfono. */
    private var inicioMedicion: TimeMark? = null

    init {
        cargar()
    }

    /** Busca el ejercicio en la rutina (la navegación solo transporta su id). */
    fun cargar() {
        viewModelScope.launch {
            try {
                val rutina = rutinas.obtenerRutina(pacienteId).rutina
                val ejercicio = rutina.ejercicios.firstOrNull { it.id == idEjercicio }
                if (ejercicio == null) {
                    _estado.update { it.copy(mensajeError = "No se encontró el ejercicio en tu rutina.") }
                } else {
                    analizador = AnalizadorMovimiento(anguloObjetivo = ejercicio.anguloObjetivo)
                    _estado.update { it.copy(ejercicio = ejercicio, mensajeError = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(mensajeError = "No pudimos cargar el ejercicio. Regresa e inténtalo de nuevo.") }
            }
        }
    }

    /** Botón «Comenzar». Con sensores pasa a calibrar; sin sensores empieza el conteo manual. */
    fun comenzar() {
        if (_estado.value.ejercicio == null) return
        if (sensor.hayAcelerometro) calibrar() else comenzarManual()
    }

    /** FASE 2: el paciente mantiene el teléfono quieto y se mide la postura inicial y el sesgo del giroscopio. */
    private fun calibrar() {
        _estado.update { it.copy(fase = FaseMedicion.CALIBRANDO, mensajeError = null) }
        viewModelScope.launch {
            val aceleraciones = mutableListOf<LecturaEje>()
            val giros = mutableListOf<LecturaEje>()
            val a = launch { sensor.acelerometro(PeriodosMuestreo.EJERCICIO_MS).collect { aceleraciones += it } }
            val g = if (sensor.hayGiroscopio) {
                launch { sensor.giroscopio(PeriodosMuestreo.EJERCICIO_MS).collect { giros += it } }
            } else {
                null
            }
            delay(FiltroSenal.DURACION_CALIBRACION_MS)
            a.cancel()
            g?.cancel()

            when (filtro.calibrar(aceleraciones.toList(), giros.toList())) {
                Calibracion.CORRECTA -> comenzarMedicion()
                Calibracion.POCAS_MUESTRAS -> _estado.update {
                    it.copy(fase = FaseMedicion.PREPARADO, mensajeError = "El sensor no respondió. Inténtalo de nuevo.")
                }
                Calibracion.HUBO_MOVIMIENTO -> _estado.update {
                    it.copy(
                        fase = FaseMedicion.PREPARADO,
                        mensajeError = "Te moviste durante la calibración. Mantén el teléfono quieto y vuelve a intentarlo.",
                    )
                }
            }
        }
    }

    /** FASES 3 y 4: recoge los dos sensores y procesa cada lectura. */
    private fun comenzarMedicion() {
        val referencia = filtro.referencia ?: return
        analizador.iniciar(referencia)
        filtro.reiniciar()
        iniciarReloj()
        _estado.update { it.copy(fase = FaseMedicion.MIDIENDO, repeticiones = 0, anguloActual = 0f, anguloMaximo = 0f) }

        // Con giroscopio, la fusión ya filtra el ruido del acelerómetro (solo pesa un 2 %), así que
        // recibe la lectura sin el paso bajo para no sumarle retraso. Sin giroscopio, se usa el paso bajo.
        val hayFusion = sensor.hayGiroscopio
        trabajoAcelerometro = viewModelScope.launch {
            sensor.acelerometro(PeriodosMuestreo.EJERCICIO_MS).collect { lectura ->
                analizador.procesarAceleracion(if (hayFusion) lectura else filtro.filtrarAceleracion(lectura))
                publicar()
            }
        }
        if (hayFusion) {
            trabajoGiroscopio = viewModelScope.launch {
                sensor.giroscopio(PeriodosMuestreo.EJERCICIO_MS).collect { lectura ->
                    analizador.procesarGiro(filtro.corregirGiro(lectura))
                    publicar()
                }
            }
        }
    }

    /**
     * Llegan 100 lecturas por segundo; la pantalla solo se actualiza cuando
     * cambia el conteo o el ángulo se mueve al menos un grado.
     */
    private fun publicar() {
        val actual = _estado.value
        val reps = analizador.totalRepeticiones
        val angulo = analizador.anguloActual
        if (reps == actual.repeticiones && abs(angulo - actual.anguloActual) < 1f) return
        _estado.update {
            it.copy(
                repeticiones = reps,
                anguloActual = angulo,
                anguloMaximo = analizador.anguloMaximo,
                rango = RangoArticular.clasificar(angulo, it.ejercicio?.anguloObjetivo),
            )
        }
    }

    // ---- Modo manual (sin acelerómetro) ----

    private fun comenzarManual() {
        iniciarReloj()
        _estado.update { it.copy(fase = FaseMedicion.MIDIENDO, repeticiones = 0, mensajeError = null) }
    }

    fun sumarRepeticion() = _estado.update { it.copy(repeticiones = it.repeticiones + 1) }

    fun restarRepeticion() = _estado.update { it.copy(repeticiones = (it.repeticiones - 1).coerceAtLeast(0)) }

    // ---- Fin ----

    /**
     * Detiene los sensores y guarda ÚNICAMENTE el resumen. Si se midió con
     * sensores, también se envía al servidor (POST /resultados-ejercicio).
     */
    fun terminar() {
        detenerSensores()
        val actual = _estado.value
        val ejercicio = actual.ejercicio ?: return
        val duracion = inicioMedicion?.elapsedNow()?.inWholeSeconds?.toInt() ?: 0

        if (actual.modoManual) {
            // Conteo manual: lo declara el paciente, no es una medición de sensores; no se envía como tal.
            _estado.update { it.copy(fase = FaseMedicion.TERMINADO, segundos = duracion) }
            return
        }

        _estado.update { it.copy(fase = FaseMedicion.TERMINADO, guardando = true, segundos = duracion) }
        viewModelScope.launch {
            try {
                val resultado = analizador.resultado(ejercicio.id, ejercicio.repeticionesTotales, duracion)
                val envio = repositorio.registrar(resultado)
                _estado.update { it.copy(guardando = false, enviado = envio.enviada, detalleTecnico = envio.detalle) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update {
                    it.copy(guardando = false, enviado = false, mensajeError = "No pudimos guardar la medición en el teléfono.")
                }
            }
        }
    }

    /** Repetir el ejercicio sin salir de la pantalla. */
    fun reintentar() {
        detenerSensores()
        filtro.reiniciar()
        _estado.update {
            it.copy(
                fase = FaseMedicion.PREPARADO,
                repeticiones = 0,
                anguloActual = 0f,
                anguloMaximo = 0f,
                enviado = null,
                segundos = 0,
                mensajeError = null,
            )
        }
    }

    private fun iniciarReloj() {
        inicioMedicion = TimeSource.Monotonic.markNow()
        trabajoReloj?.cancel()
        trabajoReloj = viewModelScope.launch {
            while (isActive) {
                _estado.update { it.copy(segundos = inicioMedicion?.elapsedNow()?.inWholeSeconds?.toInt() ?: 0) }
                delay(1_000)
            }
        }
    }

    private fun detenerSensores() {
        trabajoAcelerometro?.cancel()
        trabajoGiroscopio?.cancel()
        trabajoReloj?.cancel()
        trabajoAcelerometro = null
        trabajoGiroscopio = null
        trabajoReloj = null
        sensor.detener()
    }

    /**
     * Red de seguridad: si el paciente sale de la pantalla sin tocar
     * «Terminar», el ViewModel se destruye y el sensor se apaga de todos modos.
     * Sin esto el acelerómetro seguiría a 50 Hz gastando batería.
     */
    override fun onCleared() {
        detenerSensores()
        super.onCleared()
    }
}
