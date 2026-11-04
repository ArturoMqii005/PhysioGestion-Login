package com.terapid.physiogestion.vista.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terapid.physiogestion.vista.tema.ColoresPhysio
import com.terapid.physiogestion.vista.tema.Movimiento
import com.terapid.physiogestion.vista.tema.presionable
import kotlin.math.abs

/*
 * PIEZAS DEL DISEÑO v1.5: portada con escaleras, botones de píldora, carrusel,
 * tarjetas tipo «episodio» y anillos. Todo se dibuja con Compose (sin imágenes),
 * así pesa poco y se ve nítido en cualquier pantalla.
 */

fun mezcla(a: Color, b: Color, cuanto: Float): Color = lerp(a, b, cuanto)

// ======================================================================= COLORES POR ZONA DEL CUERPO

/** Cada zona del cuerpo tiene su color, igual en la portada del ejercicio, la rutina y el inicio. */
data class ColoresZona(val fondo: Color, val numero: Color, val tinta: Color)

fun coloresDeZona(zona: String): ColoresZona {
    val z = zona.lowercase()
    return when {
        "rodilla" in z -> ColoresZona(ColoresPhysio.TinteVioleta, Color(0xFF4A33A3), ColoresPhysio.Lila)
        "cadera" in z || "glúteo" in z || "gluteo" in z -> ColoresZona(ColoresPhysio.FondoDurazno, Color(0xFF6B3B26), ColoresPhysio.Durazno)
        "tobillo" in z || "pie" in z || "pantorrilla" in z -> ColoresZona(ColoresPhysio.FondoExito, Color(0xFF23573B), ColoresPhysio.Exito)
        "muslo" in z || "isquio" in z || "pierna" in z -> ColoresZona(ColoresPhysio.FondoAzul, Color(0xFF2B4275), ColoresPhysio.AzulClaro)
        "hombro" in z || "brazo" in z || "codo" in z -> ColoresZona(Color(0xFF331A33), Color(0xFF5C2E5C), Color(0xFFF0A6F0))
        else -> ColoresZona(ColoresPhysio.Elevada, Color(0xFF4A4560), ColoresPhysio.Lavanda)
    }
}

// ======================================================================= PORTADA

/**
 * Ilustración de la meta: tres escaleras en capas que suben hacia una bandera, con
 * un punto blanco que marca dónde va el paciente. [avance] (0 a 1) mueve ese punto
 * por los escalones; abajo se desvanece hacia el fondo para que el texto se lea.
 */
@Composable
fun PortadaEscaleras(colorBase: Color, modifier: Modifier = Modifier, avance: Float = 0.55f, conPaciente: Boolean = true) {
    val base by animateColorAsState(colorBase, Movimiento.resorte(), label = "colorPortada")
    val punto by animateFloatAsState(avance.coerceIn(0f, 1f), Movimiento.resorteProgreso(), label = "avancePortada")
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / 390f
            val sy = size.height / 380f
            fun escalera(puntos: List<Pair<Float, Float>>, color: Color) {
                val p = Path()
                puntos.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x * sx, y * sy) else p.lineTo(x * sx, y * sy) }
                p.close()
                drawPath(p, color)
            }
            drawRect(mezcla(base, ColoresPhysio.Fondo, 0.86f))
            drawCircle(mezcla(base, ColoresPhysio.Fondo, 0.7f), radius = 54f * sx, center = Offset(300f * sx, 96f * sy))
            escalera(
                listOf(-10f to 380f, -10f to 300f, 60f to 300f, 60f to 258f, 130f to 258f, 130f to 216f, 200f to 216f, 200f to 174f,
                    270f to 174f, 270f to 132f, 340f to 132f, 340f to 90f, 400f to 90f, 400f to 380f),
                mezcla(base, ColoresPhysio.Fondo, 0.62f),
            )
            escalera(
                listOf(-10f to 380f, -10f to 330f, 70f to 330f, 70f to 280f, 150f to 280f, 150f to 230f, 230f to 230f, 230f to 180f,
                    310f to 180f, 310f to 130f, 400f to 130f, 400f to 380f),
                mezcla(base, ColoresPhysio.Fondo, 0.38f),
            )
            escalera(
                listOf(-10f to 380f, -10f to 360f, 85f to 360f, 85f to 305f, 180f to 305f, 180f to 250f, 275f to 250f, 275f to 195f,
                    400f to 195f, 400f to 380f),
                base,
            )
            // Bandera de la meta en lo más alto.
            drawLine(Color.White, Offset(366f * sx, 90f * sy), Offset(366f * sx, 56f * sy), strokeWidth = 2.5f * sx, cap = StrokeCap.Round)
            val bandera = Path().apply {
                moveTo(366f * sx, 56f * sy); lineTo(388f * sx, 63f * sy); lineTo(366f * sx, 70f * sy); close()
            }
            drawPath(bandera, Color.White)
            // El paciente sube por la escalera del fondo hacia la bandera. Va por la parte alta
            // del dibujo para que nunca quede debajo del título de la portada.
            if (!conPaciente) return@Canvas
            val escalones = listOf(95f to 258f, 165f to 216f, 235f to 174f, 305f to 132f, 352f to 90f)
            val pos = punto * (escalones.size - 1)
            val i = pos.toInt().coerceAtMost(escalones.size - 2)
            val f = pos - i
            val x = escalones[i].first + (escalones[i + 1].first - escalones[i].first) * f
            val y = escalones[i].second + (escalones[i + 1].second - escalones[i].second) * f - 16f
            drawCircle(Color.White.copy(alpha = 0.16f), radius = 20f * sx, center = Offset(x * sx, y * sy))
            drawCircle(Color.White, radius = 11f * sx, center = Offset(x * sx, y * sy))
            drawCircle(base, radius = 5f * sx, center = Offset(x * sx, y * sy))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to ColoresPhysio.Fondo),
            ),
        )
    }
}

