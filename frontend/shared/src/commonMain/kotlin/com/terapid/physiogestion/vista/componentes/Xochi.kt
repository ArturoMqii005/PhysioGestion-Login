package com.terapid.physiogestion.vista.componentes

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import physiogestion.shared.generated.resources.Res
import physiogestion.shared.generated.resources.logo_xochi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terapid.physiogestion.vista.tema.ColoresPhysio
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/*
 * XOCHI, el ajolote de PhysioGestion, vestido de fisioterapeuta (filipina violeta con
 * gafete). Acompaña al paciente en su rutina como la mascota de una app para aprender
 * idiomas: explica, anima, celebra y descansa con él.
 * Se dibuja con figuras (sin imágenes), así pesa nada y se ve nítido en cualquier
 * pantalla. Los colores son los de la marca: piel lila, branquias rosas, fondo violeta.
 * La cara es la misma del logo (marca.py genera el SVG y el icono de Android).
 */

/** Cómo se ve Xochi según el momento del ejercicio. */
enum class ExpresionXochi {
    /** Saluda o explica. */
    FELIZ,

    /** Mientras el paciente hace las repeticiones: boca abierta y bracitos arriba. */
    ANIMANDO,

    /** Terminó el ejercicio o la rutina: ojos felices y salta. */
    CELEBRANDO,

    /** Esperando a la cámara o calibrando. */
    PENSANDO,

    /** Dolor alto o algo salió mal: cejas tristes. */
    PREOCUPADO,

    /** Descanso entre series: ojos cerrados y burbujas. */
    DESCANSANDO,
}

private object PaletaXochi {
    val Piel = Color(0xFFC4B3FF)
    val PielOscura = Color(0xFFA994FF)
    val Hocico = Color(0xFFE9E2FF)
    val Branquia = Color(0xFFFF8CC6)
    val BranquiaClara = Color(0xFFFFD3E8)
    val Ojo = Color(0xFF1A1430)
    val Mejilla = Color(0xFFFF8CC6)
    val Lengua = Color(0xFFFF6FAE)
    val FondoA = Color(0xFF7A57FF)
    val FondoB = Color(0xFF4A2BD0)

    // Filipina de fisioterapeuta en el violeta de la app.
    val Filipina = Color(0xFF6C47FF)
    val FilipinaClara = Color(0xFF9A80FF)
    val FilipinaOscura = Color(0xFF4F30D6)
    val Gafete = Color(0xFFFFFFFF)
}

/**
 * Xochi de cuerpo completo (diseño de 200 × 240), animado: flota, mueve las
 * branquias y parpadea. Con [animado] en false queda quieto (capturas y lectores de pantalla).
 */
@Composable
fun Xochi(
    modifier: Modifier = Modifier,
    expresion: ExpresionXochi = ExpresionXochi.FELIZ,
    tamano: Dp = 120.dp,
    animado: Boolean = true,
    soloCara: Boolean = false,
) {
    val t = rememberInfiniteTransition(label = "xochi")
    val flote by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (expresion == ExpresionXochi.CELEBRANDO) 700 else 2400, easing = LinearEasing)),
        label = "flote",
    )
    val parpadeo by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 3800
                1f at 3500
                0.1f at 3600
                1f at 3720
            },
        ),
        label = "parpadeo",
    )
    val brazos by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "brazos")
    val fase = if (animado) flote else 0f
    val ojos = if (animado && expresion != ExpresionXochi.DESCANSANDO) parpadeo else 1f
    val agitar = if (animado) brazos else 0f
    val descripcion = when (expresion) {
        ExpresionXochi.FELIZ -> "Xochi, el ajolote, sonríe"
        ExpresionXochi.ANIMANDO -> "Xochi te anima"
        ExpresionXochi.CELEBRANDO -> "Xochi celebra contigo"
        ExpresionXochi.PENSANDO -> "Xochi espera"
        ExpresionXochi.PREOCUPADO -> "Xochi se ve preocupado"
        ExpresionXochi.DESCANSANDO -> "Xochi descansa"
    }
    val alto = if (soloCara) tamano else tamano * 1.2f
    Canvas(modifier.size(tamano, alto).semantics { contentDescription = descripcion }) {
        val escala = size.width / 200f
        scale(escala, escala, pivot = Offset.Zero) {
            if (soloCara) {
                cara(expresion, ojos, fase, conBranquias = true, desplazamiento = 0f)
            } else {
                cuerpoCompleto(expresion, ojos, fase, agitar)
            }
        }
    }
}

/** Icono de la ventana de escritorio (el mismo logo, como recurso vectorial). */
@Composable
fun iconoApp(): Painter = painterResource(Res.drawable.logo_xochi)

