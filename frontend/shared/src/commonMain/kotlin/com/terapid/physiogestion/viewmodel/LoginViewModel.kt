package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.AutenticacionRepositorio
import com.terapid.physiogestion.datos.remoto.ConfiguracionApi
import com.terapid.physiogestion.datos.remoto.ErrorApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla de inicio de sesión. */
data class LoginUiState(
    val usuario: String = "",
    val contrasena: String = "",
    val mostrarContrasena: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null,
    val urlServidor: String = ConfiguracionApi.urlServidor,
    val editandoServidor: Boolean = false,
)

/**
 * VIEW MODEL del login. Valida los campos, pide al repositorio que inicie
 * sesión y muestra el error si algo falla. No navega: cuando la sesión se
 * abre, la navegación principal la detecta y enruta según el rol.
 */
class LoginViewModel(private val autenticacion: AutenticacionRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(LoginUiState())
    val estado: StateFlow<LoginUiState> = _estado.asStateFlow()

    fun cambiarUsuario(texto: String) = _estado.update { it.copy(usuario = texto.take(40), error = null) }

    fun cambiarContrasena(texto: String) = _estado.update { it.copy(contrasena = texto.take(64), error = null) }

    fun alternarMostrarContrasena() = _estado.update { it.copy(mostrarContrasena = !it.mostrarContrasena) }

    fun alternarEdicionServidor() = _estado.update { it.copy(editandoServidor = !it.editandoServidor) }

    fun cambiarServidor(url: String) = _estado.update { it.copy(urlServidor = url, error = null) }

    fun iniciarSesion() {
        val actual = _estado.value
        if (actual.cargando) return // ignora la doble pulsación (RFX-18)
        if (actual.usuario.isBlank() || actual.contrasena.isBlank()) {
            _estado.update { it.copy(error = "Escribe tu usuario y tu contraseña.") }
            return
        }
        ConfiguracionApi.urlServidor = actual.urlServidor.trim()
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                autenticacion.iniciarSesion(actual.usuario, actual.contrasena)
                _estado.update { it.copy(cargando = false, contrasena = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErrorApi) {
                _estado.update { it.copy(cargando = false, error = e.message) }
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = "Ocurrió un problema al iniciar sesión. Inténtalo de nuevo.") }
            }
        }
    }
}
