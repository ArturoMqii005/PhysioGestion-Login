package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Usuario
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdministradorUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val usuarios: List<Usuario> = emptyList(),
) {
    /** Cuántos usuarios hay de cada rol, para las tarjetas de resumen. */
    val porRol: Map<Rol, Int> get() = Rol.entries.associateWith { rol -> usuarios.count { it.rol == rol } }
}

/** VIEW MODEL del panel de administración: carga los usuarios del sistema. */
class AdministradorViewModel(private val clinica: ClinicaRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(AdministradorUiState())
    val estado: StateFlow<AdministradorUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val usuarios = clinica.usuarios()
                _estado.update { it.copy(cargando = false, usuarios = usuarios) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val mensaje = (e as? ErrorApi)?.message ?: "No pudimos cargar la información. Inténtalo de nuevo."
                _estado.update { it.copy(cargando = false, error = mensaje) }
            }
        }
    }
}
