package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.terapid.physiogestion.datos.ResumenAvisos
import com.terapid.physiogestion.viewmodel.EstadoNotificaciones
import com.terapid.physiogestion.viewmodel.REVISION_EN_PANTALLA_MS
import com.terapid.physiogestion.viewmodel.ResumenAvisosViewModel
import com.terapid.physiogestion.vista.tema.ColoresPhysio
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val SIN_RESUMEN: StateFlow<ResumenAvisos> = MutableStateFlow(ResumenAvisos())
private val SIN_NOTIFICACIONES: StateFlow<EstadoNotificaciones> = MutableStateFlow(EstadoNotificaciones())

/** Para las vistas que también se usan sin avisos (vistas previas, pruebas). */
val ResumenAvisosViewModel?.resumenOVacio: StateFlow<ResumenAvisos> get() = this?.resumen ?: SIN_RESUMEN
val ResumenAvisosViewModel?.notificacionesOVacio: StateFlow<EstadoNotificaciones> get() = this?.notificaciones ?: SIN_NOTIFICACIONES

/**
 * Revisa los avisos cada 30 s mientras la pantalla está visible. Al pasar a
 * segundo plano se detiene: desde ahí revisa WorkManager (Android).
 */
@Composable
fun VigilarAvisos(viewModel: ResumenAvisosViewModel) {
    val ciclo = LocalLifecycleOwner.current
    LaunchedEffect(ciclo, viewModel) {
        ciclo.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refrescar()
                delay(REVISION_EN_PANTALLA_MS)
            }
        }
    }
}

/**
 * Avisos sin leer en la vista del rol: el más reciente a la vista y el botón
 * para abrir la bandeja. Si no hay nada nuevo no ocupa espacio.
 */
@Composable
fun TarjetaAvisosSinLeer(resumen: ResumenAvisos, alAbrir: () -> Unit) {
    val ultimo = resumen.destacado ?: return
    if (resumen.sinLeer == 0) return
    val color = if (ultimo.urgente) ColoresPhysio.Alerta else ColoresPhysio.Lila
    TarjetaPhysio(borde = color, fondo = if (ultimo.urgente) ColoresPhysio.FondoAlerta else MaterialTheme.colorScheme.surface) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Etiqueta(ultimo.tipo.etiqueta, ultimo.tipo.color)
            if (resumen.sinLeer > 1) Etiqueta("${resumen.sinLeer} sin leer", ColoresPhysio.TextoSecundario)
        }
        Text(ultimo.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            ultimo.mensaje,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Button(
            onClick = alAbrir,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = ColoresPhysio.Fondo),
        ) {
            Text(if (resumen.sinLeer == 1) "Ver aviso" else "Ver los ${resumen.sinLeer} avisos")
        }
    }
}

/** Invita a permitir las notificaciones; [motivo] explica para qué sirven en ese rol. */
@Composable
fun TarjetaActivarNotificaciones(estado: EstadoNotificaciones, motivo: String, alActivar: () -> Unit) {
    if (!estado.pedirlo && estado.mensaje == null) return
    TarjetaPhysio(borde = ColoresPhysio.Pendiente) {
        if (estado.pedirlo) {
            Text("Activa las notificaciones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        estado.mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ColoresPhysio.Lila) }
        if (estado.pedirlo) {
            OutlinedButton(onClick = alActivar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Permitir notificaciones") }
        }
    }
}
