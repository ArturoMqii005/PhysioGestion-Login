package com.terapid.physiogestion.vista

import com.terapid.physiogestion.viewmodel.ResumenAvisosViewModel
import com.terapid.physiogestion.vista.componentes.TarjetaActivarNotificaciones
import com.terapid.physiogestion.vista.componentes.TarjetaAvisosSinLeer
import com.terapid.physiogestion.vista.componentes.VigilarAvisos
import com.terapid.physiogestion.vista.componentes.notificacionesOVacio
import com.terapid.physiogestion.vista.componentes.resumenOVacio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.terapid.physiogestion.modelo.fechaIsoLegible
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.vista.componentes.AccionCita
import com.terapid.physiogestion.vista.componentes.BotonEnlace
import com.terapid.physiogestion.vista.componentes.DialogoCancelarCita
import com.terapid.physiogestion.vista.componentes.MensajeInfo
import com.terapid.physiogestion.vista.componentes.SelectorDia
import com.terapid.physiogestion.vista.componentes.BotonPrincipal
import com.terapid.physiogestion.vista.componentes.CampoTexto
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.MensajeError
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.tema.ColoresPhysio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.viewmodel.AgendaViewModel
import com.terapid.physiogestion.viewmodel.InvitacionesViewModel
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.EncabezadoRol
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.TarjetaCita
import com.terapid.physiogestion.vista.componentes.TarjetaDato
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta
import com.terapid.physiogestion.vista.componentes.plural

/** VISTA del rol RECEPCIONISTA: agenda de toda la clínica, agrupada por fisioterapeuta. */
@Composable
fun RecepcionPantalla(
    usuario: Usuario,
    viewModel: AgendaViewModel,
    alCerrarSesion: () -> Unit,
    invitaciones: InvitacionesViewModel? = null,
    alCambiarContrasena: (() -> Unit)? = null,
    alAgendar: () -> Unit = {},
    alReprogramar: (Cita) -> Unit = {},
    alAbrirPacientes: () -> Unit = {},
    avisos: ResumenAvisosViewModel? = null,
    alAbrirAvisos: () -> Unit = {},
) {
    val resumenAvisos by avisos.resumenOVacio.collectAsStateWithLifecycle()
    val notificaciones by avisos.notificacionesOVacio.collectAsStateWithLifecycle()
    if (avisos != null) VigilarAvisos(avisos)
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { EncabezadoRol("Agenda de la clínica", usuario, alCerrarSesion, alCambiarContrasena, resumenAvisos.sinLeer, alAbrirAvisos.takeIf { avisos != null }) }
            if (avisos != null) {
                item { TarjetaAvisosSinLeer(resumenAvisos, alAbrirAvisos) }
                item { TarjetaActivarNotificaciones(notificaciones, "Te avisamos cuando un paciente cancele su cita desde la app, para ofrecer esa hora a alguien más.", avisos::activarNotificaciones) }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = alAgendar, modifier = Modifier.weight(1f).heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa)) {
                        Text("Agendar cita", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = alAbrirPacientes, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Pacientes") }
                }
            }
            item {
                SelectorDia(estado.etiquetaDia, { viewModel.cambiarDia(-1) }, { viewModel.cambiarDia(1) }, if (estado.fecha != estado.hoy) viewModel::irAHoy else null)
            }
            estado.aviso?.let { item { MensajeInfo(it) } }

            when {
                estado.cargando -> item { Cargando("Cargando agenda…") }
                estado.error != null -> item { ErrorConReintento(estado.error ?: "", viewModel::cargar) }
                else -> {
                    // Cuántas citas hay en cada estado, dos tarjetas por fila.
                    items(EstadoCita.entries.chunked(2)) { par ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            par.forEach { e ->
                                TarjetaDato(estado.cuantas(e).toString(), e.plural, e.color, Modifier.weight(1f))
                            }
                        }
                    }
                    estado.porFisioterapeuta.forEach { (fisioterapeuta, citas) ->
                        item {
                            Text(
                                "$fisioterapeuta · ${citas.size} citas",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(citas, key = { it.id }) { cita ->
                            val ocupada = estado.procesando == cita.id
                            TarjetaCita(cita, mostrarFisioterapeuta = false) {
                                if (cita.estado == EstadoCita.PENDIENTE) AccionCita("Confirmar", { viewModel.confirmar(cita) }, !ocupada)
                                AccionCita("Reprogramar", { alReprogramar(cita) }, !ocupada)
                                AccionCita("Cancelar", { viewModel.pedirCancelar(cita) }, !ocupada)
                            }
                        }
                    }
                    if (estado.citas.isEmpty()) {
                        item { Text("No hay citas este día.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (invitaciones != null) item { TarjetaInvitarPaciente(invitaciones) }
        }
    }
    estado.cancelando?.let { c ->
        DialogoCancelarCita(c, estado.notaCancelacion, viewModel::cambiarNota, viewModel::confirmarCancelacion, viewModel::cerrarCancelacion)
    }
}

/**
 * Invitar a un paciente nuevo (USR-01): recepción escribe su nombre y la app
 * muestra el código que el paciente usará para crear su cuenta.
 */
@Composable
private fun TarjetaInvitarPaciente(viewModel: InvitacionesViewModel) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    TarjetaPhysio {
        Text("Invitar a un paciente nuevo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Genera un código para que el paciente cree su cuenta en la app. Vence en 7 días y sirve una sola vez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val nueva = e.nueva
        if (nueva != null) {
            TarjetaPhysio(borde = ColoresPhysio.Turquesa) {
                Text("Código para ${nueva.nombrePaciente}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    nueva.codigo,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = ColoresPhysio.Turquesa,
                    modifier = Modifier.semantics { contentDescription = "Código " + nueva.codigo.toList().joinToString(" ") },
                )
                Text(
                    "Vence el ${fechaIsoLegible(nueva.venceEn).substringBefore(' ')}. Díctalo o anótalo en su carnet de citas.",
                    style = MaterialTheme.typography.bodySmall,
                )
                BotonEnlace("Listo", viewModel::ocultarNueva)
            }
        } else {
            CampoTexto(
                e.nombre, viewModel::cambiarNombre, "Nombre completo del paciente",
                habilitado = !e.creando, mayusculas = KeyboardCapitalization.Words, accion = ImeAction.Done, alTerminar = viewModel::crear,
            )
            MensajeError(e.error)
            BotonPrincipal("Generar código", e.creando, viewModel::crear)
        }
        if (e.recientes.isNotEmpty()) {
            Text("Últimas invitaciones", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            e.recientes.forEach { inv ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(inv.nombrePaciente, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(inv.codigo, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Etiqueta(
                        if (inv.usada) "Usada" else "Pendiente",
                        if (inv.usada) ColoresPhysio.Exito else ColoresPhysio.Pendiente,
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
