package com.terapid.physiogestion.vista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.plataforma.Entorno
import com.terapid.physiogestion.plataforma.Plataforma
import com.terapid.physiogestion.plataforma.formatearFechaHora
import com.terapid.physiogestion.viewmodel.EstadoEnvio
import com.terapid.physiogestion.viewmodel.RutinaUiState
import com.terapid.physiogestion.viewmodel.RutinaViewModel
import com.terapid.physiogestion.vista.componentes.DetalleTecnico
import com.terapid.physiogestion.vista.componentes.EncabezadoSecundario
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.SelectorDificultad
import com.terapid.physiogestion.vista.componentes.SelectorDolor
import com.terapid.physiogestion.vista.componentes.TarjetaEjercicio
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.TarjetaResultado
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * VISTA del rol PACIENTE: pantalla "Mi rutina de hoy". Solo dibuja el estado
 * que publica el ViewModel y le avisa de lo que toca el paciente; no calcula
 * ni guarda nada.
 */
@Composable
fun RutinaPantalla(
    usuario: Usuario,
    viewModel: RutinaViewModel,
    alRegresar: () -> Unit,
    alMedirEjercicio: (Int) -> Unit,
    alVerHistorial: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            estado.cargando && estado.rutina == null -> PantallaCargando()
            estado.rutina == null -> PantallaError(estado.mensajeError, viewModel::cargarRutina, alRegresar)
            else -> ContenidoRutina(usuario, estado, viewModel, alRegresar, alMedirEjercicio, alVerHistorial)
        }
    }
}

@Composable
private fun PantallaCargando() {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = ColoresPhysio.Turquesa)
        Text("Descargando tu rutina…", Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun PantallaError(mensaje: String?, alReintentar: () -> Unit, alRegresar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sin rutina disponible", style = MaterialTheme.typography.titleLarge)
        Text(
            mensaje ?: "Ocurrió un problema al cargar tu rutina.",
            Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = alReintentar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") }
        OutlinedButton(onClick = alRegresar, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
            Text("Regresar")
        }
    }
}

@Composable
private fun ContenidoRutina(
    usuario: Usuario,
    estado: RutinaUiState,
    viewModel: RutinaViewModel,
    alRegresar: () -> Unit,
    alMedirEjercicio: (Int) -> Unit,
    alVerHistorial: () -> Unit,
) {
    val rutina = estado.rutina ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { EncabezadoSecundario("Mi rutina de hoy", alRegresar, subtitulo = usuario.nombre) }
        item { DatosRutina(estado) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ejercicios de hoy · ${estado.completados.size} de ${estado.totalEjercicios}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Racha: ${estado.racha} d · ${estado.puntosTotales} pts", color = ColoresPhysio.Turquesa)
                }
                LinearProgressIndicator(
                    progress = { estado.avance },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = ColoresPhysio.Turquesa,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        itemsIndexed(rutina.ejercicios, key = { _, e -> e.id }) { i, ejercicio ->
            TarjetaEjercicio(
                numero = i + 1,
                ejercicio = ejercicio,
                realizado = ejercicio.id in estado.completados,
                alCambiar = { viewModel.alternarEjercicio(ejercicio.id) },
                medicion = estado.mediciones[ejercicio.id],
                alMedir = { alMedirEjercicio(ejercicio.id) },
            )
        }

        item {
            TarjetaPhysio {
                Text("¿Cómo te sientes?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SelectorDolor("Dolor antes de la sesión", estado.dolorAntes, viewModel::cambiarDolorAntes)
                SelectorDolor("Dolor después de la sesión", estado.dolorDespues, viewModel::cambiarDolorDespues)
                SelectorDificultad(estado.dificultad, viewModel::cambiarDificultad)
                OutlinedTextField(
                    value = estado.observacion,
                    onValueChange = viewModel::cambiarObservacion,
                    label = { Text("Observación para tu fisioterapeuta (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }

        estado.aviso?.let { aviso ->
            item { Text(aviso, color = ColoresPhysio.Pendiente, style = MaterialTheme.typography.bodyMedium) }
        }

        item {
            Button(
                onClick = viewModel::guardarSesion,
                enabled = !estado.guardando,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
            ) {
                if (estado.guardando) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar sesión", fontWeight = FontWeight.Bold)
                }
            }
        }

        estado.resultado?.let { resultado ->
            item {
                val enviada = when (estado.envio) {
                    EstadoEnvio.ENVIADA -> true
                    EstadoEnvio.PENDIENTE -> false
                    else -> null
                }
                TarjetaResultado(resultado, enviada, viewModel::cerrarResultado)
            }
        }

        item {
            OutlinedButton(onClick = alVerHistorial, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Ver mi historial de sesiones")
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 8.dp)) {
                DetalleTecnico(estado.detalleTecnico)
                Text(
                    "PhysioGestion v${Plataforma.versionApp} · ${Plataforma.nombre}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DatosRutina(estado: RutinaUiState) {
    val rutina = estado.rutina ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(max = 640.dp)) {
        Text(
            "Tu meta: ${rutina.metaFuncional}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Asignada por ${rutina.fisioterapeuta} · vigente desde ${rutina.vigenteDesde}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val fecha = estado.actualizadaEn?.let(::formatearFechaHora) ?: ""
        if (estado.origen == OrigenDatos.SERVIDOR) {
            Etiqueta("Actualizada desde el servidor · $fecha", ColoresPhysio.Exito)
        } else {
            Etiqueta("Sin conexión · rutina guardada el $fecha", ColoresPhysio.Pendiente)
            Text(
                "Puedes hacer tus ejercicios; tus sesiones se guardan en el teléfono y se enviarán después.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (estado.copiaVencida) {
                Etiqueta(
                    "Esta copia tiene más de ${Entorno.VIGENCIA_RUTINA_DIAS} días: conéctate para actualizarla",
                    ColoresPhysio.Alerta,
                )
            }
        }
    }
}
