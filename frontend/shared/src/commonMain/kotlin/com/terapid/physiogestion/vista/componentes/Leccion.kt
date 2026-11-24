package com.terapid.physiogestion.vista.componentes

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/*
 * Piezas del ejercicio «como lección»: barra de avance gruesa, descanso entre series
 * con Xochi y la celebración al terminar. No conocen al ViewModel (reciben datos),
 * así se pueden ver en las capturas de diseño.
 */

/** Barra de avance de la lección con «Serie 2 de 3» y las repeticiones. */
@Composable
fun AvanceLeccion(repeticiones: Int, objetivo: Int, series: Int, porSerie: Int, modifier: Modifier = Modifier, etiqueta: String? = null) {
    val avance by animateFloatAsState(
        if (objetivo <= 0) 0f else (repeticiones.toFloat() / objetivo).coerceIn(0f, 1f),
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "avance",
    )
    val serie = if (porSerie <= 0) 1 else (repeticiones / porSerie + 1).coerceAtMost(series)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BarraLeccion(avance, Modifier.fillMaxWidth().height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                etiqueta ?: if (series > 1) "Serie $serie de $series" else "Una serie",
                color = ColoresPhysio.Lila, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            Text("$repeticiones / $objetivo", color = ColoresPhysio.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Descanso entre series: Xochi descansa y cuenta los segundos. */
@Composable
fun DescansoEntreSeries(restantes: Int, total: Int, siguienteSerie: Int, series: Int, alSeguir: () -> Unit, modifier: Modifier = Modifier, animado: Boolean = true) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        XochiDice(
            if (restantes > 5) "¡Serie lista! Respira y suelta los hombros." else "¡Prepárate! Ya casi seguimos.",
            expresion = ExpresionXochi.DESCANSANDO,
            tamano = 110.dp,
            animado = animado,
        )
        Text("Descanso", color = ColoresPhysio.TextoSecundario, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "${restantes / 60}:${(restantes % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp, lineHeight = 84.sp),
            color = ColoresPhysio.Texto,
        )
        BarraLeccion(if (total <= 0) 0f else restantes / total.toFloat(), Modifier.fillMaxWidth().height(12.dp), color = ColoresPhysio.AzulClaro)
        Text("Después sigue la serie $siguienteSerie de $series", color = ColoresPhysio.TextoTenue, fontSize = 13.sp)
        BotonPildora("Seguir ya", alSeguir, Modifier.fillMaxWidth(), icono = Iconos.Adelante)
    }
}

/** Lo primero que se ve al terminar un ejercicio: Xochi celebra (o anima a intentarlo otra vez). */
@Composable
fun CelebracionEjercicio(
    cumplido: Boolean,
    repeticiones: Int,
    objetivo: Int,
    anguloMaximo: Int?,
    duracion: String,
    modifier: Modifier = Modifier,
    animado: Boolean = true,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Xochi(expresion = if (cumplido) ExpresionXochi.CELEBRANDO else ExpresionXochi.FELIZ, tamano = 150.dp, animado = animado)
        Text(
            if (cumplido) "¡Ejercicio completado!" else "¡Buen intento!",
            style = MaterialTheme.typography.headlineLarge,
            color = if (cumplido) ColoresPhysio.Exito else ColoresPhysio.Pendiente,
            textAlign = TextAlign.Center,
        )
        Text(
            if (cumplido) "Xochi está orgulloso de ti. Cada repetición cuenta para tu recuperación."
            else "Te faltaron ${(objetivo - repeticiones).coerceAtLeast(0)} repeticiones. Puedes repetirlo o seguir con el siguiente.",
            color = ColoresPhysio.TextoSecundario, fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dato("Repeticiones", "$repeticiones/$objetivo", if (cumplido) ColoresPhysio.Exito else ColoresPhysio.Pendiente, Modifier.weight(1f))
            anguloMaximo?.let { Dato("Ángulo", "$it°", ColoresPhysio.Lila, Modifier.weight(1f)) }
            Dato("Tiempo", duracion, ColoresPhysio.AzulClaro, Modifier.weight(1f))
        }
    }
}

/** Cuadrito de dato con borde de color, como los de fin de lección. */
@Composable
private fun Dato(titulo: String, valor: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(color).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(titulo.uppercase(), color = ColoresPhysio.Fondo, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 4.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ColoresPhysio.Fondo).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(valor, color = color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** Lo que dice Xochi en la portada de la rutina según cuánto lleva el paciente. */
fun mensajeRutina(nombre: String, hechos: Int, total: Int, racha: Int, sesionGuardada: Boolean): String = when {
    sesionGuardada && racha > 1 -> "¡Rutina lista! Llevas $racha días seguidos. ¡Nos vemos mañana!"
    sesionGuardada -> "¡Rutina lista! Mañana seguimos juntos."
    total == 0 -> "Tu fisioterapeuta todavía no te asigna ejercicios."
    hechos == 0 && racha > 0 -> "¡Hola, $nombre! Llevas $racha ${if (racha == 1) "día" else "días"} de racha. Hoy son $total ejercicios."
    hechos == 0 -> "¡Hola, $nombre! Hoy son $total ejercicios. Vamos uno por uno."
    hechos < total -> "¡Vas muy bien! ${if (total - hechos == 1) "Te falta 1" else "Te faltan ${total - hechos}"}."
    else -> "¡Terminaste todos! Cuéntame cómo te sentiste para guardar tu sesión."
}