/** Logo: la cara de Xochi sobre el cuadro violeta de la marca. */
@Composable
fun LogoXochi(modifier: Modifier = Modifier, tamano: Dp = 64.dp, animado: Boolean = false) {
    val t = rememberInfiniteTransition(label = "logo")
    val parpadeo by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 4200; 1f at 3900; 0.1f at 4000; 1f at 4120 }),
        label = "parpadeo",
    )
    Canvas(modifier.size(tamano).semantics { contentDescription = "Logo de PhysioGestion" }) {
        val e = size.width / 200f
        scale(e, e, pivot = Offset.Zero) {
            drawRoundRect(
                Brush.linearGradient(listOf(PaletaXochi.FondoA, PaletaXochi.FondoB), Offset.Zero, Offset(200f, 200f)),
                cornerRadius = CornerRadius(46f),
                size = Size(200f, 200f),
            )
            // Misma composición que logo.svg: la cara al 84 % y un poco abajo.
            translate(16f, 20f) {
                scale(0.84f, 0.84f, pivot = Offset.Zero) {
                    cara(ExpresionXochi.FELIZ, if (animado) parpadeo else 1f, 0f, conBranquias = true, desplazamiento = 0f)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ DIBUJO

private fun DrawScope.cuerpoCompleto(expresion: ExpresionXochi, ojos: Float, fase: Float, agitar: Float) {
    val celebra = expresion == ExpresionXochi.CELEBRANDO
    val ondulacion = sin(fase * 2 * PI).toFloat()
    // Flota en el agua; al celebrar, salta.
    val dy = if (celebra) -kotlin.math.abs(ondulacion) * 14f else ondulacion * 4f
    // Sombra en el suelo (se encoge cuando salta).
    val sombra = 1f - (-dy / 30f)
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(100f - 46f * sombra, 226f), Size(92f * sombra, 10f))

    translate(0f, dy) {
        // Cola: una curva gruesa que sale a la derecha.
        val cola = Path().apply {
            moveTo(128f, 196f)
            cubicTo(168f, 204f, 186f, 188f, 192f, 166f + ondulacion * 4f)
            cubicTo(180f, 196f, 160f, 214f, 122f, 214f)
            close()
        }
        drawPath(cola, PaletaXochi.PielOscura)
        // Patitas.
        drawOval(PaletaXochi.PielOscura, Offset(68f, 208f), Size(26f, 16f))
        drawOval(PaletaXochi.PielOscura, Offset(106f, 208f), Size(26f, 16f))
        // Cuerpo y panza.
        drawOval(PaletaXochi.Piel, Offset(56f, 140f), Size(88f, 80f))
        drawOval(PaletaXochi.Hocico, Offset(74f, 160f), Size(52f, 54f), alpha = 0.9f)
        filipina()
        // Bracitos (0° = colgando; positivo gira hacia afuera en el izquierdo y hacia adentro en el derecho).
        val arriba = expresion == ExpresionXochi.ANIMANDO || celebra
        val izquierdo = Offset(62f, 166f)
        val derecho = Offset(138f, 166f)
        if (!arriba) {
            brazo(izquierdo, 22f)
            if (expresion != ExpresionXochi.PENSANDO) brazo(derecho, -22f)
        }
        // Cabeza encima del cuerpo.
        translate(0f, -18f) { cara(expresion, ojos, fase, conBranquias = true, desplazamiento = ondulacion) }
        // Arriba, los brazos van delante de la cabeza para que se vean (y se agitan al celebrar).
        if (arriba) {
            val a = 150f + agitar * 12f
            brazo(izquierdo, a)
            brazo(derecho, -a)
        }
        if (expresion == ExpresionXochi.PENSANDO) brazo(derecho, 158f) // la mano en la barbilla
        if (expresion == ExpresionXochi.DESCANSANDO) burbujas(fase)
    }
}

/** Brazo de Xochi desde el hombro [hombro], girado [grados] (0 = colgando). */
private fun DrawScope.brazo(hombro: Offset, grados: Float) {
    rotate(grados, pivot = hombro) {
        drawRoundRect(PaletaXochi.PielOscura, Offset(hombro.x - 8f, hombro.y), Size(16f, 34f), CornerRadius(8f))
        drawCircle(PaletaXochi.Piel, 9f, Offset(hombro.x, hombro.y + 32f))
        // Manga corta de la filipina.
        drawRoundRect(PaletaXochi.Filipina, Offset(hombro.x - 10f, hombro.y - 6f), Size(20f, 20f), CornerRadius(7f))
        drawLine(PaletaXochi.FilipinaClara, Offset(hombro.x - 9f, hombro.y + 12f), Offset(hombro.x + 9f, hombro.y + 12f), 2.5f, StrokeCap.Round)
    }
}

/**
 * Uniforme de fisioterapeuta: filipina con cuello en V, bolsillo con pluma y gafete
 * con la cruz de salud. Se recorta a la forma del cuerpo; abajo asoma la pancita.
 */
private fun DrawScope.filipina() {
    val cuerpo = Path().apply { addOval(Rect(56f, 140f, 144f, 220f)) }
    clipPath(cuerpo) {
        drawRect(PaletaXochi.Filipina, Offset(50f, 136f), Size(100f, 68f))
        // Dobladillo.
        drawRect(PaletaXochi.FilipinaOscura, Offset(50f, 200f), Size(100f, 5f))
        // Costura central bajo el cuello.
        drawLine(PaletaXochi.FilipinaOscura, Offset(100f, 172f), Offset(100f, 200f), 1.5f)
    }
    // Cuello en V: se ve la piel y lo rodea un ribete claro.
    val cuello = Path().apply { moveTo(84f, 145f); lineTo(100f, 172f); lineTo(116f, 145f); close() }
    drawPath(cuello, PaletaXochi.Piel)
    val ribete = Path().apply { moveTo(82f, 145f); lineTo(100f, 174f); lineTo(118f, 145f) }
    drawPath(ribete, PaletaXochi.FilipinaClara, style = Stroke(4f, cap = StrokeCap.Round))
    // Bolsillo con pluma (lado izquierdo de quien mira).
    drawLine(Color.White, Offset(73f, 172f), Offset(73f, 182f), 3f, StrokeCap.Round)
    drawCircle(PaletaXochi.Branquia, 2.2f, Offset(73f, 171f))
    drawRoundRect(PaletaXochi.FilipinaOscura, Offset(66f, 178f), Size(17f, 15f), CornerRadius(4f))
    drawLine(PaletaXochi.FilipinaClara, Offset(67f, 181f), Offset(82f, 181f), 1.5f)
    // Gafete con la cruz.
    drawRoundRect(PaletaXochi.Gafete, Offset(113f, 176f), Size(19f, 15f), CornerRadius(3.5f))
    drawRect(PaletaXochi.Filipina, Offset(120.5f, 179f), Size(4f, 9f))
    drawRect(PaletaXochi.Filipina, Offset(118f, 181.5f), Size(9f, 4f))
}

private fun DrawScope.burbujas(fase: Float) {
    listOf(0f, 0.33f, 0.66f).forEachIndexed { i, retraso ->
        val p = (fase + retraso) % 1f
        val x = 160f + i * 8f + sin((p + i) * 2 * PI).toFloat() * 4f
        val y = 70f - p * 60f
        drawCircle(Color.White.copy(alpha = 0.6f * (1f - p)), 4f + i * 2f, Offset(x, y), style = Stroke(2f))
    }
}

/** La cara del logo (diseño de 200 × 200). [desplazamiento] mece las branquias. */
private fun DrawScope.cara(expresion: ExpresionXochi, ojos: Float, fase: Float, conBranquias: Boolean, desplazamiento: Float) {
    if (conBranquias) {
        val vaiven = desplazamiento * 5f
        // (x base, y base, ángulo, largo, grosor) del lado izquierdo; el derecho es su espejo.
        val bases = listOf(
            floatArrayOf(48f, 90f, 38f, 42f, 17f),
            floatArrayOf(40f, 108f, 8f, 46f, 17f),
            floatArrayOf(44f, 126f, -24f, 40f, 16f),
        )
        for (lado in listOf(-1, 1)) {
            for (b in bases) {
                val bx = if (lado < 0) b[0] else 200f - b[0]
                val by = b[1]
                val angulo = (if (lado < 0) b[2] else -b[2]) + vaiven * lado
                val largo = b[3]
                val grosor = b[4]
                rotate(angulo, pivot = Offset(bx, by)) {
                    val x0 = if (lado < 0) bx - largo else bx
                    drawRoundRect(PaletaXochi.Branquia, Offset(x0, by - grosor / 2), Size(largo, grosor), CornerRadius(grosor / 2))
                    for (k in 0..2) {
                        val t = 0.38f + 0.22f * k
                        val px = if (lado < 0) bx - largo * t else bx + largo * t
                        drawCircle(PaletaXochi.Branquia, grosor * 0.3f, Offset(px, by - grosor / 2 + 1f))
                    }
                    val xr = if (lado < 0) bx - largo + grosor * 0.45f else bx + largo * 0.15f
                    drawRoundRect(
                        PaletaXochi.BranquiaClara.copy(alpha = 0.75f),
                        Offset(xr, by - grosor * 0.12f), Size(largo * 0.4f, grosor * 0.24f), CornerRadius(grosor * 0.12f),
                    )
                }
            }
        }
    }
    // Cabeza, sombra de la barbilla, hocico y brillo.
    drawOval(PaletaXochi.Piel, Offset(34f, 62f), Size(132f, 104f))
    drawOval(PaletaXochi.PielOscura.copy(alpha = 0.35f), Offset(40f, 114f), Size(120f, 52f))
    drawOval(PaletaXochi.Hocico.copy(alpha = 0.95f), Offset(58f, 110f), Size(84f, 48f))
    drawOval(Color.White.copy(alpha = 0.35f), Offset(60f, 76f), Size(36f, 16f))
    // Mejillas.
    drawOval(PaletaXochi.Mejilla.copy(alpha = 0.75f), Offset(49f, 121.5f), Size(18f, 11f))
    drawOval(PaletaXochi.Mejilla.copy(alpha = 0.75f), Offset(133f, 121.5f), Size(18f, 11f))
    ojosDe(expresion, ojos)
    bocaDe(expresion, fase)
}

private fun DrawScope.ojosDe(expresion: ExpresionXochi, abiertos: Float) {
    val trazo = Stroke(5f, cap = StrokeCap.Round)
    for (x in listOf(70f, 130f)) {
        when (expresion) {
            ExpresionXochi.CELEBRANDO -> {
                // Ojos felices: ^ ^
                val p = Path().apply { moveTo(x - 9f, 111f); quadraticTo(x, 98f, x + 9f, 111f) }
                drawPath(p, PaletaXochi.Ojo, style = trazo)
            }
            ExpresionXochi.DESCANSANDO -> {
                val p = Path().apply { moveTo(x - 9f, 106f); quadraticTo(x, 114f, x + 9f, 106f) }
                drawPath(p, PaletaXochi.Ojo, style = trazo)
            }
            else -> {
                val mira = if (expresion == ExpresionXochi.PENSANDO) Offset(3f, -4f) else Offset.Zero
                val r = if (expresion == ExpresionXochi.PREOCUPADO) 9f else 10f
                scale(1f, abiertos, pivot = Offset(x, 108f)) {
                    drawCircle(PaletaXochi.Ojo, r, Offset(x, 108f) + mira)
                    if (abiertos > 0.5f) drawCircle(Color.White, 3.6f, Offset(x + 3.5f, 104.5f) + mira)
                }
            }
        }
    }
    if (expresion == ExpresionXochi.PREOCUPADO) {
        // Cejas inclinadas hacia arriba en el centro.
        drawLine(PaletaXochi.Ojo, Offset(58f, 90f), Offset(78f, 85f), 4f, StrokeCap.Round)
        drawLine(PaletaXochi.Ojo, Offset(142f, 90f), Offset(122f, 85f), 4f, StrokeCap.Round)
    }
}

private fun DrawScope.bocaDe(expresion: ExpresionXochi, fase: Float) {
    val trazo = Stroke(5f, cap = StrokeCap.Round)
    when (expresion) {
        ExpresionXochi.FELIZ, ExpresionXochi.DESCANSANDO -> {
            val p = Path().apply { moveTo(84f, 131f); quadraticTo(100f, 145f, 116f, 131f) }
            drawPath(p, PaletaXochi.Ojo, style = trazo)
        }
        ExpresionXochi.ANIMANDO, ExpresionXochi.CELEBRANDO -> {
            // Boca abierta en forma de D con lengua.
            val abre = if (expresion == ExpresionXochi.CELEBRANDO) 20f else 14f + sin(fase * 4 * PI).toFloat() * 2f
            val boca = Path().apply {
                moveTo(82f, 128f)
                lineTo(118f, 128f)
                quadraticTo(116f, 128f + abre * 1.3f, 100f, 128f + abre)
                quadraticTo(84f, 128f + abre * 1.3f, 82f, 128f)
                close()
            }
            drawPath(boca, PaletaXochi.Ojo)
            drawOval(PaletaXochi.Lengua, Offset(91f, 128f + abre * 0.45f), Size(18f, abre * 0.5f))
        }
        ExpresionXochi.PENSANDO -> drawCircle(PaletaXochi.Ojo, 5f, Offset(104f, 134f), style = Stroke(4f))
        ExpresionXochi.PREOCUPADO -> {
            val p = Path().apply { moveTo(88f, 138f); quadraticTo(100f, 129f, 112f, 138f) }
            drawPath(p, PaletaXochi.Ojo, style = trazo)
        }
    }
}

// ------------------------------------------------------------------ GLOBO DE TEXTO

/**
 * Xochi con un globo de texto, como el personaje que explica en una lección.
 * Cuando el [mensaje] cambia, el globo aparece con una pequeña animación.
 */
@Composable
fun XochiDice(
    mensaje: String,
    modifier: Modifier = Modifier,
    expresion: ExpresionXochi = ExpresionXochi.FELIZ,
    tamano: Dp = 92.dp,
    animado: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Xochi(expresion = expresion, tamano = tamano, animado = animado)
        AnimatedContent(
            targetState = mensaje,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.85f)) togetherWith fadeOut(tween(120)) },
            label = "globo",
            modifier = Modifier.weight(1f, fill = false),
        ) { texto ->
            Globo(texto)
        }
    }
}

