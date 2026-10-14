package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.RutinaRepositorio
import com.terapid.physiogestion.datos.SesionesRepositorio
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.ModificarSesionSolicitud
import com.terapid.physiogestion.modelo.ReemplazarSesionSolicitud
import com.terapid.physiogestion.modelo.SesionTerapia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetalleSesionUiState(
    val cargando: Boolean = true,
    val sesion: SesionTerapia? = null,
    val error: String? = null,
    /** Nota que se corrige con PATCH. */
    val nota: String = "",
    /** Formulario de edición completa (PUT). */
    val editando: Boolean = false,
    val formDolorAntes: Int = 0,
    val formDolorDespues: Int = 0,
    val formDificultad: Dificultad = Dificultad.MEDIA,
    val formObservacion: String = "",
    val confirmandoEliminar: Boolean = false,
    val guardando: Boolean = false,
    val aviso: String? = null,
    val eliminada: Boolean = false,
    val detalleTecnico: String? = null,
    /** Nombres de los ejercicios de la rutina (la sesión solo trae sus ids). */
    val nombres: Map<Int, String> = emptyMap(),
) {
    fun nombreEjercicio(id: Int): String = nombres[id] ?: "Ejercicio $id"
}

/**
 * VIEW MODEL del detalle de una sesión: aquí se usan GET (ver), PATCH
 * (corregir solo la nota), PUT (reemplazar todos los datos editables) y
 * DELETE (eliminar una sesión registrada por error).
 */
class DetalleSesionViewModel(
    private val idSesion: Int,
    private val repositorio: SesionesRepositorio,
    private val rutinas: RutinaRepositorio? = null,
    private val pacienteId: Int = 0,
) : ViewModel() {

    private val _estado = MutableStateFlow(DetalleSesionUiState())
    val estado: StateFlow<DetalleSesionUiState> = _estado.asStateFlow()

    init {
        cargar()
        cargarNombres()
    }

    /** Los nombres salen de la rutina (del servidor o de la copia del teléfono). */
    private fun cargarNombres() {
        val repo = rutinas ?: return
        viewModelScope.launch {
            try {
                val rutina = repo.obtenerRutina(pacienteId).rutina
                _estado.update { it.copy(nombres = rutina.ejercicios.associate { e -> e.id to e.nombre }) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** GET /sesiones/{id} */
    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        operar(alFallar = { mensaje -> _estado.update { it.copy(cargando = false, error = mensaje) } }) {
            val respuesta = repositorio.obtener(idSesion)
            _estado.update {
                it.copy(cargando = false, sesion = respuesta.datos, nota = respuesta.datos.observacion.orEmpty(), detalleTecnico = respuesta.detalle)
            }
        }
    }

    fun cambiarNota(texto: String) = _estado.update { it.copy(nota = texto.take(280), aviso = null) }

    /** PATCH /sesiones/{id} con {"observacion": "..."}: el resto de la sesión no se toca. */
    fun guardarNota() {
        val nota = _estado.value.nota.trim()
        if (nota.isEmpty()) {
            _estado.update { it.copy(aviso = "Escribe la nota que quieres guardar.") }
            return
        }
        guardar {
            val respuesta = repositorio.modificar(idSesion, ModificarSesionSolicitud(observacion = nota))
            _estado.update {
                it.copy(sesion = respuesta.datos, aviso = "Nota actualizada.", detalleTecnico = respuesta.detalle)
            }
        }
    }

    fun abrirEdicion() {
        val s = _estado.value.sesion ?: return
        _estado.update {
            it.copy(
                editando = true,
                formDolorAntes = s.dolorAntes,
                formDolorDespues = s.dolorDespues,
                formDificultad = s.dificultad,
                formObservacion = s.observacion.orEmpty(),
                aviso = null,
            )
        }
    }

    fun cerrarEdicion() = _estado.update { it.copy(editando = false) }

    fun cambiarDolorAntes(nivel: Int) = _estado.update { it.copy(formDolorAntes = nivel.coerceIn(0, 10)) }

    fun cambiarDolorDespues(nivel: Int) = _estado.update { it.copy(formDolorDespues = nivel.coerceIn(0, 10)) }

    fun cambiarDificultad(d: Dificultad) = _estado.update { it.copy(formDificultad = d) }

    fun cambiarObservacion(texto: String) = _estado.update { it.copy(formObservacion = texto.take(280)) }

    /** PUT /sesiones/{id}: se manda el recurso completo; si la observación queda vacía, se borra. */
    fun reemplazar() {
        val e = _estado.value
        guardar {
            val respuesta = repositorio.reemplazar(
                idSesion,
                ReemplazarSesionSolicitud(
                    dolorAntes = e.formDolorAntes,
                    dolorDespues = e.formDolorDespues,
                    dificultad = e.formDificultad,
                    observacion = e.formObservacion.trim().ifBlank { null },
                ),
            )
            _estado.update {
                it.copy(
                    sesion = respuesta.datos,
                    nota = respuesta.datos.observacion.orEmpty(),
                    editando = false,
                    aviso = "Sesión actualizada.",
                    detalleTecnico = respuesta.detalle,
                )
            }
        }
    }

    fun pedirConfirmacionEliminar() = _estado.update { it.copy(confirmandoEliminar = true) }

    fun cancelarEliminar() = _estado.update { it.copy(confirmandoEliminar = false) }

    /** DELETE /sesiones/{id} -> 204 No Content. Al terminar, la pantalla regresa al historial. */
    fun eliminar() {
        _estado.update { it.copy(confirmandoEliminar = false) }
        guardar {
            val respuesta = repositorio.eliminar(idSesion)
            _estado.update { it.copy(eliminada = true, detalleTecnico = respuesta.detalle) }
        }
    }

    private fun guardar(bloque: suspend () -> Unit) {
        if (_estado.value.guardando) return
        _estado.update { it.copy(guardando = true, aviso = null) }
        operar(alFallar = { mensaje -> _estado.update { it.copy(guardando = false, aviso = mensaje) } }) {
            bloque()
            _estado.update { it.copy(guardando = false) }
        }
    }

    private fun operar(alFallar: (String) -> Unit, bloque: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                bloque()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                alFallar((e as? ErrorApi)?.message ?: "Ocurrió un problema. Inténtalo de nuevo.")
            }
        }
    }
}
