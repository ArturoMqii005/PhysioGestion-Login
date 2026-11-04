package com.terapid.physiogestion.vista.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terapid.physiogestion.vista.tema.ColoresPhysio
import com.terapid.physiogestion.vista.tema.presionable

data class Pestana(val etiqueta: String, val icono: ImageVector, val insignia: Int = 0)

/** Altura que ocupa la barra (con su margen): las pantallas dejan este espacio libre abajo. */
val ALTO_BARRA_PESTANAS = 112.dp

/**
 * Barra de pestañas flotante. La píldora de la pestaña activa se desliza con un
 * resorte hasta la que se toca (no salta), y el color de cada ícono se funde.
 */
@Composable
fun BarraPestanas(pestanas: List<Pestana>, seleccionada: Int, alElegir: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().height(68.dp),
        shape = RoundedCornerShape(24.dp),
        color = ColoresPhysio.Superficie.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, ColoresPhysio.Borde),
        shadowElevation = 18.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
            val ancho = maxWidth / pestanas.size
            val x by animateDpAsState(ancho * seleccionada.coerceAtLeast(0), spring(dampingRatio = 0.78f, stiffness = 520f), label = "pildora")
            if (seleccionada >= 0) {
                Box(Modifier.offset(x = x).width(ancho).fillMaxHeight().background(ColoresPhysio.Elevada, RoundedCornerShape(16.dp)))
            }
            Row(Modifier.fillMaxSize()) {
                pestanas.forEachIndexed { i, p ->
                    val activa = i == seleccionada
                    val color by animateColorAsState(if (activa) Color.White else ColoresPhysio.TextoSecundario, label = "colorPestana")
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .presionable(escala = 0.9f, etiqueta = p.etiqueta, rol = Role.Tab) { alElegir(i) }
                            .semantics { selected = activa },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        Box {
                            Icono(p.icono, null, tamano = 22.dp, color = color)
                            if (p.insignia > 0) Insignia(p.insignia, Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-6).dp))
                        }
                        Text(p.etiqueta, color = color, fontSize = 11.sp, fontWeight = if (activa) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}
