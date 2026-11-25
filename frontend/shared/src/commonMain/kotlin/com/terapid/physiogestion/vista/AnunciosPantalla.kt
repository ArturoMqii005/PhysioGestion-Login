package com.terapid.physiogestion.vista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.Anuncio
import com.terapid.physiogestion.modelo.Destinatarios
import com.terapid.physiogestion.plataforma.formatearFechaHora
import com.terapid.physiogestion.viewmodel.AnunciosViewModel
import com.terapid.physiogestion.viewmodel.MAXIMO_ANUNCIOS_DIA
import com.terapid.physiogestion.vista.componentes.BotonPildora
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.ChipOpcion
import com.terapid.physiogestion.vista.componentes.EncabezadoSeccion
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.ExpresionXochi
import com.terapid.physiogestion.vista.componentes.Iconos
import com.terapid.physiogestion.vista.componentes.MensajeInfo
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.XochiDice
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * VISTA de recepción y administración: anuncios de la clínica. Llegan como
 * notificación a los teléfonos y quedan en la bandeja de avisos. Para que no
 * se vuelvan ruido, se pueden publicar hasta 3 en 24 horas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnunciosPantalla(viewModel: AnunciosViewModel) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EncabezadoSeccion(
                    "Anuncios",
                    "Llegan como notificación a los teléfonos y quedan en Avisos. Máximo $MAXIMO_ANUNCIOS_DIA en 24 horas.",
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BotonPildora(
                        "Publicar anuncio",
                        viewModel::abrirFormulario,
                        Modifier.fillMaxWidth(),
                        icono = Iconos.Mas,
                        violeta = true,
                        habilitado = e.puedePublicar,
                    )
                    if (!e.cargando && !e.puedePublicar) {
                        Text(
                            "Ya se publicaron $MAXIMO_ANUNCIOS_DIA anuncios en las últimas 24 horas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColoresPhysio.Pendiente,
                        )
                    }
                }
            }
            e.aviso?.let { item { MensajeInfo(it) } }
            when {
                e.cargando -> item { Cargando("Cargando anuncios…") }
                e.error != null -> item { ErrorConReintento(e.error ?: "", viewModel::cargar) }
                e.anuncios.isEmpty() -> item {
                    XochiDice(
                        "Todavía no hay anuncios. Por ejemplo: «Cerramos el lunes festivo» o «Junta del personal a las 8:00».",
                        expresion = ExpresionXochi.FELIZ,
                        tamano = 72.dp,
                    )
                }
                else -> items(e.anuncios, key = { it.id }) { TarjetaAnuncio(it) }
            }
        }
    }

    if (e.formularioAbierto) {
        DialogoSeccion(alCerrar = viewModel::cerrarFormulario) {
            Text("Publicar anuncio", style = MaterialTheme.typography.headlineSmall, color = ColoresPhysio.Texto)
            Text("Para quién", fontWeight = FontWeight.SemiBold, color = ColoresPhysio.Texto)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Destinatarios.entries.forEach { d ->
                    ChipOpcion(d.etiqueta, d == e.destinatarios, { viewModel.elegirDestinatarios(d) })
                }
            }
            OutlinedTextField(
                value = e.titulo,
                onValueChange = viewModel::escribirTitulo,
                label = { Text("Título") },
                placeholder = { Text("Cerramos el lunes festivo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !e.publicando,
            )
            OutlinedTextField(
                value = e.texto,
                onValueChange = viewModel::escribirTexto,
                label = { Text("Mensaje") },
                placeholder = { Text("El lunes no hay consulta. Tus citas de ese día se moverán; recepción te llamará.") },
                supportingText = { Text("${e.texto.length} / 300") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                maxLines = 6,
                enabled = !e.publicando,
            )
            Text(
                "Se manda en ese momento como notificación. No incluyas datos de pacientes.",
                style = MaterialTheme.typography.bodySmall,
                color = ColoresPhysio.TextoSecundario,
            )
            e.errorFormulario?.let { Text(it, color = ColoresPhysio.Pendiente, fontSize = 14.sp) }
            BotonPildora("Publicar", viewModel::publicar, Modifier.fillMaxWidth(), icono = null, violeta = true, habilitado = !e.publicando)
            TextButton(onClick = viewModel::cerrarFormulario, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !e.publicando) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun TarjetaAnuncio(a: Anuncio) {
    TarjetaPhysio {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Etiqueta(a.destinatarios.etiqueta, ColoresPhysio.Lila)
            Text(
                "${formatearFechaHora(a.creado)} · ${a.creadoPor}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(a.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(a.texto, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (a.personas == 1) "Le llegó a 1 persona" else "Les llegó a ${a.personas} personas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
