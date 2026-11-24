package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.modelo.Anuncio
import com.terapid.physiogestion.modelo.AnuncioSolicitud
import com.terapid.physiogestion.modelo.Destinatarios
import com.terapid.physiogestion.modelo.FisioterapeutaResumen
import com.terapid.physiogestion.modelo.ResolverSolicitud
import com.terapid.physiogestion.modelo.SolicitudReagenda
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.claveDelDia
import com.terapid.physiogestion.servicio.ReglasAgenda
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// =========================================================================== SOLICITUDES DE CAMBIO DE CITA

/** Datos del diálogo «Aprobar y mover»: día, hora libre, fisioterapeuta y mensaje para el paciente. */
data class AprobacionUiState(
    val solicitud: SolicitudReagenda,
    /** Días que se ofrecen (ISO, sin domingos). */
    val dias: List<String>,
    val fecha: String,
    val fisioterapeutaId: Int,
    val fisioterapeutas: List<FisioterapeutaResumen> = emptyList(),
    val horas: List<String> = emptyList(),
    val cargandoHoras: Boolean = false,
    val hora: String? = null,
    val nota: String = "",
) {
    /** La hora que propuso el paciente ya no está libre ese día. */
    val propuestaOcupada: Boolean
        get() = !cargandoHoras && solicitud.horaPropuesta != null && fecha == solicitud.fechaPropuesta &&
            solicitud.horaPropuesta !in horas
}

data class SolicitudesUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val soloPendientes: Boolean = true,
    val solicitudes: List<SolicitudReagenda> = emptyList(),
    val aviso: String? = null,
    val aprobando: AprobacionUiState? = null,
    val rechazando: SolicitudReagenda? = null,
    val motivoRechazo: String = "",
    val enviando: Boolean = false,
    val errorDialogo: String? = null,
) {
    val puedeAprobar: Boolean get() = !enviando && aprobando?.hora != null
    val puedeRechazar: Boolean get() = !enviando && motivoRechazo.trim().length >= 3
}

/**
 * VIEW MODEL de las solicitudes de cambio de cita (recepción y administración).
 * El paciente las manda cuando faltan menos de 24 horas o ya movió su cita dos
 * veces. Aprobar mueve la cita a la hora elegida; rechazar le explica el motivo.
 * En los dos casos el servidor le manda un aviso al paciente.
 */
