package com.terapid.physiogestion.vista

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.Aviso
import com.terapid.physiogestion.modelo.TipoAviso
import com.terapid.physiogestion.plataforma.formatearFechaHora
import com.terapid.physiogestion.viewmodel.AvisosViewModel
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.EncabezadoSecundario
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.TarjetaActivarNotificaciones
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * VISTA de la bandeja de avisos (NOT-01..04). Tocar un aviso lo marca como
 * leído y, si tiene a dónde llevar ([destino] no es null), abre esa pantalla.
 */
@Composable
fun AvisosPantalla(
    viewModel: AvisosViewModel,
    motivoNotificaciones: String,
    textoVacio: String,
    destino: (Aviso) -> String?,
    alRegresar: () -> Unit,
    alAbrirAviso: (Aviso) -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EncabezadoSecundario(
                    "Avisos",
                    alRegresar,
                    subtitulo = when {
                        estado.cargando -> null
                        estado.sinLeer == 0 -> "Estás al día."
                        estado.sinLeer == 1 -> "1 aviso sin leer"
                        else -> "${estado.sinLeer} avisos sin leer"
                    },
                )
            }
            item { TarjetaActivarNotificaciones(estado.notificaciones, motivoNotificaciones, viewModel::activarNotificaciones) }

            when {
                estado.cargando -> item { Cargando("Cargando tus avisos…") }
                estado.error != null && estado.avisos.isEmpty() -> item { ErrorConReintento(estado.error ?: "", viewModel::cargar) }
                estado.avisos.isEmpty() -> item {
                    Text(textoVacio, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    if (estado.sinLeer > 0) {
                        item {
                            OutlinedButton(
                                onClick = viewModel::marcarTodos,
                                enabled = !estado.procesando,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("Marcar todos como leídos") }
                        }
                    }
                    items(estado.avisos, key = { it.id }) { aviso ->
                        TarjetaAviso(aviso, destino(aviso)) {
                            viewModel.abrir(aviso)
                            alAbrirAviso(aviso)
                        }
                    }
                }
            }
        }
    }
}

/** Un aviso; sin leer lleva borde de color y la etiqueta «Nuevo». */
@Composable
private fun TarjetaAviso(aviso: Aviso, destino: String?, alPulsar: () -> Unit) {
    val borde = when {
        aviso.leido -> MaterialTheme.colorScheme.outline
        aviso.tipo == TipoAviso.DOLOR_ALTO -> ColoresPhysio.Alerta
        else -> ColoresPhysio.Turquesa
    }
    TarjetaPhysio(
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = destino ?: "Marcar como leído", onClick = alPulsar),
        borde = borde,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Etiqueta(aviso.tipo.etiqueta, aviso.tipo.color)
            if (!aviso.leido) Etiqueta("Nuevo", ColoresPhysio.Turquesa)
        }
        Text(
            aviso.titulo,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (aviso.leido) FontWeight.Normal else FontWeight.SemiBold,
        )
        Text(aviso.mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatearFechaHora(aviso.creado),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            destino?.let { Text("$it ›", style = MaterialTheme.typography.labelLarge, color = ColoresPhysio.Turquesa) }
        }
    }
}
