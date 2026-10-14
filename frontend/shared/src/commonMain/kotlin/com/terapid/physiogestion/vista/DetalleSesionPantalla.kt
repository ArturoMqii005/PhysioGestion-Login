package com.terapid.physiogestion.vista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.SesionTerapia
import com.terapid.physiogestion.modelo.fechaIsoLegible
import com.terapid.physiogestion.viewmodel.DetalleSesionUiState
import com.terapid.physiogestion.viewmodel.DetalleSesionViewModel
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.DetalleTecnico
import com.terapid.physiogestion.vista.componentes.EncabezadoSecundario
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.SelectorDificultad
import com.terapid.physiogestion.vista.componentes.SelectorDolor
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * VISTA del detalle de una sesión: consultar (GET), corregir la nota (PATCH),
 * editar todos los datos (PUT) o eliminarla si se registró por error (DELETE).
 */
@Composable
fun DetalleSesionPantalla(
    viewModel: DetalleSesionViewModel,
    alRegresar: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.eliminada) { if (estado.eliminada) alRegresar() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val sesion = estado.sesion
            EncabezadoSecundario(
                titulo = "Sesión del ${sesion?.let { fechaIsoLegible(it.fechaHoraInicio).substringBefore(' ') } ?: "…"}",
                alRegresar = alRegresar,
            )
            when {
                estado.cargando -> Cargando("Cargando la sesión…")
                sesion == null -> ErrorConReintento(estado.error ?: "No se encontró la sesión.", viewModel::cargar)
                else -> {
                    DatosSesion(sesion, estado::nombreEjercicio)
                    if (estado.editando) FormularioEdicion(estado, viewModel) else Acciones(estado, viewModel)
                }
            }
            estado.aviso?.let { Text(it, color = ColoresPhysio.Turquesa, style = MaterialTheme.typography.bodyMedium) }
            DetalleTecnico(estado.detalleTecnico)
        }
    }

    if (estado.confirmandoEliminar) {
        AlertDialog(
            onDismissRequest = viewModel::cancelarEliminar,
            title = { Text("¿Eliminar esta sesión?") },
            text = { Text("Úsalo solo si la registraste por error. Tu fisioterapeuta ya no la verá.") },
            confirmButton = {
                TextButton(onClick = viewModel::eliminar) { Text("Eliminar", color = ColoresPhysio.Alerta) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelarEliminar) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DatosSesion(sesion: SesionTerapia, nombreEjercicio: (Int) -> String) {
    TarjetaPhysio(borde = sesion.semaforo.color) {
        Etiqueta("Semáforo: ${sesion.semaforo.etiqueta}", sesion.semaforo.color)
        Text(
            "${fechaIsoLegible(sesion.fechaHoraInicio)} – ${fechaIsoLegible(sesion.fechaHoraFin).substringAfter(' ')}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text("Cumplimiento: ${sesion.porcentajeCumplimiento} %")
        Text("Dolor: ${sesion.dolorAntes} antes → ${sesion.dolorDespues} después · ${sesion.dificultad.etiqueta}")
        Text("Nota: ${sesion.observacion ?: "sin nota"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    TarjetaPhysio {
        Text("Ejercicios realizados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        sesion.ejercicios.forEach { e ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(nombreEjercicio(e.idEjercicio), modifier = Modifier.weight(1f))
                Text(
                    "${e.repeticionesRealizadas} reps" + (e.anguloMaximo?.let { " · $it°" } ?: ""),
                    color = if (e.verificado) ColoresPhysio.AzulClaro else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "En azul: medido con los sensores del teléfono.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Acciones(estado: DetalleSesionUiState, viewModel: DetalleSesionViewModel) {
    TarjetaPhysio {
        Text("Corregir la nota", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Solo cambia la nota; el resto de la sesión se queda igual.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = estado.nota,
            onValueChange = viewModel::cambiarNota,
            label = { Text("Nota para tu fisioterapeuta") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Button(
            onClick = viewModel::guardarNota,
            enabled = !estado.guardando,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
        ) {
            Text("Guardar nota")
        }
    }
    OutlinedButton(onClick = viewModel::abrirEdicion, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Editar todos los datos de la sesión")
    }
    OutlinedButton(
        onClick = viewModel::pedirConfirmacionEliminar,
        enabled = !estado.guardando,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text("Eliminar sesión registrada por error", color = ColoresPhysio.Alerta)
    }
}

@Composable
private fun FormularioEdicion(estado: DetalleSesionUiState, viewModel: DetalleSesionViewModel) {
    TarjetaPhysio(borde = ColoresPhysio.Turquesa) {
        Text("Editar la sesión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Se reemplazan todos estos datos. Si dejas la nota vacía, se borra.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectorDolor("Dolor antes de la sesión", estado.formDolorAntes, viewModel::cambiarDolorAntes)
        SelectorDolor("Dolor después de la sesión", estado.formDolorDespues, viewModel::cambiarDolorDespues)
        SelectorDificultad(estado.formDificultad, viewModel::cambiarDificultad)
        OutlinedTextField(
            value = estado.formObservacion,
            onValueChange = viewModel::cambiarObservacion,
            label = { Text("Nota (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::cerrarEdicion, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text("Cancelar")
            }
            Button(
                onClick = viewModel::reemplazar,
                enabled = !estado.guardando,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
            ) {
                Text("Guardar cambios")
            }
        }
    }
}
