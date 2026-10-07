package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.RutinaRepositorio
import com.terapid.physiogestion.datos.SinRutinaDisponible
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.claveDelDia
import com.terapid.physiogestion.servicio.SeguimientoServicio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * VIEW MODEL: recibe las acciones de la vista, usa el servicio y el
 * repositorio, y publica el resultado en [estado] para que la vista lo pinte.
 * No conoce ninguna pantalla ni ningún botón.
 */
class RutinaViewModel(
    private val repositorio: RutinaRepositorio,
    private val servicio: SeguimientoServicio,
    private val pacienteId: Int,
) : ViewModel() {

    private val _estado = MutableStateFlow(RutinaUiState())
    val estado: StateFlow<RutinaUiState> = _estado.asStateFlow()

    /** Momento en que el paciente marcó su primer ejercicio (hora de inicio, RFN-29). */
    private var inicioSesion: Long? = null

    init {
        cargarRutina()
        actualizarHistorial()
    }

    fun cargarRutina() {
        _estado.update { it.copy(cargando = true, mensajeError = null) }
        viewModelScope.launch {
            try {
                val datos = repositorio.obtenerRutina(pacienteId)
                _estado.update {
                    it.copy(
                        cargando = false,
                        rutina = datos.rutina,
                        origen = datos.origen,
                        actualizadaEn = datos.actualizadaEn,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Mensaje comprensible y con la acción a seguir (RC-19).
                val causa = (e as? SinRutinaDisponible)?.cause as? ErrorApi
                _estado.update {
                    it.copy(
                        cargando = false,
                        mensajeError = causa?.message
                            ?: "No pudimos descargar tu rutina. Revisa tu conexión a Internet y toca «Reintentar».",
                    )
                }
            }
        }
    }

    /** Marca o desmarca un ejercicio como realizado (RFN-26). */
    fun alternarEjercicio(idEjercicio: Int) {
        if (inicioSesion == null) inicioSesion = ahoraEnMilisegundos()
        _estado.update {
            val nuevos = if (idEjercicio in it.completados) it.completados - idEjercicio else it.completados + idEjercicio
            it.copy(completados = nuevos, aviso = null, resultado = null)
        }
    }

    fun cambiarDolorAntes(nivel: Int) {
        if (servicio.esNivelDolorValido(nivel)) _estado.update { it.copy(dolorAntes = nivel, aviso = null) }
    }

    fun cambiarDolorDespues(nivel: Int) {
        if (servicio.esNivelDolorValido(nivel)) _estado.update { it.copy(dolorDespues = nivel, aviso = null) }
    }

    fun cambiarDificultad(dificultad: Dificultad) = _estado.update { it.copy(dificultad = dificultad) }

    fun cambiarObservacion(texto: String) = _estado.update { it.copy(observacion = texto.take(280)) }

    fun cerrarResultado() = _estado.update { it.copy(resultado = null) }

    /**
     * Guarda la sesión. Si ya se está guardando, ignora el toque (evita
     * registros dobles por doble pulsación, RFX-18).
     */
    fun guardarSesion() {
        val actual = _estado.value
        val rutina = actual.rutina ?: return
        if (actual.guardando) return

        val dolorAntes = actual.dolorAntes
        val dolorDespues = actual.dolorDespues
        val aviso = when {
            actual.completados.isEmpty() -> "Marca al menos un ejercicio que hayas realizado."
            dolorAntes == null || dolorDespues == null -> "Indica tu nivel de dolor antes y después de la sesión."
            else -> null
        }
        if (aviso != null || dolorAntes == null || dolorDespues == null) {
            _estado.update { it.copy(aviso = aviso) }
            return
        }

        _estado.update { it.copy(guardando = true, aviso = null) }
        viewModelScope.launch {
            try {
                val fin = ahoraEnMilisegundos()
                val dia = claveDelDia(fin)
                val evaluacion = servicio.evaluarSesion(
                    realizados = actual.completados.size,
                    asignados = rutina.ejercicios.size,
                    dolorAntes = dolorAntes,
                    dolorDespues = dolorDespues,
                    umbral = rutina.umbralDolor,
                    dolorSesionAnterior = repositorio.ultimoDolorRegistrado(),
                    puntosPreviosDelDia = repositorio.puntosDelDia(dia),
                )
                repositorio.registrarSesion(
                    RegistroSesion(
                        rutinaId = rutina.id,
                        inicioMillis = inicioSesion ?: fin,
                        finMillis = fin,
                        claveDia = dia,
                        ejerciciosRealizados = actual.completados.sorted(),
                        ejerciciosAsignados = rutina.ejercicios.size,
                        cumplimiento = evaluacion.cumplimiento,
                        dolorAntes = dolorAntes,
                        dolorDespues = dolorDespues,
                        dificultad = actual.dificultad ?: Dificultad.MEDIA,
                        observacion = actual.observacion.trim(),
                        puntos = evaluacion.puntos,
                        alertaDolor = evaluacion.alerta != null,
                    )
                )
                inicioSesion = null
                _estado.update {
                    it.copy(
                        guardando = false,
                        resultado = evaluacion,
                        completados = emptySet(),
                        dolorAntes = null,
                        dolorDespues = null,
                        dificultad = null,
                        observacion = "",
                    )
                }
                actualizarHistorial()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update {
                    it.copy(guardando = false, aviso = "No pudimos guardar tu sesión en el teléfono. Inténtalo de nuevo.")
                }
            }
        }
    }

    private fun actualizarHistorial() {
        viewModelScope.launch {
            try {
                val sesiones = repositorio.historial()
                val puntos = repositorio.puntosTotales()
                _estado.update { it.copy(historial = sesiones, puntosTotales = puntos) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // El historial es informativo; si falla, la pantalla sigue funcionando.
            }
        }
    }
}