class SolicitudesViewModel(
    private val clinica: ClinicaRepositorio,
    private val hoy: String = claveDelDia(ahoraEnMilisegundos()),
) : ViewModel() {

    private val _estado = MutableStateFlow(SolicitudesUiState())
    val estado: StateFlow<SolicitudesUiState> = _estado.asStateFlow()
    private var pedidoHoras: Job? = null

    init {
        cargar()
    }

    fun cargar() {
        val soloPendientes = _estado.value.soloPendientes
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val lista = clinica.solicitudes(soloPendientes)
                _estado.update { if (it.soloPendientes == soloPendientes) it.copy(cargando = false, solicitudes = lista) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    fun filtrar(soloPendientes: Boolean) {
        if (_estado.value.soloPendientes == soloPendientes) return
        _estado.update { it.copy(soloPendientes = soloPendientes, solicitudes = emptyList(), aviso = null) }
        cargar()
    }

    // ---------------------------------------------------------------- Aprobar y mover

    fun abrirAprobar(s: SolicitudReagenda) {
        val dias = ReglasAgenda.diasHabiles(hoy, 20)
        val deseada = s.fechaPropuesta ?: s.fechaActual
        val fecha = if (deseada in dias) deseada else dias.first()
        _estado.update {
            it.copy(
                aprobando = AprobacionUiState(s, dias, fecha, s.fisioterapeutaId),
                rechazando = null,
                errorDialogo = null,
                aviso = null,
            )
        }
        cargarHoras()
        viewModelScope.launch {
            try {
                val fisios = clinica.fisioterapeutas()
                _estado.update { e -> e.copy(aprobando = e.aprobando?.copy(fisioterapeutas = fisios)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sin la lista se puede aprobar igual con el mismo fisioterapeuta.
            }
        }
    }

    fun elegirFecha(fecha: String) {
        _estado.update { e -> e.copy(aprobando = e.aprobando?.copy(fecha = fecha, hora = null)) }
        cargarHoras()
    }

    fun elegirFisioterapeuta(id: Int) {
        _estado.update { e -> e.copy(aprobando = e.aprobando?.copy(fisioterapeutaId = id, hora = null)) }
        cargarHoras()
    }

    fun elegirHora(hora: String) = _estado.update { e ->
        e.copy(aprobando = e.aprobando?.let { a -> a.copy(hora = if (a.hora == hora) null else hora) })
    }

    fun escribirNota(texto: String) = _estado.update { e -> e.copy(aprobando = e.aprobando?.copy(nota = texto.take(200))) }

    private fun cargarHoras() {
        val a = _estado.value.aprobando ?: return
        pedidoHoras?.cancel()
        _estado.update { e -> e.copy(aprobando = e.aprobando?.copy(cargandoHoras = true, horas = emptyList()), errorDialogo = null) }
        pedidoHoras = viewModelScope.launch {
            try {
                val horas = clinica.horasLibresDeCita(a.solicitud.citaId, a.fecha, a.fisioterapeutaId)
                _estado.update { e ->
                    val actual = e.aprobando ?: return@update e
                    // Si la hora que pidió el paciente está libre, ya queda elegida.
                    val propuesta = a.solicitud.horaPropuesta?.takeIf { a.fecha == a.solicitud.fechaPropuesta && it in horas }
                    e.copy(aprobando = actual.copy(cargandoHoras = false, horas = horas, hora = actual.hora ?: propuesta))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { s -> s.copy(aprobando = s.aprobando?.copy(cargandoHoras = false), errorDialogo = mensajeDe(e)) }
            }
        }
    }

    fun aprobar() {
        val s = _estado.value
        val a = s.aprobando ?: return
        val hora = a.hora ?: return
        if (s.enviando) return
        _estado.update { it.copy(enviando = true, errorDialogo = null) }
        viewModelScope.launch {
            try {
                clinica.resolverSolicitud(
                    a.solicitud.id,
                    ResolverSolicitud(
                        aprobar = true,
                        fecha = a.fecha,
                        hora = hora,
                        fisioterapeutaId = a.fisioterapeutaId,
                        nota = a.nota.trim().ifEmpty { null },
                    ),
                )
                _estado.update {
                    it.copy(
                        enviando = false,
                        aprobando = null,
                        aviso = "Solicitud aprobada: la cita de ${a.solicitud.paciente} quedó para " +
                            "${ReglasAgenda.etiquetaDia(a.fecha, hoy).lowercase()} a las $hora. Le avisamos al paciente.",
                    )
                }
                cargar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(enviando = false, errorDialogo = mensajeDe(e)) }
            }
        }
    }

    // ---------------------------------------------------------------- Rechazar

    fun abrirRechazar(s: SolicitudReagenda) =
        _estado.update { it.copy(rechazando = s, aprobando = null, motivoRechazo = "", errorDialogo = null, aviso = null) }

    fun escribirMotivo(texto: String) = _estado.update { it.copy(motivoRechazo = texto.take(200), errorDialogo = null) }

    fun rechazar() {
        val s = _estado.value
        val solicitud = s.rechazando ?: return
        if (!s.puedeRechazar) return
        _estado.update { it.copy(enviando = true, errorDialogo = null) }
        viewModelScope.launch {
            try {
                clinica.resolverSolicitud(solicitud.id, ResolverSolicitud(aprobar = false, nota = s.motivoRechazo.trim()))
                _estado.update {
                    it.copy(enviando = false, rechazando = null, aviso = "Solicitud rechazada. Le explicamos el motivo a ${solicitud.paciente}.")
                }
                cargar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(enviando = false, errorDialogo = mensajeDe(e)) }
            }
        }
    }

    fun cerrarDialogo() {
        if (_estado.value.enviando) return
        pedidoHoras?.cancel()
        _estado.update { it.copy(aprobando = null, rechazando = null, errorDialogo = null) }
    }
}

// =========================================================================== ANUNCIOS DE LA CLÍNICA

/** El servidor acepta hasta 3 anuncios en 24 horas para que no se vuelvan ruido. */
const val MAXIMO_ANUNCIOS_DIA = 3
private const val UN_DIA_MS = 24L * 60 * 60 * 1000

data class AnunciosUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val anuncios: List<Anuncio> = emptyList(),
    /** Momento de la última carga, para contar los de las últimas 24 horas. */
    val ahora: Long = 0,
    val aviso: String? = null,
    val formularioAbierto: Boolean = false,
    val destinatarios: Destinatarios = Destinatarios.PACIENTES,
    val titulo: String = "",
    val texto: String = "",
    val publicando: Boolean = false,
    val errorFormulario: String? = null,
) {
    val publicadosHoy: Int get() = anuncios.count { it.creado > ahora - UN_DIA_MS }
    val puedePublicar: Boolean get() = !cargando && publicadosHoy < MAXIMO_ANUNCIOS_DIA
}

/**
 * VIEW MODEL de los anuncios (recepción y administración): «Cerramos el lunes
 * festivo», «Junta del personal a las 8:00». Llegan como notificación push y
 * quedan en la bandeja de avisos de cada persona.
 */
class AnunciosViewModel(
    private val clinica: ClinicaRepositorio,
    private val reloj: () -> Long = ::ahoraEnMilisegundos,
) : ViewModel() {

    private val _estado = MutableStateFlow(AnunciosUiState())
    val estado: StateFlow<AnunciosUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val lista = clinica.anuncios()
                _estado.update { it.copy(cargando = false, anuncios = lista.sortedByDescending { a -> a.creado }, ahora = reloj()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    fun abrirFormulario() = _estado.update {
        it.copy(formularioAbierto = true, titulo = "", texto = "", destinatarios = Destinatarios.PACIENTES, errorFormulario = null, aviso = null)
    }

    fun cerrarFormulario() {
        if (!_estado.value.publicando) _estado.update { it.copy(formularioAbierto = false) }
    }

    fun elegirDestinatarios(d: Destinatarios) = _estado.update { it.copy(destinatarios = d) }
    fun escribirTitulo(t: String) = _estado.update { it.copy(titulo = t.take(60), errorFormulario = null) }
    fun escribirTexto(t: String) = _estado.update { it.copy(texto = t.take(300), errorFormulario = null) }

    fun publicar() {
        val s = _estado.value
        if (s.publicando) return
        val problema = when {
            s.titulo.trim().length < 3 -> "El título debe tener al menos 3 caracteres."
            s.texto.trim().length < 5 -> "Escribe el mensaje del anuncio."
            else -> null
        }
        if (problema != null) {
            _estado.update { it.copy(errorFormulario = problema) }
            return
        }
        _estado.update { it.copy(publicando = true, errorFormulario = null) }
        viewModelScope.launch {
            try {
                val nuevo = clinica.publicarAnuncio(AnuncioSolicitud(s.titulo.trim(), s.texto.trim(), s.destinatarios))
                _estado.update {
                    it.copy(
                        publicando = false,
                        formularioAbierto = false,
                        anuncios = listOf(nuevo) + it.anuncios.filterNot { a -> a.id == nuevo.id },
                        ahora = reloj(),
                        aviso = "Anuncio publicado: les llegó a ${nuevo.personas} ${if (nuevo.personas == 1) "persona" else "personas"}.",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(publicando = false, errorFormulario = mensajeDe(e)) }
            }
        }
    }
}
