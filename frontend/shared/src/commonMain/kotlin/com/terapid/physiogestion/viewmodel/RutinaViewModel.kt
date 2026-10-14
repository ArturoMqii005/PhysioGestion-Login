package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.PreferenciasRepositorio
import com.terapid.physiogestion.datos.RutinaRepositorio
import com.terapid.physiogestion.datos.SensorRepositorio
import com.terapid.physiogestion.datos.SesionesRepositorio
import com.terapid.physiogestion.datos.SinRutinaDisponible
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.EjercicioRealizado
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.plataforma.Entorno
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
 * VIEW MODEL: recibe las acciones de la vista, usa el servicio y los
 * repositorios, y publica el resultado en [estado] para que la vista lo pinte.
 * No conoce ninguna pantalla ni ningún botón.
 *
 * Lo comparte la pantalla de medición con sensores: al terminar un ejercicio
 * medido, avisa con [medicionRegistrada] y aquí se marca como realizado.
 */
class RutinaViewModel(
    private val repositorio: RutinaRepositorio,
    private val servicio: SeguimientoServicio,
    private val pacienteId: Int,
    private val sesiones: SesionesRepositorio? = null,
    private val sensores: SensorRepositorio? = null,
    private val preferencias: PreferenciasRepositorio? = null,
) : ViewModel() {

    private val _estado = MutableStateFlow(RutinaUiState())
    val estado: StateFlow<RutinaUiState> = _estado.asStateFlow()

    /** Momento en que el paciente marcó su primer ejercicio (hora de inicio, RFN-29). */
    private var inicioSesion: Long? = null

    init {
        cargarRutina()
        actualizarResumen()
    }

    fun cargarRutina() {
        _estado.update { it.copy(cargando = true, mensajeError = null) }
        viewModelScope.launch {
            try {
                val datos = repositorio.obtenerRutina(pacienteId)
                val dias = (ahoraEnMilisegundos() - datos.actualizadaEn) / UN_DIA_MS
                _estado.update {
                    it.copy(
                        cargando = false,
                        rutina = datos.rutina,
                        origen = datos.origen,
                        actualizadaEn = datos.actualizadaEn,
                        copiaVencida = datos.origen == OrigenDatos.ALMACENAMIENTO_LOCAL &&
                            dias >= Entorno.VIGENCIA_RUTINA_DIAS,
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
            it.copy(completados = nuevos, aviso = null, resultado = null, envio = null)
        }
    }

    /**
     * La pantalla de sensores terminó de medir un ejercicio. Si se completó,
     * queda marcado como realizado; en cualquier caso se actualizan las mediciones.
     */
    fun medicionRegistrada(idEjercicio: Int, completado: Boolean) {
        if (inicioSesion == null) inicioSesion = ahoraEnMilisegundos()
        if (completado) {
            _estado.update { it.copy(completados = it.completados + idEjercicio, resultado = null, envio = null) }
        }
        actualizarMediciones()
    }

    fun cambiarDolorAntes(nivel: Int) {
        if (servicio.esNivelDolorValido(nivel)) _estado.update { it.copy(dolorAntes = nivel, aviso = null) }
    }

    fun cambiarDolorDespues(nivel: Int) {
        if (servicio.esNivelDolorValido(nivel)) _estado.update { it.copy(dolorDespues = nivel, aviso = null) }
    }

    fun cambiarDificultad(dificultad: Dificultad) = _estado.update { it.copy(dificultad = dificultad) }

    fun cambiarObservacion(texto: String) = _estado.update { it.copy(observacion = texto.take(280)) }

    fun cerrarResultado() = _estado.update { it.copy(resultado = null, envio = null) }

    /**
     * Guarda la sesión: primero en el teléfono y después la envía al servidor
     * (POST /sesiones). Si ya se está guardando, ignora el toque (evita
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
                // Detalle por ejercicio: lo medido con sensores va como verificado.
                val detalle = rutina.ejercicios.filter { it.id in actual.completados }.map { e ->
                    val medido = actual.mediciones[e.id]
                    if (medido != null) {
                        EjercicioRealizado(e.id, medido.repeticiones, medido.anguloMaximo, verificado = true)
                    } else {
                        EjercicioRealizado(e.id, e.repeticionesTotales)
                    }
                }
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
                        detalle = detalle,
                    )
                )
                inicioSesion = null
                val racha = preferencias?.registrarDiaActivo(pacienteId)
                _estado.update {
                    it.copy(
                        guardando = false,
                        resultado = evaluacion,
                        envio = EstadoEnvio.ENVIANDO,
                        completados = emptySet(),
                        dolorAntes = null,
                        dolorDespues = null,
                        dificultad = null,
                        observacion = "",
                        racha = racha ?: it.racha,
                    )
                }
                actualizarResumen()
                enviarAlServidor()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update {
                    it.copy(guardando = false, aviso = "No pudimos guardar tu sesión en el teléfono. Inténtalo de nuevo.")
                }
            }
        }
    }

    /** Vacía la cola de envío: la sesión recién guardada y cualquiera que se haya quedado sin enviar. */
    private suspend fun enviarAlServidor() {
        val repo = sesiones
        if (repo == null) {
            _estado.update { it.copy(envio = EstadoEnvio.PENDIENTE) }
            return
        }
        try {
            val envio = repo.enviarPendientes()
            sensores?.enviarPendientes()
            if (envio.error == null && envio.pendientes == 0) preferencias?.registrarSincronizacion()
            _estado.update {
                it.copy(
                    envio = if (envio.pendientes == 0) EstadoEnvio.ENVIADA else EstadoEnvio.PENDIENTE,
                    detalleTecnico = envio.detalle ?: it.detalleTecnico,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sesión vencida u otro problema: la sesión ya quedó guardada en el teléfono.
            _estado.update { it.copy(envio = EstadoEnvio.PENDIENTE) }
        }
    }

    private fun actualizarResumen() {
        viewModelScope.launch {
            try {
                val puntos = repositorio.puntosTotales()
                val racha = preferencias?.racha(pacienteId) ?: 0
                _estado.update { it.copy(puntosTotales = puntos, racha = racha) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // El resumen es informativo; si falla, la pantalla sigue funcionando.
            }
        }
        actualizarMediciones()
    }

    private fun actualizarMediciones() {
        val repo = sensores ?: return
        viewModelScope.launch {
            try {
                val mediciones = repo.medicionesDeHoy()
                _estado.update { it.copy(mediciones = mediciones) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val UN_DIA_MS = 24L * 60 * 60 * 1000
    }
}
