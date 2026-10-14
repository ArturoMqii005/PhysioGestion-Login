package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.PreferenciasRepositorio
import com.terapid.physiogestion.datos.RutinaRepositorio
import com.terapid.physiogestion.datos.SensorRepositorio
import com.terapid.physiogestion.datos.SesionesRepositorio
import com.terapid.physiogestion.datos.SinRutinaDisponible
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.datos.sensores.SensorPasos
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.Rutina
import com.terapid.physiogestion.plataforma.permisos.GestorPermisos
import com.terapid.physiogestion.plataforma.permisos.Permiso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del contador de pasos en la pantalla de inicio. */
enum class EstadoPasos {
    /** El dispositivo no tiene contador de pasos (por ejemplo, la computadora o el emulador). */
    NO_DISPONIBLE,

    /** Falta el permiso de actividad física (Android 10+). */
    FALTA_PERMISO,

    /** El usuario negó el permiso. */
    PERMISO_DENEGADO,

    CONTANDO,
}

data class InicioPacienteUiState(
    val cargando: Boolean = true,
    val rutina: Rutina? = null,
    val origen: OrigenDatos? = null,
    val error: String? = null,
    val racha: Int = 0,
    val puntos: Int = 0,
    val medidosHoy: Int = 0,
    val pendientesEnvio: Int = 0,
    val pasos: EstadoPasos = EstadoPasos.NO_DISPONIBLE,
    val aviso: String? = null,
)

/**
 * VIEW MODEL del panel principal del paciente: resumen de la rutina,
 * racha, pasos del día y la cola de envío. Al abrir, intenta enviar lo que
 * se haya quedado guardado sin conexión.
 */
class InicioPacienteViewModel(
    private val pacienteId: Int,
    private val rutinas: RutinaRepositorio,
    private val sesiones: SesionesRepositorio,
    private val sensores: SensorRepositorio,
    private val preferencias: PreferenciasRepositorio,
    private val sensorPasos: SensorPasos,
    private val permisos: GestorPermisos,
) : ViewModel() {

    private val _estado = MutableStateFlow(InicioPacienteUiState())
    val estado: StateFlow<InicioPacienteUiState> = _estado.asStateFlow()

    init {
        revisarPasos()
        sincronizar()
    }

    /** Se llama cada vez que la pantalla aparece. */
    fun cargar() {
        viewModelScope.launch {
            try {
                val datos = rutinas.obtenerRutina(pacienteId)
                _estado.update { it.copy(cargando = false, rutina = datos.rutina, origen = datos.origen, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val causa = (e as? SinRutinaDisponible)?.cause as? ErrorApi
                _estado.update { it.copy(cargando = false, error = causa?.message ?: "No pudimos cargar tu rutina.") }
            }
            actualizarContadores()
        }
    }

    /** Envía las sesiones y mediciones guardadas sin conexión. */
    fun sincronizar() {
        viewModelScope.launch {
            try {
                val envio = sesiones.enviarPendientes()
                val mediciones = sensores.enviarPendientes()
                if (envio.error == null && envio.pendientes == 0) preferencias.registrarSincronizacion()
                if (envio.enviadas > 0 || mediciones > 0) {
                    _estado.update { it.copy(aviso = "Se enviaron a tu fisioterapeuta los registros guardados sin conexión.") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sin conexión: quedan en la cola para el siguiente intento.
            }
            actualizarContadores()
        }
    }

    fun cerrarAviso() = _estado.update { it.copy(aviso = null) }

    // ---- Contador de pasos ----

    private fun revisarPasos() {
        val estado = when {
            !sensorPasos.estaDisponible -> EstadoPasos.NO_DISPONIBLE
            permisos.estaConcedido(Permiso.ACTIVIDAD_FISICA) -> EstadoPasos.CONTANDO
            else -> EstadoPasos.FALTA_PERMISO
        }
        _estado.update { it.copy(pasos = estado) }
    }

    /** HABILITACIÓN: pide el permiso de actividad física y, si lo conceden, empieza a contar. */
    fun activarPasos() {
        viewModelScope.launch {
            val concedido = permisos.solicitar(Permiso.ACTIVIDAD_FISICA)
            _estado.update { it.copy(pasos = if (concedido) EstadoPasos.CONTANDO else EstadoPasos.PERMISO_DENEGADO) }
        }
    }

    /**
     * Pasos de hoy. La vista lo recoge solo mientras está visible
     * (collectAsStateWithLifecycle): al salir, el receptor del sensor se da de baja.
     */
    fun pasosDeHoy(): Flow<Long> =
        sensorPasos.pasos().map { lectura -> preferencias.pasosDeHoy(lectura, sensorPasos.cuentaDesdeInicioDelDia) }

    private suspend fun actualizarContadores() {
        try {
            val puntos = rutinas.puntosTotales()
            val pendientes = sesiones.pendientesDeEnvio()
            val medidos = sensores.medicionesDeHoy().size
            _estado.update {
                it.copy(racha = preferencias.racha(pacienteId), puntos = puntos, pendientesEnvio = pendientes, medidosHoy = medidos)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}
