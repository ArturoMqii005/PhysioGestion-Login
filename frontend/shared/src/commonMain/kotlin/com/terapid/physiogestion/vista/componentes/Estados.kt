package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** Indicador de carga dentro de una lista. */
@Composable
fun Cargando(texto: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = ColoresPhysio.Turquesa)
        Text(texto, Modifier.padding(start = 12.dp))
    }
}

/** Mensaje de error comprensible con botón para reintentar (RC-19). */
@Composable
fun ErrorConReintento(mensaje: String, alReintentar: () -> Unit) {
    TarjetaPhysio(borde = ColoresPhysio.Alerta) {
        Etiqueta("No se pudo cargar", ColoresPhysio.Alerta)
        Text(mensaje, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = alReintentar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") }
    }
}

/** Tarjeta pequeña con un número grande y su descripción. */
@Composable
fun TarjetaDato(valor: String, descripcion: String, color: Color, modifier: Modifier = Modifier) {
    TarjetaPhysio(modifier = modifier) {
        Text(valor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        Text(descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
