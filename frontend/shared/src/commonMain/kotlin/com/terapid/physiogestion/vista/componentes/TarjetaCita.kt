package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * Una cita de la agenda. Recepción también ve el nombre del fisioterapeuta.
 * [acciones] dibuja los botones que corresponden al rol (confirmar, cancelar, asistencia…).
 */
@Composable
fun TarjetaCita(
    cita: Cita,
    mostrarFisioterapeuta: Boolean,
    resaltada: Boolean = false,
    acciones: (@Composable RowScope.() -> Unit)? = null,
) {
    TarjetaPhysio(borde = if (resaltada) ColoresPhysio.Turquesa else MaterialTheme.colorScheme.outline) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(84.dp)) {
                Text(cita.hora, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ColoresPhysio.Turquesa)
                Text("${cita.duracionMinutos} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cita.paciente, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(cita.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (mostrarFisioterapeuta) {
                    Text(cita.fisioterapeuta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Etiqueta(cita.estado.etiqueta, cita.estado.color)
            if (resaltada) Etiqueta("Siguiente", ColoresPhysio.Turquesa)
            if (cita.pacienteId == null) Etiqueta("Sin cuenta en la app", ColoresPhysio.TextoSecundario)
        }
        cita.nota?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (acciones != null && cita.abierta) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = acciones)
        }
    }
}

/** Botón compacto para las acciones de una cita. */
@Composable
fun RowScope.AccionCita(texto: String, alPulsar: () -> Unit, habilitado: Boolean = true) {
    OutlinedButton(
        onClick = alPulsar,
        enabled = habilitado,
        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** «‹  Hoy · Mié 04/11  ›» para moverse por los días de la agenda. */
@Composable
fun SelectorDia(etiqueta: String, alAnterior: () -> Unit, alSiguiente: () -> Unit, alHoy: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = alAnterior, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ Anterior") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(etiqueta, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (alHoy != null) BotonEnlace("Volver a hoy", alHoy)
        }
        TextButton(onClick = alSiguiente, modifier = Modifier.heightIn(min = 48.dp)) { Text("Siguiente ›") }
    }
}

/** Confirma la cancelación y pide el motivo (opcional). */
@Composable
fun DialogoCancelarCita(cita: Cita, nota: String?, alCambiarNota: ((String) -> Unit)?, alConfirmar: () -> Unit, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("¿Cancelar la cita?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${cita.paciente} · ${cita.hora} con ${cita.fisioterapeuta}. La hora quedará libre para otra persona.")
                if (alCambiarNota != null) {
                    OutlinedTextField(
                        value = nota.orEmpty(),
                        onValueChange = alCambiarNota,
                        label = { Text("Motivo (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = alConfirmar) { Text("Sí, cancelar", color = ColoresPhysio.Alerta) } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("No") } },
    )
}
