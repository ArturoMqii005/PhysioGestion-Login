package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** Una cita de la agenda. Recepción también ve el nombre del fisioterapeuta. */
@Composable
fun TarjetaCita(cita: Cita, mostrarFisioterapeuta: Boolean, resaltada: Boolean = false) {
    TarjetaPhysio(borde = if (resaltada) ColoresPhysio.Turquesa else MaterialTheme.colorScheme.outline) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                cita.hora,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ColoresPhysio.Turquesa,
                modifier = Modifier.width(84.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cita.paciente, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(cita.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (mostrarFisioterapeuta) {
                    Text(cita.fisioterapeuta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Etiqueta(cita.estado.etiqueta, cita.estado.color)
            if (resaltada) Etiqueta("Siguiente", ColoresPhysio.Turquesa)
        }
    }
}
