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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
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
                        Text(
                            p.etiqueta,
                            color = color,
                            // Con cinco pestañas («Solicitudes») la letra baja un punto para que quepa.
                            fontSize = if (pestanas.size >= 5) 10.sp else 11.sp,
                            fontWeight = if (activa) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Ancho desde el que la app cambia la barra de abajo por el menú lateral (la web en una computadora, una tableta). */
val ANCHO_PANTALLA_AMPLIA = 840.dp

/**
 * Menú lateral para pantallas anchas: las mismas pestañas de la barra flotante,
 * una debajo de otra, con el logo arriba. Así la web del personal se usa como
 * una página de escritorio y no como un teléfono estirado.
 */
@Composable
fun RielPestanas(pestanas: List<Pestana>, seleccionada: Int, alElegir: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(232.dp).background(ColoresPhysio.Superficie).padding(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LogoXochi(tamano = 36.dp)
            Text("PhysioGestion", color = ColoresPhysio.Texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        pestanas.forEachIndexed { i, p ->
            val activa = i == seleccionada
            val color by animateColorAsState(if (activa) Color.White else ColoresPhysio.TextoSecundario, label = "colorRiel")
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (activa) ColoresPhysio.Elevada else Color.Transparent)
                    .presionable(escala = 0.97f, etiqueta = p.etiqueta, rol = Role.Tab) { alElegir(i) }
                    .semantics { selected = activa }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icono(p.icono, null, tamano = 20.dp, color = color)
                Text(
                    p.etiqueta,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = if (activa) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (p.insignia > 0) Insignia(p.insignia)
            }
        }
    }
}
