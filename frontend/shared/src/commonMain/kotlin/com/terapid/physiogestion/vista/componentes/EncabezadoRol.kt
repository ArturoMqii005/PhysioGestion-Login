package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** Encabezado común de las vistas de cada rol: título, usuario, rol y "Cerrar sesión". */
@Composable
fun EncabezadoRol(
    titulo: String,
    usuario: Usuario,
    alCerrarSesion: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PhysioGestion", color = ColoresPhysio.Turquesa, style = MaterialTheme.typography.labelLarge)
                Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = alCerrarSesion, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cerrar sesión")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Etiqueta(usuario.rol.etiqueta, usuario.rol.color)
            Text(
                usuario.nombre,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
