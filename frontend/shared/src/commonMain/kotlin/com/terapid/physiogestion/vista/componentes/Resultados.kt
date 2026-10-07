package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.modelo.EvaluacionSesion
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.plataforma.formatearFechaHora
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** Resultado de guardar la sesión; si hay alerta de dolor, se muestra primero (RFX-09). */
@Composable
fun TarjetaResultado(resultado: EvaluacionSesion, alCerrar: () -> Unit) {
    val alerta = resultado.alerta
    TarjetaPhysio(
        fondo = if (alerta != null) ColoresPhysio.FondoAlerta else MaterialTheme.colorScheme.surface,
        borde = if (alerta != null) ColoresPhysio.Alerta else ColoresPhysio.Exito,
    ) {
        if (alerta != null) {
            Etiqueta("Atención: dolor alto", ColoresPhysio.Alerta)
            Text(alerta.recomendacion, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(alerta.motivo, style = MaterialTheme.typography.bodyMedium)
        } else {
            Etiqueta("Sesión guardada", ColoresPhysio.Exito)
        }
        Text(
            "Cumplimiento: ${resultado.cumplimiento} % · Puntos ganados: ${resultado.puntos}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Tu sesión quedó guardada en el teléfono y se enviará a tu fisioterapeuta cuando haya conexión.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = alCerrar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Entendido") }
    }
}

/** Una fila del historial de sesiones (RFN-31). */
@Composable
fun FilaHistorial(sesion: RegistroSesion) {
    TarjetaPhysio {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatearFechaHora(sesion.finMillis), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${sesion.cumplimiento} % cumplido · dolor ${sesion.dolorAntes} → ${sesion.dolorDespues} · ${sesion.dificultad.etiqueta}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("+${sesion.puntos} pts", color = ColoresPhysio.Turquesa, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (sesion.pendienteEnvio) Etiqueta("Pendiente de envío", ColoresPhysio.Pendiente)
            else Etiqueta("Enviada", ColoresPhysio.Exito)
            if (sesion.alertaDolor) Etiqueta("Dolor alto", ColoresPhysio.Alerta)
        }
    }
}
