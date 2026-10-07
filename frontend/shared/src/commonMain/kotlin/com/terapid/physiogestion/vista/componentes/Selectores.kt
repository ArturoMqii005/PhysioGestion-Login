package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** Botón de opción de al menos 48 dp de alto (RC-14). */
@Composable
private fun Opcion(
    texto: String,
    seleccionada: Boolean,
    alElegir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forma = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(forma)
            .background(if (seleccionada) ColoresPhysio.Turquesa else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (seleccionada) ColoresPhysio.Turquesa else MaterialTheme.colorScheme.outline, forma)
            .selectable(selected = seleccionada, role = Role.RadioButton, onClick = alElegir),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            fontWeight = if (seleccionada) FontWeight.Bold else FontWeight.Normal,
            color = if (seleccionada) ColoresPhysio.Fondo else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Escala numérica de dolor de 0 a 10 (RFN-27, RD-03), repartida en dos filas. */
@Composable
fun SelectorDolor(
    titulo: String,
    valor: Int?,
    alElegir: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        listOf(0..5, 6..10).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fila.forEach { nivel ->
                    Opcion(
                        texto = nivel.toString(),
                        seleccionada = valor == nivel,
                        alElegir = { alElegir(nivel) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // La segunda fila tiene 5 números; un espacio vacío mantiene las columnas alineadas.
                if (fila.count() == 5) Box(Modifier.weight(1f))
            }
        }
        Text(
            "0 = sin dolor · 10 = el peor dolor imaginable",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dificultad percibida (RFN-28). */
@Composable
fun SelectorDificultad(
    valor: Dificultad?,
    alElegir: (Dificultad) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("¿Qué tan difícil te pareció?", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dificultad.entries.forEach { d ->
                Opcion(
                    texto = d.etiqueta,
                    seleccionada = valor == d,
                    alElegir = { alElegir(d) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
