package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.datos.CuentaRepositorio
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.modelo.AltaUsuarioRespuesta
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.servicio.ReglasCuenta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formulario de alta de personal (USR-02). */
data class AltaUiState(
    val nombre: String = "",
    val usuario: String = "",
    val correo: String = "",
    val rol: Rol = Rol.FISIOTERAPEUTA,
    val creando: Boolean = false,
    val error: String? = null,
    /** Cuenta recién creada: la contraseña temporal se muestra una sola vez. */
    val creada: AltaUsuarioRespuesta? = null,
)

data class AdministradorUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val usuarios: List<Usuario> = emptyList(),
    val mostrandoAlta: Boolean = false,
    val alta: AltaUiState = AltaUiState(),
    /** Usuario que se está activando o desactivando (para deshabilitar su botón). */
    val cambiandoId: Int? = null,
    val aviso: String? = null,
) {
    /** Cuántos usuarios hay de cada rol, para las tarjetas de resumen. */
    val porRol: Map<Rol, Int> get() = Rol.entries.associateWith { rol -> usuarios.count { it.rol == rol } }
}

/** VIEW MODEL del panel de administración: usuarios del sistema, alta del personal y activación (ADM-01..03). */
class AdministradorViewModel(
    private val clinica: ClinicaRepositorio,
    private val cuentas: CuentaRepositorio? = null,
    private val miId: Int? = null,
) : ViewModel() {

    private val _estado = MutableStateFlow(AdministradorUiState())
    val estado: StateFlow<AdministradorUiState> = _estado.asStateFlow()

    /** true si el panel puede dar de alta y desactivar cuentas. */
    val puedeAdministrar: Boolean get() = cuentas != null

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

    // ---- Alta de personal ----

    fun abrirAlta() = _estado.update { it.copy(mostrandoAlta = true, alta = AltaUiState()) }
    fun cerrarAlta() = _estado.update { it.copy(mostrandoAlta = false, alta = AltaUiState()) }
    fun cambiarNombre(t: String) = _estado.update { it.copy(alta = it.alta.copy(nombre = t.take(80), error = null)) }
    fun cambiarUsuario(t: String) = _estado.update { it.copy(alta = it.alta.copy(usuario = t.lowercase().trim().take(30), error = null)) }
    fun cambiarCorreo(t: String) = _estado.update { it.copy(alta = it.alta.copy(correo = t.trim().take(120), error = null)) }
    fun cambiarRol(rol: Rol) = _estado.update { it.copy(alta = it.alta.copy(rol = rol, error = null)) }

    fun crearUsuario() {
        val repo = cuentas ?: return
        val a = _estado.value.alta
        if (a.creando) return
        val problema = when {
            a.nombre.trim().length < 5 -> "Escribe el nombre completo."
            else -> ReglasCuenta.problemaUsuario(a.usuario) ?: ReglasCuenta.problemaCorreo(a.correo.lowercase())
        }
        if (problema != null) {
            _estado.update { it.copy(alta = a.copy(error = problema)) }
            return
        }
        _estado.update { it.copy(alta = a.copy(creando = true, error = null)) }
        viewModelScope.launch {
            try {
                val creada = repo.darDeAlta(a.usuario, a.nombre, a.rol, a.correo)
                _estado.update { it.copy(alta = it.alta.copy(creando = false, creada = creada), usuarios = it.usuarios + creada.usuario) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(alta = it.alta.copy(creando = false, error = mensajeDe(e))) }
            }
        }
    }

    // ---- Activar o desactivar ----

    fun alternarActivo(u: Usuario) {
        val repo = cuentas ?: return
        if (_estado.value.cambiandoId != null) return
        if (u.id == miId) {
            _estado.update { it.copy(aviso = "No puedes desactivar tu propia cuenta.") }
            return
        }
        _estado.update { it.copy(cambiandoId = u.id, aviso = null) }
        viewModelScope.launch {
            try {
                val actualizado = repo.cambiarEstado(u.id, activo = !u.activo)
                _estado.update { s ->
                    s.copy(
                        cambiandoId = null,
                        usuarios = s.usuarios.map { if (it.id == u.id) actualizado else it },
                        aviso = if (actualizado.activo) "${u.nombre} puede volver a iniciar sesión." else "${u.nombre} ya no puede iniciar sesión.",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cambiandoId = null, aviso = mensajeDe(e)) }
            }
        }
    }
}