// ======================================================================= BOTONES

/** Botón principal en forma de píldora: blanco sobre el fondo oscuro (o violeta con [violeta]). */
@Composable
fun BotonPildora(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = Iconos.Reproducir,
    violeta: Boolean = false,
    habilitado: Boolean = true,
    /** Versión baja y angosta para dentro de una tarjeta (dos botones lado a lado). */
    compacta: Boolean = false,
) {
    val fondo = if (violeta) ColoresPhysio.Violeta else Color.White
    val tinta = if (violeta) Color.White else ColoresPhysio.Fondo
    Row(
        modifier
            .heightIn(min = if (compacta) 44.dp else 54.dp)
            .clip(RoundedCornerShape(50))
            .background(if (habilitado) fondo else ColoresPhysio.SuperficieAlta)
            .presionable(habilitado = habilitado, alPulsar = alPulsar)
            .padding(horizontal = if (compacta) 12.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compacta) 6.dp else 10.dp, Alignment.CenterHorizontally),
    ) {
        if (icono != null) Icono(icono, null, tamano = if (compacta) 16.dp else 18.dp, color = if (habilitado) tinta else ColoresPhysio.TextoTenue)
        Text(
            texto,
            color = if (habilitado) tinta else ColoresPhysio.TextoTenue,
            fontSize = if (compacta) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Botón redondo de un solo ícono (con su descripción para el lector de pantalla). */
@Composable
fun BotonRedondo(
    icono: ImageVector,
    descripcion: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    tamano: Dp = 54.dp,
    fondo: Color = ColoresPhysio.SuperficieAlta,
    colorIcono: Color = Color.White,
    insignia: Int = 0,
) {
    Box(modifier.size(tamano)) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(fondo)
                .presionable(escala = 0.9f, etiqueta = descripcion, alPulsar = alPulsar)
                .semantics { contentDescription = descripcion },
            contentAlignment = Alignment.Center,
        ) {
            Icono(icono, null, tamano = tamano * 0.42f, color = colorIcono)
        }
        if (insignia > 0) Insignia(insignia, Modifier.align(Alignment.TopEnd))
    }
}

/** Numerito rojo de pendientes (avisos sin leer). */
@Composable
fun Insignia(n: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 18.dp).width(if (n > 9) 24.dp else 18.dp).clip(RoundedCornerShape(50)).background(ColoresPhysio.AlertaRelleno),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (n > 99) "99+" else "$n", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ======================================================================= CARRUSEL

/** Puntos del carrusel: el actual se alarga y se mueve junto con el dedo. */
@Composable
fun PuntosCarrusel(estado: PagerState, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "Página ${estado.currentPage + 1} de ${estado.pageCount}" }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(estado.pageCount) { i ->
            val distancia = abs((estado.currentPage + estado.currentPageOffsetFraction) - i).coerceAtMost(1f)
            val ancho = 7.dp + 19.dp * (1f - distancia)
            Box(Modifier.size(width = ancho, height = 7.dp).clip(RoundedCornerShape(50)).background(mezcla(Color.White, ColoresPhysio.BordeFuerte, distancia)))
        }
    }
}

// ======================================================================= TEXTO Y CONTENEDORES

/** Título de sección con flecha, como los estantes de las apps de Apple. */
@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier, alPulsar: (() -> Unit)? = null) {
    Row(
        modifier.then(if (alPulsar != null) Modifier.presionable(escala = 0.98f, alPulsar = alPulsar) else Modifier).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(texto, style = MaterialTheme.typography.titleLarge, color = ColoresPhysio.Texto)
        if (alPulsar != null) Icono(Iconos.Adelante, null, tamano = 18.dp, color = ColoresPhysio.TextoTenue)
    }
}