/** Globo blanco con una colita que apunta a Xochi. */
@Composable
private fun Globo(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp, 16.dp)) {
            val colita = Path().apply {
                moveTo(size.width, 0f)
                lineTo(0f, size.height * 0.55f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(colita, Color.White)
        }
        Box(
            Modifier.widthIn(max = 280.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(texto, color = ColoresPhysio.Fondo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
        }
    }
}

// ------------------------------------------------------------------ CONFETI

/** Lluvia de confeti para celebrar (se dibuja encima del contenido, sin bloquear toques). */
@Composable
fun Confeti(modifier: Modifier = Modifier, piezas: Int = 46) {
    val colores = listOf(ColoresPhysio.Violeta, ColoresPhysio.Lila, PaletaXochi.Branquia, ColoresPhysio.Pendiente, ColoresPhysio.Exito, ColoresPhysio.AzulClaro)
    val datos = remember(piezas) {
        val r = Random(7)
        List(piezas) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat() * 360f, 0.6f + r.nextFloat() * 0.8f, r.nextInt(colores.size).toFloat()) }
    }
    val t = rememberInfiniteTransition(label = "confeti")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "caida")
    Canvas(modifier) {
        datos.forEach { d ->
            val avance = (p * d[3] + d[1]) % 1f
            val x = d[0] * size.width + sin((avance * 3 + d[0]) * 2 * PI).toFloat() * 18f
            val y = avance * (size.height + 40f) - 20f
            rotate(d[2] + avance * 540f, pivot = Offset(x, y)) {
                drawRoundRect(colores[d[4].toInt()], Offset(x - 5f, y - 3f), Size(10f, 6f), CornerRadius(2f))
            }
        }
    }
}

