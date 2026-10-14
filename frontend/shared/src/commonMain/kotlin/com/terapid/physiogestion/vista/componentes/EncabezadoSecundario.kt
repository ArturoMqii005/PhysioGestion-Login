package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.plataforma.Entorno
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** Encabezado de las pantallas internas del paciente: botón para regresar y título. */
@Composable
fun EncabezadoSecundario(titulo: String, alRegresar: () -> Unit, subtitulo: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(onClick = alRegresar, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("‹ Regresar", color = ColoresPhysio.Turquesa)
        }
        Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitulo != null) {
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Método, ruta y código HTTP de la última petición. Solo aparece fuera de
 * producción (Entorno.registroActivo): sirve para revisar la app, el
 * paciente nunca lo ve.
 */
@Composable
fun DetalleTecnico(texto: String?) {
    if (texto == null || !Entorno.registroActivo) return
    Text(
        "Desarrollo · $texto",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
