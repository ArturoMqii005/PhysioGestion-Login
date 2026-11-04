package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.CitaSolicitud
import com.terapid.physiogestion.modelo.Ejercicio
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.FichaPaciente
import com.terapid.physiogestion.modelo.FisioterapeutaResumen
import com.terapid.physiogestion.modelo.PacienteResumen
import com.terapid.physiogestion.modelo.ReprogramarCitaSolicitud
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.claveDelDia
import com.terapid.physiogestion.servicio.BorradorRutina
import com.terapid.physiogestion.servicio.CampoDosis
import com.terapid.physiogestion.servicio.ItemRutina
import com.terapid.physiogestion.servicio.ReglasAgenda
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun hoyIso() = claveDelDia(ahoraEnMilisegundos())

// =========================================================================== AGENDAR O REPROGRAMAR (CIT-05, CIT-06)

data class AgendarCitaUiState(
    val reprogramando: Cita? = null,
    val cargando: Boolean = true,
    val error: String? = null,
    val fisioterapeutas: List<FisioterapeutaResumen> = emptyList(),
    val pacientes: List<PacienteResumen> = emptyList(),
    val nombrePaciente: String = "",
    val pacienteId: Int? = null,
    val fisioterapeutaId: Int? = null,
    val dias: List<String> = emptyList(),
    val fecha: String = "",
    val hoy: String = "",
    val duracion: Int = 50,
    val horasLibres: List<String> = emptyList(),
    val cargandoHoras: Boolean = false,
    val hora: String? = null,
    val motivo: String = "",
    val guardando: Boolean = false,
    /** Cita guardada: la vista regresa a la agenda. */
    val guardada: Cita? = null,
) {
    /** Pacientes que coinciden con lo que se escribe (máximo 5). */
    val sugerencias: List<PacienteResumen>
        get() = if (pacienteId != null || nombrePaciente.trim().length < 2) emptyList()
        else pacientes.filter { it.nombre.contains(nombrePaciente.trim(), ignoreCase = true) }.take(5)
}

