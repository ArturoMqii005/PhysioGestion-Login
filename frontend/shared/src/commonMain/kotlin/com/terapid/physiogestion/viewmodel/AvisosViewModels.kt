package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.AvisosRepositorio
import com.terapid.physiogestion.datos.ResumenAvisos
import com.terapid.physiogestion.modelo.Aviso
import com.terapid.physiogestion.plataforma.Notificador
import com.terapid.physiogestion.plataforma.permisos.GestorPermisos
import com.terapid.physiogestion.plataforma.permisos.Permiso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cada cuánto revisa la app sus avisos mientras la vista del rol está en pantalla. */
const val REVISION_EN_PANTALLA_MS = 30_000L

/**
 * Permiso de notificaciones compartido por la bandeja y las vistas de cada rol.
 * [pedirlo] es true donde hay notificaciones del sistema (Android) y el usuario
 * todavía no las permite.
 */
data class EstadoNotificaciones(val pedirlo: Boolean = false, val mensaje: String? = null)

private fun estadoNotificaciones(n: Notificador) = EstadoNotificaciones(pedirlo = n.disponible && !n.permitidas())

private suspend fun activar(permisos: GestorPermisos, notificador: Notificador): EstadoNotificaciones {
    permisos.solicitar(Permiso.NOTIFICACIONES)
    return if (notificador.permitidas()) {
        EstadoNotificaciones(pedirlo = false, mensaje = "Listo: te avisaremos aunque la app esté cerrada.")
    } else {
        EstadoNotificaciones(pedirlo = true, mensaje = "Las notificaciones siguen apagadas. Puedes activarlas en Ajustes > Apps > PhysioGestion.")
    }
}

// =========================================================================== RESUMEN EN LA VISTA DEL ROL

/**
 * VIEW MODEL del contador de avisos en la vista de cada rol. La vista llama a
 * [refrescar] cada [REVISION_EN_PANTALLA_MS] mientras está visible; si llegó
 * algo nuevo, también sale la notificación del sistema.
 */
class ResumenAvisosViewModel(
    private val repositorio: AvisosRepositorio,
    private val notificador: () -> Notificador,
    private val permisos: GestorPermisos,
) : ViewModel() {
    val resumen: StateFlow<ResumenAvisos> = repositorio.resumen

    private val _notificaciones = MutableStateFlow(estadoNotificaciones(notificador()))
    val notificaciones: StateFlow<EstadoNotificaciones> = _notificaciones.asStateFlow()

    /** Pide los avisos al servidor. Sin conexión no hace nada: la siguiente vuelta lo intenta otra vez. */
    suspend fun refrescar() {
        try {
            notificador().mostrarAvisos(repositorio.revisar())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        _notificaciones.update { estadoNotificaciones(notificador()).copy(mensaje = it.mensaje) }
    }

    fun activarNotificaciones() {
        viewModelScope.launch { _notificaciones.value = activar(permisos, notificador()) }
    }
}

// =========================================================================== BANDEJA

data class AvisosUiState(
    val cargando: Boolean = true,
    val avisos: List<Aviso> = emptyList(),
    val sinLeer: Int = 0,
    val error: String? = null,
    val procesando: Boolean = false,
    val notificaciones: EstadoNotificaciones = EstadoNotificaciones(),
)

/** VIEW MODEL de la bandeja de avisos. Tocar un aviso lo marca como leído. */
class AvisosViewModel(
    private val repositorio: AvisosRepositorio,
    private val notificador: () -> Notificador,
    private val permisos: GestorPermisos,
) : ViewModel() {
    private val _estado = MutableStateFlow(AvisosUiState(notificaciones = estadoNotificaciones(notificador())))
    val estado: StateFlow<AvisosUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = it.avisos.isEmpty(), error = null) }
        viewModelScope.launch {
            try {
                val b = repositorio.bandeja()
                _estado.update { it.copy(cargando = false, avisos = b.avisos, sinLeer = b.sinLeer) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    /** Lo marca como leído en el servidor; la navegación (abrir la ficha, la rutina…) la decide la vista. */
    fun abrir(aviso: Aviso) {
        if (aviso.leido) return
        _estado.update { s ->
            s.copy(avisos = s.avisos.map { if (it.id == aviso.id) it.copy(leido = true) else it }, sinLeer = (s.sinLeer - 1).coerceAtLeast(0))
        }
        viewModelScope.launch {
            try {
                repositorio.marcarLeidos(listOf(aviso.id))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Si no se pudo marcar, la próxima carga lo vuelve a mostrar como nuevo.
            }
        }
    }

    fun marcarTodos() {
        _estado.update { it.copy(procesando = true) }
        viewModelScope.launch {
            try {
                val b = repositorio.marcarTodos()
                _estado.update { it.copy(procesando = false, avisos = b.avisos, sinLeer = b.sinLeer) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(procesando = false, error = mensajeDe(e)) }
            }
        }
    }

    fun activarNotificaciones() {
        viewModelScope.launch { _estado.update { it.copy(notificaciones = activar(permisos, notificador())) } }
    }
}
