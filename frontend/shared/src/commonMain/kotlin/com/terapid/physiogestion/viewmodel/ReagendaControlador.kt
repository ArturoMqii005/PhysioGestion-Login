package com.terapid.physiogestion.viewmodel

import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.datos.remoto.DatosInvalidos
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.FisioterapeutaResumen
import com.terapid.physiogestion.modelo.NuevaSolicitudReagenda
import com.terapid.physiogestion.modelo.ReprogramarCitaSolicitud
import com.terapid.physiogestion.servicio.ReglasAgenda
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * REAGENDAR UNA CITA desde la app.
 *
 *  - Paciente, más de 24 h antes y máximo 2 veces: elige otra hora libre y la cita se mueve ([MOVER]).
 *  - Paciente, con menos tiempo o ya la movió 2 veces: manda una solicitud y recepción decide ([SOLICITAR]).
 *  - Fisioterapeuta que no puede atender: la mueve a otra hora ([MOVER]) o se la pasa a otro
 *    fisioterapeuta libre a la misma hora ([PASAR]); el motivo le llega al paciente.
 *
 * El servidor tiene la última palabra: si al mover responde REQUIERE_SOLICITUD, se cambia a solicitud.
 */
enum class ModoReagenda { MOVER, SOLICITAR, PASAR }

data class ReagendaUiState(
    val cita: Cita,
    val esFisioterapeuta: Boolean,
    val modo: ModoReagenda,
    /** Días que se ofrecen (ISO). */
    val dias: List<String>,
    val fecha: String,
    val horas: List<String> = emptyList(),
    val cargandoHoras: Boolean = false,
    val hora: String? = null,
    val fisios: List<FisioterapeutaResumen> = emptyList(),
    val fisio: Int? = null,
    val mensaje: String = "",
    val enviando: Boolean = false,
    val error: String? = null,
    /** Texto final cuando todo salió bien (el diálogo muestra a Xochi contento). */
    val listo: String? = null,
) {
    val puedeEnviar: Boolean
        get() = !enviando && when (modo) {
            ModoReagenda.MOVER -> hora != null && (!esFisioterapeuta || mensaje.trim().length >= 3)
            ModoReagenda.PASAR -> fisio != null && mensaje.trim().length >= 3
            ModoReagenda.SOLICITAR -> mensaje.trim().length in 3..200
        }

    /** Cuántas veces más la puede mover el paciente él mismo. */
    val cambiosRestantes: Int get() = (MAXIMO_REAGENDAS - cita.reagendasPaciente).coerceAtLeast(0)

    companion object {
        const val MAXIMO_REAGENDAS = 2
    }
}

