package com.terapid.physiogestion.vista

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.servicio.EstadoDia
import com.terapid.physiogestion.servicio.PuntoDolor
import com.terapid.physiogestion.servicio.PuntoRitmo
import com.terapid.physiogestion.viewmodel.ProgresoViewModel
import com.terapid.physiogestion.vista.componentes.BotonPildora
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.Chip
import com.terapid.physiogestion.vista.componentes.Iconos
import com.terapid.physiogestion.vista.componentes.TarjetaSuave
import com.terapid.physiogestion.vista.componentes.TituloSeccion
import com.terapid.physiogestion.vista.componentes.mezcla
import com.terapid.physiogestion.vista.tema.ColoresPhysio
import com.terapid.physiogestion.vista.tema.Movimiento

/**
 * VISTA «Tu progreso» del paciente: cómo cambia su dolor, su ritmo en las
 * escaleras y su constancia. Las gráficas se dibujan con Canvas y aparecen
 * con una animación (la línea se traza, las barras crecen).
 */
@Composable
fun ProgresoPantalla(viewModel: ProgresoViewModel, alAbrirHistorial: () -> Unit, alAbrirEscaleras: () -> Unit) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.cargar() }
    Surface(Modifier.fillMaxSize(), color = ColoresPhysio.Fondo) {
        LazyColumn(
            Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tu progreso", style = MaterialTheme.typography.headlineLarge, color = ColoresPhysio.Texto)
                    Text(
                        if (e.sinConexion) "Sin conexión: con lo guardado en este teléfono" else "Tus últimas sesiones y pruebas",
                        color = if (e.sinConexion) ColoresPhysio.Pendiente else ColoresPhysio.TextoSecundario,
                        fontSize = 14.sp,
                    )
                }
            }
            if (e.cargando) {
                item { Cargando("Calculando tu progreso…") }
                return@LazyColumn
            }
            item { TarjetaDolor(e.dolor, e.textoDolor, e.umbralDolor) }
            item { TarjetaRitmo(e.ritmo, e.mejoraRitmo, alAbrirEscaleras) }
            item { TarjetaConstancia(e.calendario, e.diasConSesion, e.diasTranscurridos) }
            item { BotonPildora("Ver mi historial completo", alAbrirHistorial, Modifier.fillMaxWidth(), icono = Iconos.Reloj) }
        }
    }
}

// ======================================================================= DOLOR

