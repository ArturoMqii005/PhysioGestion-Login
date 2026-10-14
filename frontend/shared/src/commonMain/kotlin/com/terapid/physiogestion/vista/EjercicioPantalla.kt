package com.terapid.physiogestion.vista

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.servicio.movimiento.mensaje
import com.terapid.physiogestion.viewmodel.EjercicioUiState
import com.terapid.physiogestion.viewmodel.EjercicioViewModel
import com.terapid.physiogestion.viewmodel.FaseMedicion
import com.terapid.physiogestion.vista.componentes.DetalleTecnico
import com.terapid.physiogestion.vista.componentes.EncabezadoSecundario
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * VISTA de la medición con sensores (RFN-26, RFN-27). No lee sensores ni
 * cuenta repeticiones: dibuja el estado que publica el ViewModel y le avisa
 * lo que toca el paciente.
 *
 * [alTerminar] recibe true si el paciente completó el ejercicio, para
 * marcarlo como realizado en su rutina.
 */
@Composable
fun EjercicioPantalla(
    viewModel: EjercicioViewModel,
    alRegresar: () -> Unit,
    alTerminar: (completado: Boolean) -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val ejercicio = estado.ejercicio

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EncabezadoSecundario(
                titulo = ejercicio?.nombre ?: "Cargando ejercicio…",
                alRegresar = alRegresar,
                subtitulo = ejercicio?.let { e ->
                    "${e.series} series × ${e.repeticiones} repeticiones" + (e.anguloObjetivo?.let { " · objetivo $it°" } ?: "")
                },
            )
            estado.mensajeError?.let { Text(it, color = ColoresPhysio.Pendiente, style = MaterialTheme.typography.bodyMedium) }

            when (estado.fase) {
                FaseMedicion.PREPARADO -> BloquePreparado(estado, viewModel::comenzar)
                FaseMedicion.CALIBRANDO -> BloqueCalibrando()
                FaseMedicion.MIDIENDO -> BloqueMidiendo(estado, viewModel)
                FaseMedicion.TERMINADO -> BloqueTerminado(estado, viewModel::reintentar) { alTerminar(estado.objetivoCumplido) }
            }
            DetalleTecnico(estado.detalleTecnico)
        }
    }
}

@Composable
private fun BloquePreparado(estado: EjercicioUiState, alComenzar: () -> Unit) {
    TarjetaPhysio {
        Text("Antes de empezar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (estado.modoManual) {
            Text("Este dispositivo no tiene sensores de movimiento. Haz el ejercicio y suma cada repetición con el botón.")
        } else {
            Text("1. Sujeta el teléfono a la parte del cuerpo que vas a mover (por ejemplo, el muslo) con la pantalla hacia afuera.")
            Text("2. Quédate quieto unos segundos mientras se calibra el sensor.")
            Text("3. Haz cada repetición a ritmo constante: llega al ángulo indicado y regresa a la posición inicial.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Etiqueta(
                if (estado.hayAcelerometro) "Acelerómetro listo" else "Sin acelerómetro",
                if (estado.hayAcelerometro) ColoresPhysio.Exito else ColoresPhysio.Pendiente,
            )
            if (estado.hayAcelerometro) {
                Etiqueta(
                    if (estado.hayGiroscopio) "Giroscopio listo" else "Sin giroscopio",
                    if (estado.hayGiroscopio) ColoresPhysio.Exito else ColoresPhysio.TextoSecundario,
                )
            }
        }
    }
    Button(
        onClick = alComenzar,
        enabled = estado.ejercicio != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
    ) {
        Text(if (estado.modoManual) "Comenzar conteo manual" else "Comenzar", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BloqueCalibrando() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = ColoresPhysio.Turquesa)
            Text("Calibrando: mantén el teléfono quieto", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BloqueMidiendo(estado: EjercicioUiState, viewModel: EjercicioViewModel) {
    TarjetaPhysio(borde = ColoresPhysio.Turquesa) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${estado.repeticiones}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = ColoresPhysio.Turquesa,
            )
            Text("/ ${estado.objetivo} repeticiones", style = MaterialTheme.typography.titleMedium)
        }
        LinearProgressIndicator(
            progress = { estado.progreso },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = ColoresPhysio.Turquesa,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text("Tiempo: ${formatoReloj(estado.segundos)}", style = MaterialTheme.typography.bodyMedium)

        if (!estado.modoManual) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(estado.rango.color, CircleShape))
                Spacer(Modifier.size(8.dp))
                Column {
                    Text("Ángulo: ${estado.anguloActual.toInt()}° · máximo ${estado.anguloMaximo.toInt()}°")
                    Text(estado.rango.mensaje(), style = MaterialTheme.typography.bodySmall, color = estado.rango.color)
                }
            }
        }
    }

    if (estado.modoManual) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::restarRepeticion, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text("− 1")
            }
            Button(
                onClick = viewModel::sumarRepeticion,
                modifier = Modifier.weight(2f).heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.AzulProfundo, contentColor = ColoresPhysio.Texto),
            ) {
                Text("+1 repetición", fontWeight = FontWeight.Bold)
            }
        }
    }

    Button(
        onClick = viewModel::terminar,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
    ) {
        Text("Terminar ejercicio", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BloqueTerminado(estado: EjercicioUiState, alRepetir: () -> Unit, alContinuar: () -> Unit) {
    if (estado.guardando) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ColoresPhysio.Turquesa)
        }
        return
    }
    TarjetaPhysio(borde = if (estado.objetivoCumplido) ColoresPhysio.Exito else ColoresPhysio.Pendiente) {
        Text("Resumen del ejercicio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Repeticiones: ${estado.repeticiones} de ${estado.objetivo}")
        if (!estado.modoManual) Text("Ángulo máximo: ${estado.anguloMaximo.toInt()}°")
        Text("Duración: ${formatoReloj(estado.segundos)}")
        Etiqueta(
            if (estado.objetivoCumplido) "Completaste el objetivo" else "No alcanzaste el objetivo",
            if (estado.objetivoCumplido) ColoresPhysio.Exito else ColoresPhysio.Pendiente,
        )
        Text(
            when (estado.enviado) {
                true -> "El resumen se envió a tu fisioterapeuta."
                false -> "Se guardó en el teléfono. Se enviará a la clínica cuando haya conexión."
                null -> "Conteo manual: se marcará en tu rutina, pero no se envía como medición de sensores."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = alRepetir, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Repetir") }
        Button(
            onClick = alContinuar,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
        ) {
            Text("Continuar", fontWeight = FontWeight.Bold)
        }
    }
}

/** 75 -> "01:15" */
private fun formatoReloj(segundos: Int): String {
    val m = segundos / 60
    val s = segundos % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
