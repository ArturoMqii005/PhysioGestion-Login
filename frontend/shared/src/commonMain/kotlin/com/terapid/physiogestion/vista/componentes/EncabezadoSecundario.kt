package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.plataforma.Entorno
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * Encabezado de las pantallas internas: botón redondo para regresar (como en
 * las apps de Apple) y el título grande debajo.
 */
@Composable
fun EncabezadoSecundario(titulo: String, alRegresar: () -> Unit, subtitulo: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BotonRedondo(Iconos.Atras, "Regresar", alRegresar, tamano = 48.dp)
        Text(titulo, style = MaterialTheme.typography.headlineMedium, color = ColoresPhysio.Texto, modifier = Modifier.padding(top = 6.dp))
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