@Composable
private fun TarjetaDolor(serie: List<PuntoDolor>, texto: String, umbral: Int?) {
    val trazo = remember(serie) { Animatable(0f) }
    LaunchedEffect(serie) { trazo.animateTo(1f, tween(900, easing = Movimiento.CurvaIos)) }
    TarjetaSuave {
        Text("Dolor al terminar la sesión", color = ColoresPhysio.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(texto, style = MaterialTheme.typography.headlineSmall, color = ColoresPhysio.Texto)
        if (serie.size >= 2) {
            val descripcion = "Gráfica de dolor: " + serie.joinToString(", ") { "${it.dolor}" }
            Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = descripcion }) {
                val izq = 8.dp.toPx()
                val der = size.width - 8.dp.toPx()
                val arriba = 10.dp.toPx()
                val abajo = size.height - 8.dp.toPx()
                fun y(v: Int) = abajo - (abajo - arriba) * v / 10f
                val paso = (der - izq) / (serie.size - 1)
                drawLine(ColoresPhysio.Borde, Offset(izq, abajo), Offset(der, abajo), 1.dp.toPx())
                drawLine(ColoresPhysio.Borde, Offset(izq, y(5)), Offset(der, y(5)), 1.dp.toPx())
                if (umbral != null) {
                    drawLine(
                        ColoresPhysio.AlertaRelleno, Offset(izq, y(umbral)), Offset(der, y(umbral)), 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
                    )
                }
                val linea = Path()
                val area = Path()
                serie.forEachIndexed { i, p ->
                    val x = izq + paso * i
                    if (i == 0) { linea.moveTo(x, y(p.dolor)); area.moveTo(x, abajo); area.lineTo(x, y(p.dolor)) } else { linea.lineTo(x, y(p.dolor)); area.lineTo(x, y(p.dolor)) }
                }
                area.lineTo(der, abajo); area.close()
                // La línea «se dibuja» de izquierda a derecha al entrar a la pantalla.
                clipRect(right = izq + (der - izq) * trazo.value + 8.dp.toPx()) {
                    drawPath(area, ColoresPhysio.TinteVioleta.copy(alpha = 0.7f))
                    drawPath(linea, ColoresPhysio.Lila, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    serie.forEachIndexed { i, p ->
                        val ultimo = i == serie.lastIndex
                        drawCircle(if (ultimo) ColoresPhysio.Lila else ColoresPhysio.Superficie, if (ultimo) 6.dp.toPx() else 4.dp.toPx(), Offset(izq + paso * i, y(p.dolor)))
                        drawCircle(ColoresPhysio.Lila, if (ultimo) 6.dp.toPx() else 4.dp.toPx(), Offset(izq + paso * i, y(p.dolor)), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(fechaCorta(serie.first().fecha), color = ColoresPhysio.TextoTenue, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (umbral != null) Text("Tu límite: $umbral", color = ColoresPhysio.Alerta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(fechaCorta(serie.last().fecha), color = ColoresPhysio.TextoTenue, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
    }
}

// ======================================================================= ESCALERAS

@Composable
private fun TarjetaRitmo(serie: List<PuntoRitmo>, mejora: Int?, alAbrirEscaleras: () -> Unit) {
    TarjetaSuave {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Prueba de escaleras · ritmo", color = ColoresPhysio.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (mejora != null) {
                Chip(
                    (if (mejora >= 0) "+" else "") + "$mejora % vs. la primera",
                    color = if (mejora >= 0) ColoresPhysio.Exito else ColoresPhysio.Pendiente,
                    relleno = if (mejora >= 0) ColoresPhysio.FondoExito else ColoresPhysio.FondoPendiente,
                )
            }
        }
        if (serie.isEmpty()) {
            Text("Todavía no haces la prueba. Toma un par de minutos y mide cuánto subes.", color = ColoresPhysio.TextoSecundario, fontSize = 14.sp)
            BotonPildora("Hacer la prueba", alAbrirEscaleras, Modifier.fillMaxWidth(), icono = Iconos.Escalera, violeta = true)
            return@TarjetaSuave
        }
        val maximo = (serie.maxOf { it.metrosPorMinuto } * 1.15f).coerceAtLeast(0.1f)
        // Las barras crecen desde abajo al entrar a la pantalla.
        val animacion = remember(serie) { Animatable(0f) }
        LaunchedEffect(serie) { animacion.animateTo(1f, tween(800, easing = Movimiento.CurvaIos)) }
        val crecer = animacion.value
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            serie.forEachIndexed { i, p ->
                val ultima = i == serie.lastIndex
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
                    Text(formatoDecimal(p.metrosPorMinuto), color = ColoresPhysio.Texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((p.metrosPorMinuto / maximo * crecer).coerceIn(0.05f, 0.82f))
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(if (ultima) ColoresPhysio.Violeta else mezcla(ColoresPhysio.Violeta, ColoresPhysio.Fondo, 0.5f - 0.4f * i / serie.size)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            serie.forEach { Text(fechaCorta(it.fecha), Modifier.weight(1f), color = ColoresPhysio.TextoTenue, fontSize = 11.sp, textAlign = TextAlign.Center) }
        }
        Text("Metros por minuto que subes, medidos con el barómetro del teléfono.", color = ColoresPhysio.TextoSecundario, fontSize = 13.sp)
    }
}

// ======================================================================= CONSTANCIA

@Composable
private fun TarjetaConstancia(calendario: List<EstadoDia>, hechos: Int, transcurridos: Int) {
    TarjetaSuave {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Constancia · 4 semanas", color = ColoresPhysio.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$hechos de $transcurridos días", color = ColoresPhysio.Texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                Text(it, Modifier.weight(1f), color = ColoresPhysio.TextoTenue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        calendario.chunked(7).forEach { semana ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                semana.forEach { d ->
                    val (fondo, borde) = when (d) {
                        EstadoDia.HECHO -> ColoresPhysio.Violeta to Color.Transparent
                        EstadoDia.HOY_HECHO -> ColoresPhysio.Violeta to ColoresPhysio.Texto
                        EstadoDia.HOY_PENDIENTE -> ColoresPhysio.SuperficieAlta to ColoresPhysio.Texto
                        EstadoDia.FALTO -> ColoresPhysio.SuperficieAlta to Color.Transparent
                        EstadoDia.FUTURO -> Color.Transparent to ColoresPhysio.BordeFuerte
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1.15f).clip(RoundedCornerShape(9.dp)).background(fondo)
                            .border(if (borde == Color.Transparent) 0.dp else 2.dp, borde, RoundedCornerShape(9.dp)),
                    )
                }
            }
        }
    }
}

private fun fechaCorta(iso: String): String = if (iso.length >= 10) "${iso.substring(8, 10)}/${iso.substring(5, 7)}" else iso

private fun formatoDecimal(v: Float): String {
    val decimas = (v * 10).toInt()
    return "${decimas / 10}.${decimas % 10}"
}
