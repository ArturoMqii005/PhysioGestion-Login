package com.terapid.physiogestion.vista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.PruebaEscaleras
import com.terapid.physiogestion.plataforma.formatearFechaHora
import com.terapid.physiogestion.viewmodel.EscalerasUiState
import com.terapid.physiogestion.viewmodel.EscalerasViewModel
import com.terapid.physiogestion.viewmodel.FaseEscaleras
import com.terapid.physiogestion.vista.componentes.EncabezadoSecundario
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.TarjetaDato
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.tema.ColoresPhysio
import kotlin.math.roundToInt

/**
 * VISTA de la prueba de escaleras (barómetro + contador de pasos). Solo
 * dibuja lo que publica el EscalerasViewModel.
 */
@Composable
fun EscalerasPantalla(viewModel: EscalerasViewModel, alRegresar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EncabezadoSecundario(
                titulo = "Prueba de escaleras",
                alRegresar = alRegresar,
                subtitulo = estado.metaFuncional?.let { "Tu meta: $it" },
            )
            estado.mensajeError?.let { Text(it, color = ColoresPhysio.Pendiente, style = MaterialTheme.typography.bodyMedium) }
            when (estado.fase) {
                FaseEscaleras.PREPARADO -> BloqueInstrucciones(estado, viewModel::comenzar)
                FaseEscaleras.CALIBRANDO -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = ColoresPhysio.Turquesa)
                        Text("Calibrando: quédate quieto al pie de la escalera", style = MaterialTheme.typography.titleMedium)
                    }
                }
                FaseEscaleras.MIDIENDO -> BloqueSubiendo(estado, viewModel::terminar)
                FaseEscaleras.TERMINADO -> BloqueResumen(estado, viewModel::reintentar, alRegresar)
            }
        }
    }
}

@Composable
private fun BloqueInstrucciones(estado: EscalerasUiState, alComenzar: () -> Unit) {
    TarjetaPhysio {
        Text("Cómo se hace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!estado.hayBarometro) {
            Text("Este dispositivo no tiene barómetro, así que no puede medir cuánto subes.")
        } else {
            Text("1. Párate al pie de la escalera con el teléfono en la mano o en el bolsillo.")
            Text("2. Toca «Comenzar» y quédate quieto 3 segundos mientras se calibra.")
            Text("3. Sube a tu ritmo, apoyándote en el barandal. Si sientes dolor, detente.")
            Text("4. Al llegar arriba, toca «Terminar».")
            Text(
                "El barómetro mide la presión del aire, que baja unos 0.12 hPa por cada metro que subes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Etiqueta(
                if (estado.hayBarometro) "Barómetro listo" else "Sin barómetro",
                if (estado.hayBarometro) ColoresPhysio.Exito else ColoresPhysio.Pendiente,
            )
            Etiqueta(
                if (estado.hayContadorPasos) "Contador de pasos" else "Sin contador de pasos",
                if (estado.hayContadorPasos) ColoresPhysio.Exito else ColoresPhysio.TextoSecundario,
            )
        }
    }
    Button(
        onClick = alComenzar,
        enabled = estado.hayBarometro,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
    ) {
        Text("Comenzar", fontWeight = FontWeight.Bold)
    }
    if (estado.historial.isNotEmpty()) Historial(estado.historial)
}

@Composable
private fun BloqueSubiendo(estado: EscalerasUiState, alTerminar: () -> Unit) {
    TarjetaPhysio(borde = ColoresPhysio.Turquesa) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(metros(estado.subida), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = ColoresPhysio.Turquesa)
            Text("m subidos", style = MaterialTheme.typography.titleMedium)
        }
        Text("Tiempo: ${reloj(estado.segundos)}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Altura actual: ${metros(estado.alturaActual)} m respecto del inicio",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TarjetaDato("${estado.pisos}", if (estado.pisos == 1) "piso" else "pisos", ColoresPhysio.AzulClaro, Modifier.weight(1f))
        TarjetaDato("${estado.escalones}", "escalones aprox.", ColoresPhysio.Violeta, Modifier.weight(1f))
        TarjetaDato(estado.pasos?.toString() ?: "—", "pasos", ColoresPhysio.Exito, Modifier.weight(1f))
    }
    Button(
        onClick = alTerminar,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
    ) {
        Text("Terminar", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BloqueResumen(estado: EscalerasUiState, alRepetir: () -> Unit, alRegresar: () -> Unit) {
    if (estado.guardando) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ColoresPhysio.Turquesa)
        }
        return
    }
    val r = estado.resultado ?: return
    TarjetaPhysio(borde = ColoresPhysio.Exito) {
        Text("Resumen de la prueba", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Subiste ${metros(r.metrosSubidos)} m · ${r.pisos} ${if (r.pisos == 1) "piso" else "pisos"} · unos ${r.escalonesEstimados} escalones")
        Text("Tiempo: ${reloj(r.duracionSegundos)} · ritmo ${metros(r.metrosPorMinuto)} m por minuto")
        r.pasos?.let { Text("Pasos: $it") }
        estado.mejorAnterior?.let { mejor ->
            val mejoro = r.metrosPorMinuto > mejor.metrosPorMinuto
            Etiqueta(
                if (mejoro) "¡Tu mejor ritmo hasta ahora!" else "Tu mejor ritmo: ${metros(mejor.metrosPorMinuto)} m/min",
                if (mejoro) ColoresPhysio.Exito else ColoresPhysio.AzulClaro,
            )
        }
        Text(
            when (estado.enviado) {
                true -> "Se envió a tu fisioterapeuta."
                else -> "Se guardó en el teléfono. Se enviará a la clínica cuando haya conexión."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = alRepetir, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Otra prueba") }
        Button(
            onClick = alRegresar,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
        ) {
            Text("Listo", fontWeight = FontWeight.Bold)
        }
    }
    if (estado.historial.isNotEmpty()) Historial(estado.historial)
}

/** Últimas pruebas guardadas en el teléfono: así se ve el avance semana a semana. */
@Composable
private fun Historial(pruebas: List<PruebaEscaleras>) {
    TarjetaPhysio {
        Text("Tus pruebas anteriores", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        pruebas.take(5).forEach { p ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatearFechaHora(p.medidoEn), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    "${metros(p.metrosSubidos)} m en ${reloj(p.duracionSegundos)} · ${metros(p.metrosPorMinuto)} m/min",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColoresPhysio.Turquesa,
                )
            }
        }
    }
}

/** 6.04 -> "6.0" (un decimal, sin depender del idioma del sistema). */
private fun metros(valor: Float): String {
    val decimas = (valor * 10).roundToInt()
    val signo = if (decimas < 0) "-" else ""
    val absoluto = kotlin.math.abs(decimas)
    return "$signo${absoluto / 10}.${absoluto % 10}"
}

/** 75 -> "01:15" */
private fun reloj(segundos: Int): String =
    "${(segundos / 60).toString().padStart(2, '0')}:${(segundos % 60).toString().padStart(2, '0')}"
