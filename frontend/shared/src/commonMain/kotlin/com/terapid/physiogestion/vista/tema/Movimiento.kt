package com.terapid.physiogestion.vista.tema

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.navigation.NavBackStackEntry

/**
 * MOVIMIENTO: lo que hace que la app se sienta fluida.
 *
 * Todo se anima con resortes (springs) y con la curva de desaceleración que usa
 * iOS al mostrar una pantalla. Los resortes no tienen una duración fija: si una
 * animación se interrumpe (el usuario toca otra cosa a la mitad), continúan desde
 * donde iban con su velocidad, en lugar de saltar.
 */
object Movimiento {
    /** Desaceleración larga tipo iOS: arranca rápido y frena suave. */
    val CurvaIos = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    const val PANTALLA_MS = 420
    const val PESTANA_MS = 220

    /** Resorte para tamaños y posiciones (sin rebote visible). */
    fun <T> resorte() = spring<T>(dampingRatio = 0.86f, stiffness = 420f)

    /** Resorte un poco más vivo para lo que se toca (botones, tarjetas). */
    fun <T> resortePresion() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

    /** Resorte para números y anillos que avanzan. */
    fun <T> resorteProgreso() = spring<T>(dampingRatio = 1f, stiffness = Spring.StiffnessLow)

    // ---------------------------------------------------------------- Transiciones entre pantallas

    /** Entrar a una pantalla: llega desde la derecha; la de atrás se corre un tercio y se atenúa. */
    val entrar: EnterTransition =
        slideInHorizontally(tween(PANTALLA_MS, easing = CurvaIos)) { it } + fadeIn(tween(PANTALLA_MS / 2))
    val salirHaciaAtras: ExitTransition =
        slideOutHorizontally(tween(PANTALLA_MS, easing = CurvaIos)) { -it / 3 } + fadeOut(tween(PANTALLA_MS), targetAlpha = 0.4f)
    val volver: EnterTransition =
        slideInHorizontally(tween(PANTALLA_MS, easing = CurvaIos)) { -it / 3 } + fadeIn(tween(PANTALLA_MS), initialAlpha = 0.4f)
    val cerrar: ExitTransition =
        slideOutHorizontally(tween(PANTALLA_MS, easing = CurvaIos)) { it } + fadeOut(tween(PANTALLA_MS, delayMillis = PANTALLA_MS / 3))

    /** Cambiar de pestaña: desvanecido corto con un leve acercamiento, como en las apps de Apple. */
    val entrarPestana: EnterTransition =
        fadeIn(tween(PESTANA_MS, delayMillis = 60)) + scaleIn(tween(PESTANA_MS, delayMillis = 60), initialScale = 0.985f)
    val salirPestana: ExitTransition = fadeOut(tween(PESTANA_MS / 2))

    /** Iniciar o cerrar sesión: desvanecido con acercamiento (no tiene sentido «regresar»). */
    val entrarRaiz: EnterTransition = fadeIn(tween(360)) + scaleIn(tween(360, easing = CurvaIos), initialScale = 0.96f)
    val salirRaiz: ExitTransition = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.02f)
}

/** Elige la transición según de dónde a dónde se navega (pestaña ↔ pestaña o entrar a una pantalla). */
class TransicionesNavegacion(
    private val esPestana: (NavBackStackEntry) -> Boolean,
    private val esRaiz: (NavBackStackEntry) -> Boolean,
) {
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.entrePestanas() =
        esPestana(initialState) && esPestana(targetState)

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.haciaRaiz() = esRaiz(targetState) || esRaiz(initialState)

    val entrar: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when {
            entrePestanas() -> Movimiento.entrarPestana
            haciaRaiz() -> Movimiento.entrarRaiz
            else -> Movimiento.entrar
        }
    }
    val salir: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when {
            entrePestanas() -> Movimiento.salirPestana
            haciaRaiz() -> Movimiento.salirRaiz
            else -> Movimiento.salirHaciaAtras
        }
    }
    val volver: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (entrePestanas()) Movimiento.entrarPestana else Movimiento.volver
    }
    val cerrar: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (entrePestanas()) Movimiento.salirPestana else Movimiento.cerrar
    }
}

// ---------------------------------------------------------------- Presionar

/**
 * Lo que se toca se encoge un poco y regresa con un resorte (en lugar de la onda
 * de Material). Conserva el rol de botón para TalkBack y VoiceOver.
 */
fun Modifier.presionable(
    habilitado: Boolean = true,
    escala: Float = 0.96f,
    etiqueta: String? = null,
    rol: Role = Role.Button,
    alPulsar: () -> Unit,
): Modifier = composed {
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val factor by animateFloatAsState(if (presionado && habilitado) escala else 1f, Movimiento.resortePresion(), label = "presion")
    this
        .graphicsLayer {
            scaleX = factor
            scaleY = factor
        }
        .clickable(
            interactionSource = interaccion,
            indication = null,
            enabled = habilitado,
            onClickLabel = etiqueta,
            role = rol,
            onClick = alPulsar,
        )
}

/** Solo el efecto de encogerse, para componentes que ya manejan el clic (Button, Card). */
fun Modifier.efectoPresion(interaccion: MutableInteractionSource, escala: Float = 0.96f): Modifier = composed {
    val presionado by interaccion.collectIsPressedAsState()
    val factor by animateFloatAsState(if (presionado) escala else 1f, Movimiento.resortePresion(), label = "presion")
    graphicsLayer {
        scaleX = factor
        scaleY = factor
    }
}

// ---------------------------------------------------------------- Elementos compartidos

/**
 * Transición conectada: una tarjeta que se toca «crece» hasta la pantalla que abre.
 * La navegación provee ambos alcances; si falta alguno (vistas previas, pruebas),
 * el modificador no hace nada.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalTransicionCompartida = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAlcanceAnimado = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun ConAlcanceAnimado(alcance: AnimatedVisibilityScope, contenido: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAlcanceAnimado provides alcance, content = contenido)
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val limitesIos = BoundsTransform { _: Rect, _: Rect -> spring(dampingRatio = 0.9f, stiffness = 380f) }

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.compartido(clave: String): Modifier = composed {
    val transicion = LocalTransicionCompartida.current
    val alcance = LocalAlcanceAnimado.current
    if (transicion == null || alcance == null) {
        this
    } else {
        with(transicion) {
            this@composed.sharedBounds(
                sharedContentState = rememberSharedContentState(clave),
                animatedVisibilityScope = alcance,
                boundsTransform = limitesIos,
            )
        }
    }
}
