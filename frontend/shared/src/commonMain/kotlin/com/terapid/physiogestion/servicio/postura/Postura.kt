package com.terapid.physiogestion.servicio.postura

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Puntos del cuerpo que usa la verificación con cámara. El detector de
 * postura (ML Kit en Android) encuentra 33; para medir rodilla, cadera y
 * tobillo bastan estos, de los dos lados del cuerpo.
 */
enum class PuntoCuerpo {
    HOMBRO_IZQ, HOMBRO_DER,
    CADERA_IZQ, CADERA_DER,
    RODILLA_IZQ, RODILLA_DER,
    TOBILLO_IZQ, TOBILLO_DER,
    PUNTA_PIE_IZQ, PUNTA_PIE_DER,
}

/**
 * Un punto detectado, en píxeles de la imagen ya enderezada (como se ve en
 * pantalla, sin espejo). [confianza] es la probabilidad (0 a 1) de que el
 * punto esté realmente dentro del cuadro.
 */
data class PuntoDetectado(val x: Float, val y: Float, val confianza: Float)

/**
 * Lo que vio la cámara en un cuadro de video. Si no había nadie, [puntos]
 * llega vacío: el cuadro igual cuenta para saber cuánto tiempo no se vio a
 * la persona.
 */
data class Postura(
    val puntos: Map<PuntoCuerpo, PuntoDetectado>,
    /** Milisegundos (reloj monotónico del teléfono). */
    val marcaTiempo: Long,
    val ancho: Int,
    val alto: Int,
) {
    val hayPersona: Boolean get() = puntos.isNotEmpty()
}

enum class Lado { IZQUIERDO, DERECHO }

/** Ángulo de una articulación en un cuadro y el lado del cuerpo con que se midió. */
data class MedidaArticular(val angulo: Float, val lado: Lado)

/**
 * Articulación que se mide según la zona corporal del ejercicio. Cada una
 * se mide con tres puntos: el ángulo está en el punto de en medio.
 *  - RODILLA: cadera, rodilla, tobillo (sentadilla).
 *  - CADERA: hombro, cadera, rodilla (puente de glúteo).
 *  - TOBILLO: rodilla, tobillo, punta del pie (elevación de talones).
 */
