package com.terapid.physiogestion.vista.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import physiogestion.shared.generated.resources.Res
import physiogestion.shared.generated.resources.bricolage_bold
import physiogestion.shared.generated.resources.bricolage_extrabold
import physiogestion.shared.generated.resources.inter_bold
import physiogestion.shared.generated.resources.inter_medium
import physiogestion.shared.generated.resources.inter_regular
import physiogestion.shared.generated.resources.inter_semibold

/**
 * Identidad visual de PhysioGestion (v1.5): modo oscuro con violeta.
 *
 *  - [Violeta] se usa como RELLENO (botones, la tarjeta de la meta, el anillo) con texto blanco.
 *  - [Lila] es el mismo violeta aclarado para TEXTO e ÍCONOS sobre el fondo oscuro:
 *    el violeta puro no llega al contraste mínimo de 4.5:1 sobre negro.
 *  - El coral ([Alerta]) queda reservado para el dolor alto y los errores.
 */
object ColoresPhysio {
    val Fondo = Color(0xFF0E0C17)
    val Superficie = Color(0xFF1A1726)
    val SuperficieAlta = Color(0xFF2A2640)
    val Elevada = Color(0xFF2E2945)
    val Borde = Color(0xFF2A2640)
    val BordeFuerte = Color(0xFF3A3550)

    val Violeta = Color(0xFF6C47FF)
    val Lila = Color(0xFFAA94FF)
    val TinteVioleta = Color(0xFF281D58)

    val Texto = Color(0xFFF4F2FF)
    val TextoSecundario = Color(0xFFA9A4C2)
    val TextoTenue = Color(0xFF8E89A8)

    val Alerta = Color(0xFFFF8A8F)
    val AlertaRelleno = Color(0xFFE5484D)
    val FondoAlerta = Color(0xFF2A1418)
    val Exito = Color(0xFF7EE2B4)
    val FondoExito = Color(0xFF12301F)
    val Pendiente = Color(0xFFFFCF6B)
    val FondoPendiente = Color(0xFF3A2A0C)
    val AzulClaro = Color(0xFF9DBBFF)
    val FondoAzul = Color(0xFF172544)
    val Durazno = Color(0xFFFFB592)
    val FondoDurazno = Color(0xFF3A2218)
    val Lavanda = Color(0xFFC4B5FD)

    /** Botón secundario redondo y fondos de íconos. */
    val AzulProfundo = SuperficieAlta
}

private val esquema = darkColorScheme(
    primary = ColoresPhysio.Violeta,
    onPrimary = Color.White,
    primaryContainer = ColoresPhysio.TinteVioleta,
    onPrimaryContainer = ColoresPhysio.Lila,
    secondary = ColoresPhysio.Lila,
    onSecondary = ColoresPhysio.Fondo,
    secondaryContainer = ColoresPhysio.Elevada,
    onSecondaryContainer = ColoresPhysio.Texto,
    tertiary = ColoresPhysio.Exito,
    background = ColoresPhysio.Fondo,
    onBackground = ColoresPhysio.Texto,
    surface = ColoresPhysio.Superficie,
    onSurface = ColoresPhysio.Texto,
    surfaceVariant = ColoresPhysio.SuperficieAlta,
    onSurfaceVariant = ColoresPhysio.TextoSecundario,
    surfaceContainer = ColoresPhysio.Superficie,
    surfaceContainerHigh = ColoresPhysio.SuperficieAlta,
    surfaceContainerHighest = ColoresPhysio.Elevada,
    outline = ColoresPhysio.BordeFuerte,
    outlineVariant = ColoresPhysio.Borde,
    error = ColoresPhysio.Alerta,
    onError = ColoresPhysio.Fondo,
)

private val formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Inter para el texto y Bricolage Grotesque para los títulos (ambas con licencia OFL). */
@Composable
private fun tipografia(): Typography {
    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )
    val titulos = FontFamily(
        Font(Res.font.bricolage_bold, FontWeight.Bold),
        Font(Res.font.bricolage_extrabold, FontWeight.ExtraBold),
    )
    return remember(inter, titulos) {
        val base = Typography()
        fun TextStyle.texto() = copy(fontFamily = inter)
        fun TextStyle.titulo(tam: Int, interlineado: Int, espaciado: Double) = copy(
            fontFamily = titulos,
            fontWeight = FontWeight.ExtraBold,
            fontSize = tam.sp,
            lineHeight = interlineado.sp,
            letterSpacing = espaciado.em,
        )
        Typography(
            displayLarge = base.displayLarge.titulo(56, 56, -0.035),
            displayMedium = base.displayMedium.titulo(46, 46, -0.035),
            displaySmall = base.displaySmall.titulo(38, 40, -0.03),
            headlineLarge = base.headlineLarge.titulo(34, 38, -0.02),
            headlineMedium = base.headlineMedium.titulo(28, 32, -0.02),
            headlineSmall = base.headlineSmall.titulo(24, 28, -0.015),
            titleLarge = base.titleLarge.titulo(21, 26, -0.01),
            titleMedium = base.titleMedium.texto().copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            titleSmall = base.titleSmall.texto().copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            bodyLarge = base.bodyLarge.texto(),
            bodyMedium = base.bodyMedium.texto(),
            bodySmall = base.bodySmall.texto().copy(fontSize = 13.sp, lineHeight = 18.sp),
            labelLarge = base.labelLarge.texto().copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            labelMedium = base.labelMedium.texto().copy(fontWeight = FontWeight.SemiBold),
            labelSmall = base.labelSmall.texto().copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
fun TemaPhysioGestion(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = esquema, typography = tipografia(), shapes = formas, content = contenido)
}
