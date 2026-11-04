package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Íconos de línea de la app, dibujados con la misma retícula de 24 × 24 y trazo
 * redondeado (sin depender de la biblioteca de íconos de Material).
 */
object Iconos {
    private fun linea(nombre: String, vararg trazos: String, grosor: Float = 2f): ImageVector =
        ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).apply {
            trazos.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = grosor,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun relleno(nombre: String, vararg trazos: String): ImageVector =
        ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).apply {
            trazos.forEach { d -> addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)) }
        }.build()

    val Inicio = linea("inicio", "M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z", grosor = 2.2f)
    val Rutina = linea("rutina", "M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11", grosor = 2.2f)
    val Progreso = linea("progreso", "M4 20h16M7 16v-4M12 16V8M17 16V5", grosor = 2.2f)
    val Perfil = linea("perfil", "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0M4 21a8 8 0 0 1 16 0", grosor = 2.2f)
    val Agenda = linea("agenda", "M6 5h12a2.5 2.5 0 0 1 2.5 2.5v10A2.5 2.5 0 0 1 18 20H6a2.5 2.5 0 0 1-2.5-2.5v-10A2.5 2.5 0 0 1 6 5zM3.5 10h17M8 3v4M16 3v4", grosor = 2.2f)
    val Pacientes = linea("pacientes", "M12.5 8a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0M2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 7M18 14a6 6 0 0 1 3.5 6", grosor = 2.2f)
    val Campana = linea("campana", "M6 8a6 6 0 0 1 12 0c0 7 3 8 3 8H3s3-1 3-8M10.3 20a2 2 0 0 0 3.4 0")
    val Lista = linea("lista", "M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01", grosor = 2.2f)
    val Reloj = linea("reloj", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0M12 7v5l3 2")
    val Camara = linea("camara", "M4 8a2 2 0 0 1 2-2h2l1.5-2h5L16 6h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zM15.5 12.5a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0")
    val Cerrar = linea("cerrar", "M6 6l12 12M18 6L6 18", grosor = 2.4f)
    val Atras = linea("atras", "M15 5l-7 7 7 7", grosor = 2.6f)
    val Adelante = linea("adelante", "M9 6l6 6-6 6", grosor = 2.6f)
    val Abajo = linea("abajo", "M6 9l6 6 6-6", grosor = 2.6f)
    val Info = linea("info", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0M12 11v5M12 8h.01")
    val Escalera = linea("escalera", "M4 20h4v-4h4v-4h4V8h4", grosor = 2.2f)
    val Ondas = linea("ondas", "M3 12h3l3-7 4 14 3-7h5")
    val Check = linea("check", "M5 12.5l4.5 4.5L19 7.5", grosor = 2.6f)
    val FlechaAbajo = linea("flecha-abajo", "M12 5v14M6 13l6 6 6-6", grosor = 2.4f)
    val FlechaArriba = linea("flecha-arriba", "M12 19V5M6 11l6-6 6 6", grosor = 2.4f)
    val Salir = linea("salir", "M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16l-4-4 4-4M6 12h10")

    val Reproducir = relleno("reproducir", "M7 4.8v14.4a1 1 0 0 0 1.5.86l12-7.2a1 1 0 0 0 0-1.72l-12-7.2A1 1 0 0 0 7 4.8z")
    val Pausa = relleno("pausa", "M7.2 5h2.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2H7.2A1.2 1.2 0 0 1 6 17.8V6.2A1.2 1.2 0 0 1 7.2 5zM14.2 5h2.6A1.2 1.2 0 0 1 18 6.2v11.6a1.2 1.2 0 0 1-1.2 1.2h-2.6a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 14.2 5z")
    val Mas = linea("mas", "M12 5v14M5 12h14", grosor = 2.6f)
    val Menos = linea("menos", "M5 12h14", grosor = 2.6f)
}

/** Ícono de línea con el color del texto que lo rodea (o el indicado). */
@Composable
fun Icono(icono: ImageVector, descripcion: String?, modifier: Modifier = Modifier, tamano: Dp = 22.dp, color: Color = Color.Unspecified) {
    Icon(icono, contentDescription = descripcion, modifier = modifier.size(tamano), tint = if (color == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else color)
}