/** Barra de avance gruesa y redondeada, con brillo, al estilo de una lección. */
@Composable
fun BarraLeccion(avance: Float, modifier: Modifier = Modifier, color: Color = ColoresPhysio.Violeta) {
    Canvas(modifier) {
        val alto = size.height
        drawRoundRect(ColoresPhysio.SuperficieAlta, size = size, cornerRadius = CornerRadius(alto / 2))
        val ancho = (size.width * avance.coerceIn(0f, 1f)).coerceAtLeast(if (avance > 0f) alto else 0f)
        if (ancho > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(color, ColoresPhysio.Lila), 0f, size.width),
                size = Size(ancho, alto), cornerRadius = CornerRadius(alto / 2),
            )
            drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(alto * 0.4f, alto * 0.22f), Size((ancho - alto * 0.8f).coerceAtLeast(0f), alto * 0.22f), CornerRadius(alto))
        }
    }
}

/** Mensajes de ánimo según el avance del ejercicio (0..1). */
fun animoPara(avance: Float, repeticionesFaltantes: Int): String = when {
    avance <= 0f -> "¡Tú puedes! Empieza cuando quieras."
    repeticionesFaltantes == 1 -> "¡Solo una más!"
    avance < 0.25f -> "¡Muy bien! Mantén el ritmo."
    avance < 0.5f -> "¡Eso es! Vas muy bien."
    avance < 0.75f -> "¡Ya pasaste la mitad!"
    avance < 1f -> "¡Casi! Faltan $repeticionesFaltantes."
    else -> "¡Lo lograste!"
}

/** Para pantallas sin lugar: Xochi pequeño junto a un texto corto. */
@Composable
fun XochiMini(texto: String, modifier: Modifier = Modifier, expresion: ExpresionXochi = ExpresionXochi.FELIZ) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Xochi(expresion = expresion, tamano = 44.dp, soloCara = true)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = ColoresPhysio.TextoSecundario)
    }
}
