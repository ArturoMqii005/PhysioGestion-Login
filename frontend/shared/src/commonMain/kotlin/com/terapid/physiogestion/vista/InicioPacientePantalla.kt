package com.terapid.physiogestion.vista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.viewmodel.EstadoPasos
import com.terapid.physiogestion.viewmodel.InicioPacienteUiState
import com.terapid.physiogestion.viewmodel.InicioPacienteViewModel
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.EncabezadoRol
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.TarjetaDato
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * VISTA del panel principal del PACIENTE. Es el punto desde el que se
 * ramifica su navegación: recibe una función por cada destino posible y no
 * conoce el controlador de navegación.
 */
@Composable
fun InicioPacientePantalla(
    usuario: Usuario,
    viewModel: InicioPacienteViewModel,
    alAbrirRutina: () -> Unit,
    alAbrirHistorial: () -> Unit,
    alAbrirPerfil: () -> Unit,
    alCerrarSesion: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // Cada vez que la pantalla aparece (también al regresar de la rutina) se actualiza el resumen.
    LaunchedEffect(Unit) { viewModel.cargar() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { EncabezadoRol("Mi tratamiento", usuario, alCerrarSesion) }

            estado.aviso?.let { aviso ->
                item {
                    TarjetaPhysio(borde = ColoresPhysio.Exito) {
                        Text(aviso, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::cerrarAviso, modifier = Modifier.heightIn(min = 48.dp)) { Text("Entendido") }
                    }
                }
            }

            item { TarjetaRutinaDeHoy(estado, alAbrirRutina, viewModel::cargar) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TarjetaDato(
                        "${estado.racha}",
                        if (estado.racha == 1) "día seguido" else "días seguidos",
                        ColoresPhysio.Turquesa,
                        Modifier.weight(1f),
                    )
                    TarjetaDato("${estado.puntos}", "puntos acumulados", ColoresPhysio.Violeta, Modifier.weight(1f))
                }
            }

            item { TarjetaPasos(estado.pasos, viewModel) }

            if (estado.pendientesEnvio > 0) {
                item {
                    TarjetaPhysio(borde = ColoresPhysio.Pendiente) {
                        Etiqueta(
                            if (estado.pendientesEnvio == 1) "1 sesión espera conexión"
                            else "${estado.pendientesEnvio} sesiones esperan conexión",
                            ColoresPhysio.Pendiente,
                        )
                        Text(
                            "Están guardadas en tu teléfono. Se enviarán a tu fisioterapeuta en cuanto haya conexión.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = viewModel::sincronizar, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Enviar ahora")
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = alAbrirHistorial, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Ver mi historial")
                }
            }
            item {
                OutlinedButton(onClick = alAbrirPerfil, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Mi perfil y almacenamiento")
                }
            }
        }
    }
}

@Composable
private fun TarjetaRutinaDeHoy(estado: InicioPacienteUiState, alAbrirRutina: () -> Unit, alReintentar: () -> Unit) {
    TarjetaPhysio(borde = ColoresPhysio.Turquesa) {
        Text("Tu rutina de hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val rutina = estado.rutina
        when {
            estado.cargando && rutina == null -> Cargando("Cargando tu rutina…")
            rutina == null -> {
                Text(estado.error ?: "No pudimos cargar tu rutina.", color = ColoresPhysio.Pendiente)
                OutlinedButton(onClick = alReintentar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") }
            }
            else -> {
                Text(rutina.metaFuncional, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val medibles = rutina.ejercicios.count { it.medibleConSensores }
                Text(
                    "${rutina.ejercicios.size} ejercicios · $medibles se pueden medir con sensores · ${estado.medidosHoy} medidos hoy",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColoresPhysio.Turquesa,
                )
                if (estado.origen == OrigenDatos.ALMACENAMIENTO_LOCAL) {
                    Etiqueta("Sin conexión · copia guardada en el teléfono", ColoresPhysio.Pendiente)
                }
                Button(
                    onClick = alAbrirRutina,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
                ) {
                    Text("Iniciar rutina", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Contador de pasos (RFN-39). El sensor solo trabaja mientras esta tarjeta está en pantalla. */
@Composable
private fun TarjetaPasos(estadoPasos: EstadoPasos, viewModel: InicioPacienteViewModel) {
    TarjetaPhysio {
        Text("Actividad de hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        when (estadoPasos) {
            EstadoPasos.NO_DISPONIBLE -> Text(
                "Este dispositivo no tiene contador de pasos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EstadoPasos.FALTA_PERMISO, EstadoPasos.PERMISO_DENEGADO -> {
                Text(
                    if (estadoPasos == EstadoPasos.FALTA_PERMISO) {
                        "Para contar tus pasos, Android pide permiso de actividad física (es un dato de salud)."
                    } else {
                        "No diste permiso de actividad física. Puedes intentarlo otra vez o activarlo en Ajustes."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = viewModel::activarPasos, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Activar contador de pasos")
                }
            }
            EstadoPasos.CONTANDO -> {
                val flujo = remember(viewModel) { viewModel.pasosDeHoy() }
                val pasos by flujo.collectAsStateWithLifecycle(initialValue = null)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        pasos?.toString() ?: "…",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColoresPhysio.AzulClaro,
                    )
                    Text("pasos", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Los cuenta el propio teléfono con bajo consumo de batería.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