/** Etiqueta con borde fino (Semana 3 de 6, 4 ejercicios…). */
@Composable
fun Chip(texto: String, modifier: Modifier = Modifier, color: Color = ColoresPhysio.Texto, relleno: Color = Color.Transparent) {
    Text(
        texto,
        modifier
            .clip(RoundedCornerShape(50))
            .background(relleno)
            .border(1.dp, if (relleno == Color.Transparent) ColoresPhysio.BordeFuerte else Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/** Tarjeta oscura con borde sutil; con [alPulsar] se encoge al tocarla. */
@Composable
fun TarjetaSuave(
    modifier: Modifier = Modifier,
    fondo: Color = ColoresPhysio.Superficie,
    borde: Color = ColoresPhysio.Borde,
    alPulsar: (() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(if (alPulsar != null) Modifier.presionable(escala = 0.98f, alPulsar = alPulsar) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = fondo,
        border = BorderStroke(1.dp, borde),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = contenido)
    }
}

// ======================================================================= TARJETA TIPO EPISODIO

/**
 * Un ejercicio presentado como episodio: «portada» con el número grande y la zona,
 * barra de avance abajo y el nombre debajo. Se usa en el inicio y en la rutina.
 */
@Composable
fun TarjetaEpisodio(
    numero: Int,
    zona: String,
    titulo: String,
    detalle: String,
    metodo: String?,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    ancho: Dp = 236.dp,
    alto: Dp = 124.dp,
    avance: Float? = null,
    completado: Boolean = false,
    tituloDentro: Boolean = false,
) {
    val colores = coloresDeZona(zona)
    val barra by animateFloatAsState(avance ?: 0f, Movimiento.resorteProgreso(), label = "avanceEpisodio")
    Column(modifier.width(ancho).presionable(escala = 0.97f, etiqueta = titulo, alPulsar = alPulsar), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(alto).clip(RoundedCornerShape(16.dp)).background(colores.fondo)) {
            Text(
                numero.toString().padStart(2, '0'),
                Modifier.align(Alignment.TopEnd).padding(end = 12.dp),
                fontSize = (alto.value * 0.58f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
                color = colores.numero,
            )
            Text(
                zona,
                Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                color = ColoresPhysio.Texto,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            if (completado) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp).size(26.dp).clip(CircleShape).background(colores.tinta),
                    contentAlignment = Alignment.Center,
                ) { Icono(Iconos.Check, "Hecho hoy", tamano = 16.dp, color = colores.fondo) }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tituloDentro) {
                    Text(titulo, color = ColoresPhysio.Texto, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (metodo != null) Text(metodo, color = colores.tinta, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (avance != null && !completado) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(barra).background(Color.White))
                }
            }
        }
        if (!tituloDentro) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, color = ColoresPhysio.Texto, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detalle, color = ColoresPhysio.TextoSecundario, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ======================================================================= ANILLO

/** Anillo de avance (repeticiones): crece con un resorte cada vez que cambia [avance]. */
@Composable
fun AnilloAvance(
    avance: Float,
    modifier: Modifier = Modifier,
    grosor: Dp = 22.dp,
    color: Color = ColoresPhysio.Violeta,
    pista: Color = ColoresPhysio.SuperficieAlta,
    centro: @Composable BoxScope.() -> Unit,
) {
    val valor by animateFloatAsState(avance.coerceIn(0f, 1f), Movimiento.resorteProgreso(), label = "anillo")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val g = grosor.toPx()
            val lado = size.minDimension - g
            val esquina = Offset((size.width - lado) / 2, (size.height - lado) / 2)
            drawCircle(ColoresPhysio.Superficie, radius = lado / 2)
            drawArc(pista, 0f, 360f, false, esquina, Size(lado, lado), style = Stroke(g))
            if (valor > 0f) drawArc(color, -90f, 360f * valor, false, esquina, Size(lado, lado), style = Stroke(g, cap = StrokeCap.Round))
        }
        centro()
    }
}

/** Barra horizontal con marca de meta (ángulo de la articulación). */
@Composable
fun BarraConMeta(valor: Float, meta: Float?, maximo: Float, modifier: Modifier = Modifier, color: Color = ColoresPhysio.Violeta) {
    val v by animateFloatAsState((valor / maximo).coerceIn(0f, 1f), Movimiento.resorteProgreso(), label = "barraMeta")
    Box(modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(ColoresPhysio.SuperficieAlta)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(v).clip(RoundedCornerShape(50)).background(color))
        }
        if (meta != null) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width * (meta / maximo).coerceIn(0f, 1f)
                drawLine(ColoresPhysio.Texto, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

/** Pestaña o fila que se puede tocar con su rol (para TalkBack). */
fun Modifier.tocable(etiqueta: String, alPulsar: () -> Unit, rol: Role = Role.Button): Modifier =
    presionable(escala = 0.98f, etiqueta = etiqueta, rol = rol, alPulsar = alPulsar)
