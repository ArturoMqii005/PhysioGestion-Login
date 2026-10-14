package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.SesionesRepositorio
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.modelo.SesionTerapia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistorialUiState(
    val cargando: Boolean = true,
    val origen: OrigenDatos? = null,
    /** Sesiones del servidor (GET /sesiones). */
    val sesiones: List<SesionTerapia> = emptyList(),
    val total: Int = 0,
    /** Copia del teléfono, cuando no hay conexión. */
    val locales: List<RegistroSesion> = emptyList(),
    val pendientes: Int = 0,
    val enviando: Boolean = false,
    val cargandoMas: Boolean = false,
    val error: String? = null,
    val aviso: String? = null,
    val detalleTecnico: String? = null,
) {
    val hayMas: Boolean get() = origen == OrigenDatos.SERVIDOR && sesiones.size < total
}

/**
 * VIEW MODEL del historial (RFN-31). Pide la lista al servidor con GET; si
 * no hay conexión, muestra lo que está guardado en el teléfono.
 */
class HistorialViewModel(private val repositorio: SesionesRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(HistorialUiState())
    val estado: StateFlow<HistorialUiState> = _estado.asStateFlow()

    /** Se llama cada vez que la pantalla aparece (por ejemplo, al regresar del detalle). */
    fun cargar() {
        _estado.update { it.copy(cargando = it.sesiones.isEmpty() && it.locales.isEmpty(), error = null) }
        viewModelScope.launch {
            val pendientes = contarPendientes()
            try {
                val respuesta = repositorio.historial(pagina = 0, tamano = TAMANO_PAGINA)
                _estado.update {
                    it.copy(
                        cargando = false,
                        origen = OrigenDatos.SERVIDOR,
                        sesiones = respuesta.datos.sesiones,
                        total = respuesta.datos.total,
                        pendientes = pendientes,
                        detalleTecnico = respuesta.detalle,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErrorApi) {
                // Si fue SesionVencida, el repositorio ya cerró la sesión y la app regresa al login.
                val locales = try {
                    repositorio.historialLocal()
                } catch (_: Exception) {
                    emptyList()
                }
                _estado.update {
                    it.copy(
                        cargando = false,
                        origen = OrigenDatos.ALMACENAMIENTO_LOCAL,
                        locales = locales,
                        pendientes = pendientes,
                        error = e.message,
                    )
                }
            }
        }
    }

    /** Siguiente página del historial (parámetro pagina de GET /sesiones). */
    fun cargarMas() {
        val actual = _estado.value
        if (!actual.hayMas || actual.cargandoMas) return
        _estado.update { it.copy(cargandoMas = true) }
        viewModelScope.launch {
            try {
                val pagina = actual.sesiones.size / TAMANO_PAGINA
                val respuesta = repositorio.historial(pagina = pagina, tamano = TAMANO_PAGINA)
                _estado.update {
                    it.copy(
                        cargandoMas = false,
                        sesiones = (it.sesiones + respuesta.datos.sesiones).distinctBy { s -> s.id },
                        total = respuesta.datos.total,
                        detalleTecnico = respuesta.detalle,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargandoMas = false, aviso = (e as? ErrorApi)?.message) }
            }
        }
    }

    /** Envía las sesiones que se guardaron sin conexión (POST /sesiones con Idempotency-Key). */
    fun enviarPendientes() {
        if (_estado.value.enviando) return
        _estado.update { it.copy(enviando = true, aviso = null) }
        viewModelScope.launch {
            try {
                val resultado = repositorio.enviarPendientes()
                val aviso = when {
                    resultado.error != null -> resultado.error.message
                    resultado.enviadas == 1 -> "Se envió 1 sesión a tu fisioterapeuta."
                    resultado.enviadas > 1 -> "Se enviaron ${resultado.enviadas} sesiones a tu fisioterapeuta."
                    else -> "No había sesiones por enviar."
                }
                _estado.update {
                    it.copy(enviando = false, aviso = aviso, detalleTecnico = resultado.detalle ?: it.detalleTecnico)
                }
                cargar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(enviando = false, aviso = (e as? ErrorApi)?.message) }
            }
        }
    }

    private suspend fun contarPendientes(): Int = try {
        repositorio.pendientesDeEnvio()
    } catch (_: Exception) {
        0
    }

    private companion object {
        const val TAMANO_PAGINA = 20
    }
}