class ReagendaControlador(
    private val clinica: ClinicaRepositorio,
    cita: Cita,
    esFisioterapeuta: Boolean,
    private val hoy: String,
    private val alcance: CoroutineScope,
) {
    private val _estado = MutableStateFlow(
        run {
            // El paciente ve desde mañana; el fisioterapeuta, desde hoy. Dos semanas de opciones.
            val inicio = if (esFisioterapeuta) hoy else ReglasAgenda.sumarDias(hoy, 1)
            val dias = (0 until 14).map { ReglasAgenda.sumarDias(inicio, it) }
            val modo = when {
                esFisioterapeuta -> ModoReagenda.MOVER
                // Hoy mismo ya no hay 24 h; tampoco si ya usó sus 2 cambios.
                cita.fecha <= hoy || cita.reagendasPaciente >= ReagendaUiState.MAXIMO_REAGENDAS -> ModoReagenda.SOLICITAR
                else -> ModoReagenda.MOVER
            }
            ReagendaUiState(cita, esFisioterapeuta, modo, dias, fecha = if (cita.fecha in dias) cita.fecha else dias.first())
        },
    )
    val estado: StateFlow<ReagendaUiState> = _estado.asStateFlow()
    private var pedidoHoras: Job? = null

    init {
        cargarHoras()
    }

    fun cambiarModo(modo: ModoReagenda) {
        _estado.update { it.copy(modo = modo, error = null) }
        if (modo == ModoReagenda.PASAR && _estado.value.fisios.isEmpty()) cargarFisios()
    }

    fun elegirFecha(fecha: String) {
        _estado.update { it.copy(fecha = fecha, hora = null) }
        cargarHoras()
    }

    fun elegirHora(hora: String?) = _estado.update { it.copy(hora = if (it.hora == hora) null else hora) }
    fun elegirFisio(id: Int) = _estado.update { it.copy(fisio = id) }
    fun escribir(texto: String) = _estado.update { it.copy(mensaje = texto.take(200), error = null) }

    private fun cargarHoras() {
        val s = _estado.value
        pedidoHoras?.cancel()
        _estado.update { it.copy(cargandoHoras = true, horas = emptyList()) }
        pedidoHoras = alcance.launch {
            try {
                val horas = clinica.horasLibresDeCita(s.cita.id, s.fecha)
                _estado.update { it.copy(cargandoHoras = false, horas = horas) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargandoHoras = false, error = mensajeDe(e)) }
            }
        }
    }

    private fun cargarFisios() {
        alcance.launch {
            try {
                val fisios = clinica.fisiosDisponibles(_estado.value.cita.id)
                _estado.update { it.copy(fisios = fisios, error = if (fisios.isEmpty()) "Nadie más está libre a esa hora. Mejor muévela a otro horario." else null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(error = mensajeDe(e)) }
            }
        }
    }

    fun enviar(alListo: () -> Unit) {
        val s = _estado.value
        if (!s.puedeEnviar) return
        _estado.update { it.copy(enviando = true, error = null) }
        alcance.launch {
            try {
                val texto = when (s.modo) {
                    ModoReagenda.MOVER -> if (s.esFisioterapeuta) {
                        clinica.reprogramar(s.cita.id, ReprogramarCitaSolicitud(fecha = s.fecha, hora = s.hora, nota = s.mensaje.trim()))
                        "Listo. Movimos la cita de ${s.cita.paciente} y le avisamos."
                    } else {
                        val nueva = clinica.reagendar(s.cita.id, s.fecha, s.hora!!)
                        val dia = ReglasAgenda.etiquetaDia(nueva.fecha, hoy)
                        val cuando = if (dia == "Mañana") "mañana" else "el ${dia.lowercase()}"
                        "¡Listo! Tu cita quedó $cuando a las ${nueva.hora}. Tu fisioterapeuta ya lo sabe."
                    }
                    ModoReagenda.PASAR -> {
                        val nueva = clinica.reprogramar(s.cita.id, ReprogramarCitaSolicitud(fisioterapeutaId = s.fisio, nota = s.mensaje.trim()))
                        "Listo. Ahora la atiende ${nueva.fisioterapeuta}; le avisamos a los dos."
                    }
                    ModoReagenda.SOLICITAR -> {
                        clinica.solicitarCambio(s.cita.id, NuevaSolicitudReagenda(s.hora?.let { s.fecha }, s.hora, s.mensaje.trim()))
                        "Enviamos tu solicitud. Recepción la revisa y te avisa en la app."
                    }
                }
                _estado.update { it.copy(enviando = false, listo = texto) }
                alListo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DatosInvalidos) {
                // Ya no se puede mover directo: se ofrece la solicitud con lo que eligió como propuesta.
                if (e.codigo == "REQUIERE_SOLICITUD") {
                    _estado.update { it.copy(enviando = false, modo = ModoReagenda.SOLICITAR, error = e.message) }
                } else {
                    _estado.update { it.copy(enviando = false, error = e.message) }
                }
            } catch (e: Exception) {
                _estado.update { it.copy(enviando = false, error = mensajeDe(e)) }
            }
        }
    }
}
