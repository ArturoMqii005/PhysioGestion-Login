package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.AutenticacionRepositorio
import com.terapid.physiogestion.modelo.Sesion
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * VIEW MODEL de la navegación principal: expone la sesión actual (de ella
 * depende a qué vista se enruta) y permite cerrarla desde cualquier vista.
 */
class SesionViewModel(private val autenticacion: AutenticacionRepositorio) : ViewModel() {

    val sesion: StateFlow<Sesion?> = autenticacion.sesion

    fun cerrarSesion() {
        viewModelScope.launch { autenticacion.cerrarSesion() }
    }
}