enum class ArticulacionMedida(
    val nombre: String,
    /**
     * true si el ángulo del ejercicio es la FLEXIÓN de la articulación
     * (180° − ángulo interno), como en la sentadilla: «objetivo 90°» es la
     * rodilla doblada a 90°. false si es cuánto se aleja de la postura inicial.
     */
    val mideFlexion: Boolean,
    private val izquierda: Triple<PuntoCuerpo, PuntoCuerpo, PuntoCuerpo>,
    private val derecha: Triple<PuntoCuerpo, PuntoCuerpo, PuntoCuerpo>,
) {
    RODILLA(
        "rodilla",
        true,
        Triple(PuntoCuerpo.CADERA_IZQ, PuntoCuerpo.RODILLA_IZQ, PuntoCuerpo.TOBILLO_IZQ),
        Triple(PuntoCuerpo.CADERA_DER, PuntoCuerpo.RODILLA_DER, PuntoCuerpo.TOBILLO_DER),
    ),
    CADERA(
        "cadera",
        false,
        Triple(PuntoCuerpo.HOMBRO_IZQ, PuntoCuerpo.CADERA_IZQ, PuntoCuerpo.RODILLA_IZQ),
        Triple(PuntoCuerpo.HOMBRO_DER, PuntoCuerpo.CADERA_DER, PuntoCuerpo.RODILLA_DER),
    ),
    TOBILLO(
        "tobillo",
        false,
        Triple(PuntoCuerpo.RODILLA_IZQ, PuntoCuerpo.TOBILLO_IZQ, PuntoCuerpo.PUNTA_PIE_IZQ),
        Triple(PuntoCuerpo.RODILLA_DER, PuntoCuerpo.TOBILLO_DER, PuntoCuerpo.PUNTA_PIE_DER),
    );

    /** Puntos de los dos lados (se resaltan mientras todavía no se sabe con cuál se mide). */
    val puntos: Set<PuntoCuerpo> get() = (izquierda.toList() + derecha.toList()).toSet()

    /** Qué partes del cuerpo deben verse en la cámara (para las instrucciones). */
    val partesVisibles: String
        get() = when (this) {
            RODILLA -> "cadera, rodilla y tobillo"
            CADERA -> "hombro, cadera y rodilla"
            TOBILLO -> "rodilla, tobillo y pie"
        }

    /** Los tres puntos del lado con que se está midiendo. */
    fun puntosDe(lado: Lado?): Set<PuntoCuerpo> = when (lado) {
        Lado.IZQUIERDO -> izquierda.toList().toSet()
        Lado.DERECHO -> derecha.toList().toSet()
        null -> puntos
    }

    /**
     * Ángulo de la articulación con el lado que mejor se ve. Si se indica
     * [preferido] y ese lado se ve, se usa ese: así la medición no salta de
     * una pierna a la otra entre cuadros. Devuelve null si ninguno de los dos
     * lados tiene sus tres puntos con confianza suficiente: en ese cuadro no
     * se puede afirmar nada.
     */
    fun medir(postura: Postura, confianzaMinima: Float = CONFIANZA_MINIMA, preferido: Lado? = null): MedidaArticular? {
        val opciones = listOf(Lado.IZQUIERDO to izquierda, Lado.DERECHO to derecha).mapNotNull { (lado, trio) ->
            val a = postura.puntos[trio.first] ?: return@mapNotNull null
            val b = postura.puntos[trio.second] ?: return@mapNotNull null
            val c = postura.puntos[trio.third] ?: return@mapNotNull null
            val confianza = minOf(a.confianza, b.confianza, c.confianza)
            if (confianza < confianzaMinima) return@mapNotNull null
            val angulo = anguloEn(a, b, c) ?: return@mapNotNull null
            Triple(lado, angulo, confianza)
        }
        val mejor = opciones.firstOrNull { it.first == preferido } ?: opciones.maxByOrNull { it.third } ?: return null
        return MedidaArticular(mejor.second, mejor.first)
    }

    /**
     * true si la persona está DE FRENTE a la cámara. De frente, las dos
     * caderas se ven separadas (unos ¾ del largo del muslo); de lado quedan
     * casi encimadas. De frente la rodilla se dobla hacia la cámara y el
     * ángulo casi no cambia en la imagen, así que la medición sería falsa.
     */
    fun vistaDeFrente(postura: Postura, confianzaMinima: Float = CONFIANZA_MINIMA): Boolean {
        val caderaIzq = postura.puntos[PuntoCuerpo.CADERA_IZQ] ?: return false
        val caderaDer = postura.puntos[PuntoCuerpo.CADERA_DER] ?: return false
        val rodillaIzq = postura.puntos[PuntoCuerpo.RODILLA_IZQ] ?: return false
        val rodillaDer = postura.puntos[PuntoCuerpo.RODILLA_DER] ?: return false
        if (listOf(caderaIzq, caderaDer, rodillaIzq, rodillaDer).any { it.confianza < confianzaMinima }) return false
        val muslo = (distancia(caderaIzq, rodillaIzq) + distancia(caderaDer, rodillaDer)) / 2f
        if (muslo < 1f) return false
        return distancia(caderaIzq, caderaDer) > PROPORCION_CADERAS_DE_FRENTE * muslo
    }

    companion object {
        /** Por debajo de esta probabilidad, el punto se considera fuera del cuadro o tapado. */
        const val CONFIANZA_MINIMA = 0.5f

        /** Separación de caderas (respecto del muslo) a partir de la cual la persona está de frente. */
        const val PROPORCION_CADERAS_DE_FRENTE = 0.45f

        /** «Rodilla» -> RODILLA. Las zonas sin articulación medible («Muslo») devuelven null. */
        fun deZona(zonaCorporal: String): ArticulacionMedida? = when (zonaCorporal.trim().lowercase()) {
            "rodilla" -> RODILLA
            "cadera" -> CADERA
            "tobillo" -> TOBILLO
            else -> null
        }
    }
}

/** Ángulo en grados (0 a 180) que forman a-b-c con vértice en b; null si dos puntos coinciden. */
fun anguloEn(a: PuntoDetectado, b: PuntoDetectado, c: PuntoDetectado): Float? {
    val ux = a.x - b.x
    val uy = a.y - b.y
    val vx = c.x - b.x
    val vy = c.y - b.y
    val nu = sqrt(ux * ux + uy * uy)
    val nv = sqrt(vx * vx + vy * vy)
    if (nu < 1f || nv < 1f) return null
    val coseno = ((ux * vx + uy * vy) / (nu * nv)).coerceIn(-1f, 1f)
    return acos(coseno) * GRADOS_POR_RADIAN
}

internal fun diferenciaAngular(a: Float, b: Float): Float = abs(a - b)

private fun distancia(a: PuntoDetectado, b: PuntoDetectado): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private const val GRADOS_POR_RADIAN = 57.29578f
