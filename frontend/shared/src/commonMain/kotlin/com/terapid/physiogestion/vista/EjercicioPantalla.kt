package com.terapid.physiogestion.vista

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.MetodoMedicion
import com.terapid.physiogestion.plataforma.camara.VistaCamaraPostura
import com.terapid.physiogestion.servicio.movimiento.mensaje
import com.terapid.physiogestion.servicio.postura.ArticulacionMedida
import com.terapid.physiogestion.servicio.postura.AvisoCamara
import com.terapid.physiogestion.servicio.postura.MotivoDescarte
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

            // La cámara queda fuera del «when» para no reiniciarse al pasar de calibrar a medir.
            if (estado.mostrarCamara) PanelCamara(estado, viewModel)

            when (estado.fase) {
                FaseMedicion.PREPARADO -> BloquePreparado(estado, viewModel)
                FaseMedicion.CALIBRANDO -> if (estado.usaCamara) BloqueEsperandoCamara(estado, viewModel::reintentar) else BloqueCalibrando()
                FaseMedicion.MIDIENDO -> BloqueMidiendo(estado, viewModel)
                FaseMedicion.TERMINADO -> BloqueTerminado(estado, viewModel::reintentar) { alTerminar(estado.objetivoCumplido) }
            }
            DetalleTecnico(estado.detalleTecnico)
        }
    }
}

