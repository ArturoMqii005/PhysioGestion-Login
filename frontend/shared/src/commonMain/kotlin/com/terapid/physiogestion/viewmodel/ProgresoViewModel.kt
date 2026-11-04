package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.EscalerasRepositorio
import com.terapid.physiogestion.datos.RutinaRepositorio
import com.terapid.physiogestion.datos.SesionesRepositorio
import com.terapid.physiogestion.plataforma.fechaHoraIso
import com.terapid.physiogestion.servicio.EstadoDia
import com.terapid.physiogestion.servicio.PuntoDolor
import com.terapid.physiogestion.servicio.PuntoRitmo
import com.terapid.physiogestion.servicio.ReglasProgreso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgresoUiState(
    val cargando: Boolean = true,
    val dolor: List<PuntoDolor> = emptyList(),
    val textoDolor: String = "",
    val umbralDolor: Int? = null,
    val ritmo: List<PuntoRitmo> = emptyList(),
    val mejoraRitmo: Int? = null,
    val calendario: List<EstadoDia> = emptyList(),
    val diasConSesion: Int = 0,
    val diasTranscurridos: Int = 0,
    /** true si no hubo conexión y se usa lo guardado en el teléfono. */
    val sinConexion: Boolean = false,
)

/**
 * VIEW MODEL de «Tu progreso»: dolor sesión por sesión con el límite de la
 * rutina, ritmo en la prueba de escaleras y calendario de constancia. Toma las
 * sesiones del servidor (las de todos sus teléfonos); sin conexión, las del teléfono.
 */
class ProgresoViewModel(
    private val pacienteId: Int,
    private val rutinas: RutinaRepositorio,
    private val sesiones: SesionesRepositorio,
    private val escaleras: EscalerasRepositorio,
    private val hoy: () -> String,
) : ViewModel() {
    private val _estado = MutableStateFlow(ProgresoUiState())
    val estado: StateFlow<ProgresoUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            val umbral = intentar { rutinas.obtenerRutina(pacienteId).rutina.umbralDolor }
            val delServidor = intentar { sesiones.historial(0, 40).datos.sesiones.map { it.fechaHoraInicio to it.dolorDespues } }
            val locales = intentar { sesiones.historialLocal(60).map { fechaHoraIso(it.inicioMillis) to it.dolorDespues } }.orEmpty()
            val todas = delServidor ?: locales
            val dias = (todas.map { it.first.take(10) } + locales.map { it.first.take(10) }).toSet()
            val pruebas = intentar { escaleras.historial(12).map { PuntoRitmo(it.claveDia, it.metrosPorMinuto) } }.orEmpty()

            val serie = ReglasProgreso.serieDolor(todas)
            val ritmo = ReglasProgreso.serieRitmo(pruebas)
            val calendario = ReglasProgreso.calendario(dias, hoy())
            val (hechos, transcurridos) = ReglasProgreso.constancia(calendario)
            _estado.update {
                it.copy(
                    cargando = false,
                    dolor = serie,
                    textoDolor = ReglasProgreso.textoDolor(serie),
                    umbralDolor = umbral,
                    ritmo = ritmo,
                    mejoraRitmo = ReglasProgreso.mejoraRitmo(ritmo),
                    calendario = calendario,
                    diasConSesion = hechos,
                    diasTranscurridos = transcurridos,
                    sinConexion = delServidor == null,
                )
            }
        }
    }

    private suspend fun <T> intentar(bloque: suspend () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
