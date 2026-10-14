package com.terapid.physiogestion.vista

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.SesionTerapia
import com.terapid.physiogestion.modelo.fechaIsoLegible
import com.terapid.physiogestion.viewmodel.HistorialViewModel
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.DetalleTecnico
import com.terapid.physiogestion.vista.componentes.EncabezadoSecundario
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.FilaHistorial
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** VISTA del historial de sesiones (RFN-31): GET /sesiones; sin conexión, la copia del teléfono. */
@Composable
fun HistorialPantalla(
    viewModel: HistorialViewModel,
    alRegresar: () -> Unit,
    alAbrirSesion: (Int) -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // Se recarga cada vez que la pantalla aparece (por ejemplo, después de editar o eliminar una sesión).
    LaunchedEffect(Unit) { viewModel.cargar() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val subtitulo = if (estado.origen == OrigenDatos.SERVIDOR) "${estado.total} sesiones registradas en la clínica" else null
                EncabezadoSecundario("Mi historial", alRegresar, subtitulo)
            }

            if (estado.pendientes > 0) {
                item {
                    TarjetaPhysio(borde = ColoresPhysio.Pendiente) {
                        Etiqueta(
                            if (estado.pendientes == 1) "1 sesión pendiente de envío" else "${estado.pendientes} sesiones pendientes de envío",
                            ColoresPhysio.Pendiente,
                        )
                        Text(
                            "Se guardaron sin conexión. Toca «Enviar ahora» para mandarlas a tu fisioterapeuta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = viewModel::enviarPendientes,
                            enabled = !estado.enviando,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(if (estado.enviando) "Enviando…" else "Enviar ahora")
                        }
                    }
                }
            }
            estado.aviso?.let { item { Text(it, color = ColoresPhysio.Turquesa, style = MaterialTheme.typography.bodyMedium) } }

            when {
                estado.cargando -> item { Cargando("Cargando tu historial…") }
                estado.origen == OrigenDatos.ALMACENAMIENTO_LOCAL -> {
                    item {
                        TarjetaPhysio(borde = ColoresPhysio.Pendiente) {
                            Etiqueta("Sin conexión", ColoresPhysio.Pendiente)
                            Text(estado.error ?: "", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Se muestran las sesiones guardadas en este teléfono.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = viewModel::cargar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") }
                        }
                    }
                    if (estado.locales.isEmpty()) {
                        item { Text("Aún no hay sesiones guardadas en este teléfono.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(estado.locales, key = { "local-${it.id}" }) { FilaHistorial(it) }
                }
                else -> {
                    if (estado.sesiones.isEmpty()) {
                        item {
                            Text(
                                "Aún no registras sesiones. Cuando guardes la primera aparecerá aquí.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(estado.sesiones, key = { it.id }) { sesion -> FilaSesion(sesion) { alAbrirSesion(sesion.id) } }
                    if (estado.hayMas) {
                        item {
                            OutlinedButton(
                                onClick = viewModel::cargarMas,
                                enabled = !estado.cargandoMas,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(if (estado.cargandoMas) "Cargando…" else "Ver sesiones anteriores")
                            }
                        }
                    }
                }
            }
            item { DetalleTecnico(estado.detalleTecnico) }
        }
    }
}

/** Una sesión del servidor; toda la tarjeta abre el detalle. */
@Composable
private fun FilaSesion(sesion: SesionTerapia, alTocar: () -> Unit) {
    TarjetaPhysio(modifier = Modifier.clickable(onClick = alTocar)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(fechaIsoLegible(sesion.fechaHoraInicio), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${sesion.porcentajeCumplimiento} % cumplido · dolor ${sesion.dolorAntes} → ${sesion.dolorDespues} · ${sesion.dificultad.etiqueta}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Ver ›", color = ColoresPhysio.Turquesa, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Etiqueta(sesion.semaforo.etiqueta, sesion.semaforo.color)
            val medidos = sesion.ejercicios.count { it.verificado }
            if (medidos > 0) Etiqueta("$medidos con sensores", ColoresPhysio.AzulClaro)
        }
        sesion.observacion?.let {
            Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