@Composable
private fun BloquePreparado(estado: EjercicioUiState, viewModel: EjercicioViewModel) {
    val alComenzar = viewModel::comenzar
    if (estado.camaraDisponible) {
        Text("¿Cómo quieres medirlo?", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OpcionMetodo(
                titulo = "Sensores",
                descripcion = "Teléfono sujeto al cuerpo",
                seleccionada = !estado.usaCamara,
                modifier = Modifier.weight(1f),
                alElegir = { viewModel.elegirMetodo(MetodoMedicion.SENSORES) },
            )
            OpcionMetodo(
                titulo = "Cámara",
                descripcion = "Verificado · teléfono apoyado",
                seleccionada = estado.usaCamara,
                modifier = Modifier.weight(1f),
                alElegir = { viewModel.elegirMetodo(MetodoMedicion.CAMARA) },
            )
        }
    }
    TarjetaPhysio {
        Text("Antes de empezar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (estado.usaCamara) {
            val partes = estado.ejercicio?.let { ArticulacionMedida.deZona(it.zonaCorporal)?.partesVisibles } ?: "articulación"
            Text("1. Apoya el teléfono en vertical, a unos 2 metros y a la altura de tu cadera, con la pantalla hacia ti.")
            Text("2. Colócate DE LADO a la cámara: deben verse tu $partes.")
            Text("3. Quédate quieto en la posición inicial; la medición empieza sola cuando te vea completo.")
            Text("4. No toques el teléfono: si se mueve, esa repetición no cuenta.")
            Text(
                "La imagen se analiza dentro del teléfono: no se graba ni se envía ningún video.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (estado.modoManual) {
            Text("Este dispositivo no tiene sensores de movimiento. Haz el ejercicio y suma cada repetición con el botón.")
        } else {
            Text("1. Sujeta el teléfono a la parte del cuerpo que vas a mover (por ejemplo, el muslo) con la pantalla hacia afuera.")
            Text("2. Quédate quieto unos segundos mientras se calibra el sensor.")
            Text("3. Haz cada repetición a ritmo constante: llega al ángulo indicado y regresa a la posición inicial.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (estado.usaCamara) Etiqueta("Cámara lista", ColoresPhysio.Exito)
            Etiqueta(
                if (estado.hayAcelerometro) "Acelerómetro listo" else "Sin acelerómetro",
                if (estado.hayAcelerometro) ColoresPhysio.Exito else ColoresPhysio.Pendiente,
            )
            if (estado.hayAcelerometro && !estado.usaCamara) {
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
        Text(
            when {
                estado.usaCamara -> "Comenzar con cámara"
                estado.modoManual -> "Comenzar conteo manual"
                else -> "Comenzar"
            },
            fontWeight = FontWeight.Bold,
        )
    }
    if (estado.usaCamara) {
        TextButton(onClick = viewModel::alternarCamara, modifier = Modifier.fillMaxWidth()) {
            Text(if (estado.usarFrontal) "Usar la cámara trasera" else "Usar la cámara frontal")
        }
    }
}

/** Una de las dos formas de medir, como tarjeta seleccionable. */
@Composable
private fun OpcionMetodo(
    titulo: String,
    descripcion: String,
    seleccionada: Boolean,
    modifier: Modifier,
    alElegir: () -> Unit,
) {
    Surface(
        onClick = alElegir,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (seleccionada) ColoresPhysio.Turquesa.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, if (seleccionada) ColoresPhysio.Turquesa else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold, color = if (seleccionada) ColoresPhysio.Turquesa else MaterialTheme.colorScheme.onSurface)
            Text(descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * La cámara con el esqueleto encima, el contador en grande (se lee a dos
 * metros) y una franja con lo que la app necesita del paciente.
 */
@Composable
private fun PanelCamara(estado: EjercicioUiState, viewModel: EjercicioViewModel) {
    val articulacion = estado.ejercicio?.let { ArticulacionMedida.deZona(it.zonaCorporal) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(16.dp)).background(Color.Black),
    ) {
        VistaCamaraPostura(
            modifier = Modifier.fillMaxSize(),
            usarFrontal = estado.usarFrontal,
            puntosResaltados = estado.puntosResaltados,
            alDetectar = viewModel::procesarPostura,
        )
        if (estado.fase == FaseMedicion.MIDIENDO) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text("${estado.repeticiones}", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = ColoresPhysio.Turquesa)
                Text("de ${estado.objetivo}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("${estado.anguloActual.toInt()}°", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = estado.rango.color)
            }
        }
        if (!estado.telefonoQuieto) {
            Text(
                "Teléfono en movimiento",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    .background(ColoresPhysio.Pendiente, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        val bien = estado.avisoCamara == AvisoCamara.NINGUNO && estado.fase == FaseMedicion.MIDIENDO
        Text(
            textoAviso(estado.avisoCamara, estado.fase, articulacion),
            color = Color.Black,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)
                .background((if (bien) ColoresPhysio.Turquesa else ColoresPhysio.Pendiente).copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                .padding(10.dp),
        )
    }
}

private fun textoAviso(aviso: AvisoCamara, fase: FaseMedicion, articulacion: ArticulacionMedida?): String {
    val partes = articulacion?.partesVisibles ?: "articulación"
    return when (aviso) {
        AvisoCamara.NINGUNO -> if (fase == FaseMedicion.MIDIENDO) "Te veo completo: haz el ejercicio" else "Te veo: quédate quieto"
        AvisoCamara.NO_TE_VEO -> "No te veo completo: que se vean tu $partes"
        AvisoCamara.TELEFONO_MOVIDO -> "El teléfono se movió: déjalo apoyado (esa repetición no cuenta)"
        AvisoCamara.QUEDATE_QUIETO -> "Quédate quieto en la posición inicial…"
        AvisoCamara.PONTE_DERECHO -> "Párate derecho para empezar"
        AvisoCamara.PONTE_DE_LADO -> "Ponte de lado a la cámara: de frente no se puede medir el ángulo"
    }
}

/** Calibración con cámara: la vista de la cámara ya está arriba; aquí solo se explica y se puede cancelar. */
@Composable
private fun BloqueEsperandoCamara(estado: EjercicioUiState, alCancelar: () -> Unit) {
    TarjetaPhysio {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = ColoresPhysio.Turquesa, modifier = Modifier.size(28.dp))
            Column {
                Text("Preparando la verificación", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (estado.telefonoQuieto) "Empieza sola cuando te vea completo y quieto." else "Apoya el teléfono y no lo muevas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    OutlinedButton(onClick = alCancelar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancelar") }
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
        if (estado.usaCamara) Etiqueta("Verificando con cámara", ColoresPhysio.Exito)

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
        estado.verificacion?.let { v ->
            Etiqueta("Verificado con cámara", ColoresPhysio.Exito)
            Text("Repeticiones completas (90 % del ángulo o más): ${v.repeticionesCompletas} de ${v.repeticiones}")
            Text("Se te vio completo el ${v.porcentajeVisible} % del tiempo")
            if (v.totalDescartadas > 0) {
                Text(
                    "No contaron: " + v.descartadas.entries.joinToString(" · ") { (motivo, n) -> "$n ${motivo.texto()}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = ColoresPhysio.Pendiente,
                )
            }
        }
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

private fun MotivoDescarte.texto(): String = when (this) {
    MotivoDescarte.SE_DEJO_DE_VER -> "porque saliste del cuadro"
    MotivoDescarte.TELEFONO_MOVIDO -> "porque se movió el teléfono"
    MotivoDescarte.DEMASIADO_RAPIDA -> "por ser demasiado rápidas"
}

/** 75 -> "01:15" */
private fun formatoReloj(segundos: Int): String {
    val m = segundos / 60
    val s = segundos % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
