package com.terapid.physiogestion.vista

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
import com.terapid.physiogestion.vista.componentes.BotonEnlace
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

/** VISTA del rol RECEPCIONISTA: agenda de toda la clínica, agrupada por fisioterapeuta. */
@Composable
fun RecepcionPantalla(
    usuario: Usuario,
    viewModel: AgendaViewModel,
    alCerrarSesion: () -> Unit,
    invitaciones: InvitacionesViewModel? = null,
    alCambiarContrasena: (() -> Unit)? = null,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { EncabezadoRol("Agenda de la clínica", usuario, alCerrarSesion, alCambiarContrasena) }
            if (invitaciones != null) item { TarjetaInvitarPaciente(invitaciones) }

            when {
                estado.cargando -> item { Cargando("Cargando agenda…") }
                estado.error != null -> item { ErrorConReintento(estado.error ?: "", viewModel::cargar) }
                else -> {
                    // Cuántas citas hay en cada estado, dos tarjetas por fila.
                    items(EstadoCita.entries.chunked(2)) { par ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            par.forEach { e ->
                                TarjetaDato(estado.cuantas(e).toString(), e.etiqueta + "s", e.color, Modifier.weight(1f))
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
                        items(citas, key = { it.id }) { TarjetaCita(it, mostrarFisioterapeuta = false) }
                    }
                }
            }
        }
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
