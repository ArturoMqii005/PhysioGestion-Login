package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.modelo.Ejercicio
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** Tarjeta base con el estilo de la app. */
@Composable
fun TarjetaPhysio(
    modifier: Modifier = Modifier,
    fondo: Color = MaterialTheme.colorScheme.surface,
    borde: Color = MaterialTheme.colorScheme.outline,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = fondo),
        border = BorderStroke(1.dp, borde),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = contenido)
    }
}

/**
 * Etiqueta de estado con texto y punto de color. El significado nunca
 * depende solo del color (RC-25).
 */
@Composable
fun Etiqueta(texto: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Un ejercicio de la rutina; toda la fila se puede tocar (mínimo 48 dp, RC-14). */
@Composable
fun TarjetaEjercicio(
    numero: Int,
    ejercicio: Ejercicio,
    realizado: Boolean,
    alCambiar: () -> Unit,
) {
    TarjetaPhysio(
        fondo = if (realizado) ColoresPhysio.Turquesa.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        borde = if (realizado) ColoresPhysio.Turquesa else MaterialTheme.colorScheme.outline,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = realizado, role = Role.Checkbox, onValueChange = { alCambiar() }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$numero. ${ejercicio.nombre}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${ejercicio.series} series × ${ejercicio.repeticiones} repeticiones · descanso ${ejercicio.descansoSegundos} s",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColoresPhysio.Turquesa,
                )
            }
            Checkbox(
                checked = realizado,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = ColoresPhysio.Turquesa),
            )
        }
        Text(
            ejercicio.indicaciones,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Etiqueta(
            texto = if (realizado) "Realizado · ${ejercicio.zonaCorporal}" else "Pendiente · ${ejercicio.zonaCorporal}",
            color = if (realizado) ColoresPhysio.Exito else ColoresPhysio.TextoSecundario,
        )
    }
}
