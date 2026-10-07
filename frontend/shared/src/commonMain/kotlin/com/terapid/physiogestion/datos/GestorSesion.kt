package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.Sesion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Guarda la sesión abierta (token + usuario) mientras la app está abierta.
 * La navegación observa [sesion]: al abrirse va a la vista del rol y al
 * cerrarse regresa al inicio de sesión.
 */
class GestorSesion {
    private val _sesion = MutableStateFlow<Sesion?>(null)
    val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()

    fun abrir(sesion: Sesion) {
        _sesion.value = sesion
    }

    fun cerrar() {
        _sesion.value = null
    }

    /** Token para el encabezado Authorization; si no hay sesión, se debe volver a iniciar. */
    fun token(): String = _sesion.value?.token ?: throw SesionVencida()
}
