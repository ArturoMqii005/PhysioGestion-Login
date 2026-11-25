package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * Encabezado de una sección del personal que se abre desde las pestañas
 * (Solicitudes, Anuncios): nombre de la app, título y para qué sirve.
 */
@Composable
fun EncabezadoSeccion(titulo: String, descripcion: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("PhysioGestion", color = ColoresPhysio.Lila, style = MaterialTheme.typography.labelLarge)
        Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (descripcion != null) {
            Text(descripcion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Opción que se elige tocándola (filtro, día, hora, destinatarios). La elegida va en violeta. */
@Composable
fun ChipOpcion(texto: String, elegida: Boolean, alElegir: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (elegida) ColoresPhysio.Violeta else Color.Transparent)
            .border(1.dp, if (elegida) ColoresPhysio.Violeta else ColoresPhysio.BordeFuerte, RoundedCornerShape(50))
            .tocable(texto, alPulsar = alElegir, rol = Role.RadioButton)
            .semantics { selected = elegida }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = if (elegida) Color.White else ColoresPhysio.Texto,
        fontWeight = FontWeight.SemiBold,
    )
}
