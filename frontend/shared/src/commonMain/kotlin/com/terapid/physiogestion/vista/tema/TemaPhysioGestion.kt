package com.terapid.physiogestion.vista.tema

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colores de la identidad de PhysioGestion. */
object ColoresPhysio {
    val Fondo = Color(0xFF0A0E1A)
    val Superficie = Color(0xFF121A2B)
    val SuperficieAlta = Color(0xFF1A2438)
    val Turquesa = Color(0xFF14B8A6)
    val AzulProfundo = Color(0xFF1E3A5F)
    val Texto = Color(0xFFE6EDF5)
    val TextoSecundario = Color(0xFF9FB0C4)
    val Alerta = Color(0xFFF87171)
    val FondoAlerta = Color(0xFF3A1419)
    val Exito = Color(0xFF34D399)
    val Pendiente = Color(0xFFFBBF24)
    val Violeta = Color(0xFFA78BFA)
    val AzulClaro = Color(0xFF60A5FA)
}

private val esquema = darkColorScheme(
    primary = ColoresPhysio.Turquesa,
    onPrimary = ColoresPhysio.Fondo,
    secondary = ColoresPhysio.AzulProfundo,
    onSecondary = ColoresPhysio.Texto,
    background = ColoresPhysio.Fondo,
    onBackground = ColoresPhysio.Texto,
    surface = ColoresPhysio.Superficie,
    onSurface = ColoresPhysio.Texto,
    surfaceVariant = ColoresPhysio.SuperficieAlta,
    onSurfaceVariant = ColoresPhysio.TextoSecundario,
    outline = Color(0xFF33415A),
    error = ColoresPhysio.Alerta,
    onError = ColoresPhysio.Fondo,
)

@Composable
fun TemaPhysioGestion(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = esquema, content = contenido)
}
