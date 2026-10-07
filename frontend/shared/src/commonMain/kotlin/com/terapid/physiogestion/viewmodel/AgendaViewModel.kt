package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.EstadoCita
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
) {
    fun cuantas(estado: EstadoCita): Int = citas.count { it.estado == estado }

    /** Primera cita que todavía no se atiende (para resaltarla). */
    val siguiente: Cita?
        get() = citas.firstOrNull { it.estado == EstadoCita.CONFIRMADA || it.estado == EstadoCita.PENDIENTE }

    val porFisioterapeuta: Map<String, List<Cita>> get() = citas.groupBy { it.fisioterapeuta }
}

/**
 * VIEW MODEL de la agenda. Lo usan la vista del fisioterapeuta y la de
 * recepción; el backend decide qué citas recibe cada rol.
 */
class AgendaViewModel(private val clinica: ClinicaRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(AgendaUiState())
    val estado: StateFlow<AgendaUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val citas = clinica.citas()
                _estado.update { it.copy(cargando = false, citas = citas) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val mensaje = (e as? ErrorApi)?.message ?: "No pudimos cargar la información. Inténtalo de nuevo."
                _estado.update { it.copy(cargando = false, error = mensaje) }
            }
        }
    }
}
