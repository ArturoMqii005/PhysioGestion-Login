package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.PruebaEscalerasRegistrada
import com.terapid.physiogestion.modelo.SesionTerapia
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.claveDelDia
import com.terapid.physiogestion.servicio.ReglasAgenda
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgendaUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val citas: List<Cita> = emptyList(),
    /** Día que se está viendo («2026-11-04») y el día de hoy. */
    val fecha: String = "",
    val hoy: String = "",
    /** Solo para el fisioterapeuta: sesiones que sus pacientes enviaron desde casa. */
    val seguimiento: List<SesionTerapia> = emptyList(),
    val errorSeguimiento: String? = null,
    /** Solo para el fisioterapeuta: pruebas de escaleras de sus pacientes. */
    val escaleras: List<PruebaEscalerasRegistrada> = emptyList(),
    /** Cita que se está actualizando (para deshabilitar sus botones). */
    val procesando: Int? = null,
    val aviso: String? = null,
    /** Cita cuya cancelación se está confirmando y el motivo que se escribe. */
    val cancelando: Cita? = null,
    val notaCancelacion: String = "",
) {
    fun cuantas(estado: EstadoCita): Int = citas.count { it.estado == estado }

    /** Primera cita que todavía no se atiende (para resaltarla). */
    val siguiente: Cita?
        get() = citas.firstOrNull { it.abierta }

    val porFisioterapeuta: Map<String, List<Cita>> get() = citas.groupBy { it.fisioterapeuta }

    val etiquetaDia: String get() = if (fecha.isEmpty()) "" else ReglasAgenda.etiquetaDia(fecha, hoy)

    /** La asistencia se marca el día de la cita o después. */
    val puedeMarcarAsistencia: Boolean get() = fecha.isNotEmpty() && fecha <= hoy
}

/**
 * VIEW MODEL de la agenda. Lo usan la vista del fisioterapeuta y la de recepción;
 * el backend decide qué citas recibe cada rol. Recepción confirma y cancela
 * (CIT-06) y el fisioterapeuta marca la asistencia (CIT-07).
 */
class AgendaViewModel(
    private val clinica: ClinicaRepositorio,
    /** true para el fisioterapeuta: además de la agenda carga el seguimiento remoto. */
    private val conSeguimiento: Boolean = false,
    hoy: String = claveDelDia(ahoraEnMilisegundos()),
) : ViewModel() {

    private val _estado = MutableStateFlow(AgendaUiState(fecha = hoy, hoy = hoy))
    val estado: StateFlow<AgendaUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        val fecha = _estado.value.fecha
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val citas = clinica.citas(fecha)
                _estado.update { if (it.fecha == fecha) it.copy(cargando = false, citas = citas) else it }
                if (conSeguimiento) cargarSeguimiento()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    /** Día anterior o siguiente (los domingos la clínica no abre: se saltan). */
    fun cambiarDia(delta: Int) {
        var nueva = ReglasAgenda.sumarDias(_estado.value.fecha, delta)
        if (ReglasAgenda.diaSemana(nueva) == 6) nueva = ReglasAgenda.sumarDias(nueva, if (delta >= 0) 1 else -1)
        _estado.update { it.copy(fecha = nueva, citas = emptyList(), aviso = null) }
        cargar()
    }

    fun irAHoy() {
        if (_estado.value.fecha == _estado.value.hoy) return
        _estado.update { it.copy(fecha = it.hoy, citas = emptyList(), aviso = null) }
        cargar()
    }

    fun confirmar(cita: Cita) = actualizar(cita, EstadoCita.CONFIRMADA, null, "Cita de ${cita.paciente} confirmada.")

    /** El fisioterapeuta registra si el paciente llegó (CIT-07). */
    fun marcarAsistencia(cita: Cita, llego: Boolean) = actualizar(
        cita,
        if (llego) EstadoCita.ATENDIDA else EstadoCita.NO_ASISTIO,
        null,
        if (llego) "${cita.paciente}: atendida." else "${cita.paciente}: no asistió.",
    )

    fun pedirCancelar(cita: Cita) = _estado.update { it.copy(cancelando = cita, notaCancelacion = "") }
    fun cambiarNota(t: String) = _estado.update { it.copy(notaCancelacion = t.take(200)) }
    fun cerrarCancelacion() = _estado.update { it.copy(cancelando = null) }

    fun confirmarCancelacion() {
        val s = _estado.value
        val cita = s.cancelando ?: return
        _estado.update { it.copy(cancelando = null) }
        actualizar(cita, EstadoCita.CANCELADA, s.notaCancelacion.ifBlank { null }, "Cita de ${cita.paciente} cancelada. La hora quedó libre.")
    }

    /** Al regresar de agendar o reprogramar, se recarga la agenda en el día de la cita. */
    fun mostrar(cita: Cita) {
        _estado.update { it.copy(fecha = cita.fecha.ifEmpty { it.fecha }, aviso = "Cita de ${cita.paciente} guardada: ${ReglasAgenda.etiquetaDia(cita.fecha, it.hoy)} a las ${cita.hora}.") }
        cargar()
    }

    private fun actualizar(cita: Cita, estado: EstadoCita, nota: String?, exito: String) {
        if (_estado.value.procesando != null) return
        _estado.update { it.copy(procesando = cita.id, aviso = null) }
        viewModelScope.launch {
            try {
                val nueva = clinica.cambiarEstadoCita(cita.id, estado, nota)
                _estado.update { s -> s.copy(procesando = null, citas = s.citas.map { if (it.id == nueva.id) nueva else it }, aviso = exito) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(procesando = null, aviso = mensajeDe(e)) }
            }
        }
    }

    private suspend fun cargarSeguimiento() {
        try {
            val sesiones = clinica.sesionesDeMisPacientes()
            _estado.update { it.copy(seguimiento = sesiones, errorSeguimiento = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _estado.update { it.copy(errorSeguimiento = (e as? ErrorApi)?.message ?: "No se pudo cargar el seguimiento.") }
        }
        try {
            val pruebas = clinica.pruebasEscalerasDeMisPacientes()
            _estado.update { it.copy(escaleras = pruebas) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Un servidor sin este recurso (versión anterior) no debe romper la agenda.
        }
    }
}