class AgendarCitaViewModel(
    private val clinica: ClinicaRepositorio,
    reprogramar: Cita? = null,
    hoy: String = hoyIso(),
) : ViewModel() {
    private val _estado = MutableStateFlow(
        AgendarCitaUiState(
            reprogramando = reprogramar,
            hoy = hoy,
            dias = ReglasAgenda.diasHabiles(hoy, 14),
            fecha = reprogramar?.fecha?.takeIf { it >= hoy } ?: ReglasAgenda.diasHabiles(hoy, 1).first(),
            nombrePaciente = reprogramar?.paciente.orEmpty(),
            pacienteId = reprogramar?.pacienteId,
            fisioterapeutaId = reprogramar?.fisioterapeutaId,
            duracion = reprogramar?.duracionMinutos ?: 50,
            motivo = reprogramar?.motivo.orEmpty(),
        )
    )
    val estado: StateFlow<AgendarCitaUiState> = _estado.asStateFlow()
    private var cargaHoras: Job? = null

    init {
        viewModelScope.launch {
            try {
                val fisios = clinica.fisioterapeutas()
                val pacientes = clinica.pacientes()
                _estado.update { it.copy(cargando = false, fisioterapeutas = fisios, pacientes = pacientes, fisioterapeutaId = it.fisioterapeutaId ?: fisios.firstOrNull()?.id) }
                cargarHoras()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    fun cambiarNombre(t: String) = _estado.update { it.copy(nombrePaciente = t.take(80), pacienteId = null, error = null) }

    fun elegirPaciente(p: PacienteResumen) = _estado.update {
        it.copy(nombrePaciente = p.nombre, pacienteId = p.pacienteId, fisioterapeutaId = p.fisioterapeutaId ?: it.fisioterapeutaId, error = null)
    }.also { cargarHoras() }

    fun elegirFisioterapeuta(id: Int) {
        _estado.update { it.copy(fisioterapeutaId = id, hora = null, error = null) }
        cargarHoras()
    }

    fun elegirFecha(f: String) {
        _estado.update { it.copy(fecha = f, hora = null, error = null) }
        cargarHoras()
    }

    fun elegirDuracion(d: Int) {
        _estado.update { it.copy(duracion = d, hora = null, error = null) }
        cargarHoras()
    }

    fun elegirHora(h: String) = _estado.update { it.copy(hora = h, error = null) }
    fun cambiarMotivo(t: String) = _estado.update { it.copy(motivo = t.take(120), error = null) }

    private fun cargarHoras() {
        val s = _estado.value
        val fisio = s.fisioterapeutaId ?: return
        cargaHoras?.cancel()
        _estado.update { it.copy(cargandoHoras = true) }
        cargaHoras = viewModelScope.launch {
            try {
                val libres = clinica.horasLibres(fisio, s.fecha, s.duracion, s.reprogramando?.id)
                _estado.update { it.copy(cargandoHoras = false, horasLibres = libres, hora = it.hora?.takeIf { h -> h in libres }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargandoHoras = false, horasLibres = emptyList(), error = mensajeDe(e)) }
            }
        }
    }

    fun guardar() {
        val s = _estado.value
        if (s.guardando) return
        val problema = when {
            s.pacienteId == null && s.nombrePaciente.trim().length < 5 -> "Escribe el nombre completo del paciente o elígelo de la lista."
            s.fisioterapeutaId == null -> "Elige al fisioterapeuta."
            s.hora == null -> "Elige una hora libre."
            s.motivo.trim().length < 3 -> "Escribe el motivo de la cita."
            else -> null
        }
        if (problema != null) {
            _estado.update { it.copy(error = problema) }
            return
        }
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            try {
                val cita = s.reprogramando?.let { actual ->
                    clinica.reprogramar(
                        actual.id,
                        ReprogramarCitaSolicitud(s.fecha, s.hora, s.fisioterapeutaId, s.duracion, s.motivo.trim()),
                    )
                } ?: clinica.agendar(
                    CitaSolicitud(s.nombrePaciente.trim(), s.pacienteId, s.fisioterapeutaId!!, s.fecha, s.hora!!, s.duracion, s.motivo.trim())
                )
                _estado.update { it.copy(guardando = false, guardada = cita) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(guardando = false, error = mensajeDe(e)) }
                cargarHoras() // la hora pudo ocuparse mientras se llenaba el formulario
            }
        }
    }
}

// =========================================================================== PACIENTES (PAC-02)

data class PacientesUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val pacientes: List<PacienteResumen> = emptyList(),
    val filtro: String = "",
) {
    val visibles: List<PacienteResumen>
        get() = if (filtro.isBlank()) pacientes else pacientes.filter { it.nombre.contains(filtro.trim(), ignoreCase = true) }
}

class PacientesViewModel(private val clinica: ClinicaRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(PacientesUiState())
    val estado: StateFlow<PacientesUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val lista = clinica.pacientes()
                _estado.update { it.copy(cargando = false, pacientes = lista) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    fun cambiarFiltro(t: String) = _estado.update { it.copy(filtro = t.take(40)) }
}

// =========================================================================== FICHA DEL PACIENTE (PAC-03, PAC-04)

data class FichaPacienteUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val ficha: FichaPaciente? = null,
    val fisioterapeutas: List<FisioterapeutaResumen> = emptyList(),
    val asignando: Boolean = false,
    val aviso: String? = null,
)

class FichaPacienteViewModel(
    private val pacienteId: Int,
    private val clinica: ClinicaRepositorio,
    /** Recepción y administración pueden cambiar al fisioterapeuta responsable. */
    val puedeAsignar: Boolean,
) : ViewModel() {
    private val _estado = MutableStateFlow(FichaPacienteUiState())
    val estado: StateFlow<FichaPacienteUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val ficha = clinica.ficha(pacienteId)
                val fisios = if (puedeAsignar) clinica.fisioterapeutas() else emptyList()
                _estado.update { it.copy(cargando = false, ficha = ficha, fisioterapeutas = fisios) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    fun asignar(fisioterapeutaId: Int) {
        if (_estado.value.asignando || _estado.value.ficha?.resumen?.fisioterapeutaId == fisioterapeutaId) return
        _estado.update { it.copy(asignando = true, aviso = null) }
        viewModelScope.launch {
            try {
                val resumen = clinica.asignar(pacienteId, fisioterapeutaId)
                _estado.update { s -> s.copy(asignando = false, ficha = s.ficha?.copy(resumen = resumen), aviso = "Ahora lo atiende ${resumen.fisioterapeuta}.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(asignando = false, aviso = mensajeDe(e)) }
            }
        }
    }
}

// =========================================================================== EDITAR RUTINA (RUT-08)

data class EditarRutinaUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val paciente: String = "",
    val catalogo: List<Ejercicio> = emptyList(),
    val borrador: BorradorRutina = BorradorRutina(),
    val esNueva: Boolean = true,
    val mostrandoCatalogo: Boolean = false,
    val guardando: Boolean = false,
    val guardada: Boolean = false,
) {
    /** Ejercicios del catálogo que todavía no están en la rutina. */
    val disponibles: List<Ejercicio> get() = catalogo.filter { e -> borrador.items.none { it.ejercicio.id == e.id } }
}

class EditarRutinaViewModel(private val pacienteId: Int, private val clinica: ClinicaRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(EditarRutinaUiState())
    val estado: StateFlow<EditarRutinaUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val catalogo = clinica.catalogo()
                val ficha = clinica.ficha(pacienteId)
                val rutina = ficha.rutina
                val borrador = if (rutina == null) {
                    BorradorRutina(metaFuncional = ficha.resumen.metaFuncional.orEmpty())
                } else {
                    BorradorRutina(rutina.metaFuncional, rutina.umbralDolor, rutina.ejercicios.map(ItemRutina::de))
                }
                _estado.update { it.copy(cargando = false, catalogo = catalogo, borrador = borrador, paciente = ficha.resumen.nombre, esNueva = rutina == null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    private fun editar(cambio: (BorradorRutina) -> BorradorRutina) = _estado.update { it.copy(borrador = cambio(it.borrador), error = null) }

    fun cambiarMeta(t: String) = editar { it.copy(metaFuncional = t.take(120)) }
    fun cambiarUmbral(delta: Int) = editar { it.copy(umbralDolor = (it.umbralDolor + delta).coerceIn(1, 10)) }
    fun ajustar(id: Int, campo: CampoDosis, delta: Int) = editar { it.ajustar(id, campo, delta) }
    fun quitar(id: Int) = editar { it.quitar(id) }
    fun subir(id: Int) = editar { it.subir(id) }
    fun abrirCatalogo() = _estado.update { it.copy(mostrandoCatalogo = true) }
    fun cerrarCatalogo() = _estado.update { it.copy(mostrandoCatalogo = false) }

    fun agregar(e: Ejercicio) {
        editar { it.agregar(e) }
        _estado.update { it.copy(mostrandoCatalogo = false) }
    }

    fun guardar() {
        val s = _estado.value
        if (s.guardando) return
        s.borrador.problema()?.let { msg ->
            _estado.update { it.copy(error = msg) }
            return
        }
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            try {
                clinica.guardarRutina(pacienteId, s.borrador.aSolicitud())
                _estado.update { it.copy(guardando = false, guardada = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(guardando = false, error = mensajeDe(e)) }
            }
        }
    }
}

// =========================================================================== MIS CITAS (paciente, CIT-08)

data class MisCitasUiState(
    val cargando: Boolean = true,
    val citas: List<Cita> = emptyList(),
    val hoy: String = "",
    val cancelando: Cita? = null,
    val procesando: Boolean = false,
    val aviso: String? = null,
) {
    /** Las que siguen en pie (las canceladas y pasadas no se muestran). */
    val proximas: List<Cita> get() = citas.filter { it.abierta }
}

class MisCitasViewModel(
    private val clinica: ClinicaRepositorio,
    hoy: String = hoyIso(),
    /** Recibe las citas cada vez que cambian (programa sus recordatorios, NOT-02). */
    private val alCambiarCitas: (List<Cita>) -> Unit = {},
) : ViewModel() {
    private val _estado = MutableStateFlow(MisCitasUiState(hoy = hoy))
    val estado: StateFlow<MisCitasUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            try {
                val citas = clinica.citas()
                _estado.update { it.copy(cargando = false, citas = citas) }
                alCambiarCitas(citas)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sin conexión simplemente no se muestran; el resto del inicio sigue funcionando.
                _estado.update { it.copy(cargando = false) }
            }
        }
    }

    fun pedirCancelar(c: Cita) = _estado.update { it.copy(cancelando = c) }
    fun cerrarCancelacion() = _estado.update { it.copy(cancelando = null) }

    fun confirmarCancelacion() {
        val cita = _estado.value.cancelando ?: return
        _estado.update { it.copy(cancelando = null, procesando = true) }
        viewModelScope.launch {
            try {
                val nueva = clinica.cambiarEstadoCita(cita.id, EstadoCita.CANCELADA)
                _estado.update { s -> s.copy(procesando = false, citas = s.citas.map { if (it.id == nueva.id) nueva else it }, aviso = "Cancelaste tu cita. Si quieres otra fecha, llama a la clínica.") }
                alCambiarCitas(_estado.value.citas)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(procesando = false, aviso = mensajeDe(e)) }
            }
        }
    }
}
