package com.terapid.physiogestion.servicio.postura

import com.terapid.physiogestion.modelo.MetodoMedicion
import com.terapid.physiogestion.servicio.movimiento.ResultadoEjercicio
import kotlin.math.abs
import kotlin.math.max

/** Lo que la pantalla le dice al paciente mientras la cámara lo observa. */
enum class AvisoCamara {
    /** Todo bien: se te ve completo y el teléfono está quieto. */
    NINGUNO,

    /** No se ve la articulación completa (te saliste del cuadro o algo la tapa). */
    NO_TE_VEO,

    /** El acelerómetro detectó que el teléfono se movió: debe estar apoyado. */
    TELEFONO_MOVIDO,

    /** Calibración: hay que quedarse quieto en la posición inicial. */
    QUEDATE_QUIETO,

    /** Calibración de sentadilla: la posición inicial es de pie, con la pierna estirada. */
    PONTE_DERECHO,

    /** De frente a la cámara la articulación no se puede medir: hay que ponerse de lado. */
    PONTE_DE_LADO,
}

/** Por qué no se contó una repetición (se informa al terminar). */
enum class MotivoDescarte { SE_DEJO_DE_VER, TELEFONO_MOVIDO, DEMASIADO_RAPIDA }

/** Resumen de la verificación con cámara. */
data class ResultadoVerificacion(
    val repeticiones: Int,
    /** Repeticiones que llegaron al 90 % del ángulo objetivo o más. */
    val repeticionesCompletas: Int,
    val descartadas: Map<MotivoDescarte, Int>,
    val anguloMaximo: Float,
    /** Porcentaje del tiempo de medición en que la articulación se vio completa. */
    val porcentajeVisible: Int,
) {
    val totalDescartadas: Int get() = descartadas.values.sum()
}

/**
 * PROCESAMIENTO de la verificación con cámara (anti-trampa).
 *
 * Con el teléfono quieto y apoyado, la cámara detecta el esqueleto en cada
 * cuadro y se mide el ángulo de la articulación del ejercicio: la flexión de
 * la rodilla en la sentadilla, o cuánto se aleja la cadera o el tobillo de la
 * postura inicial. Igual que con el acelerómetro, cada repetición sube por
 * encima de [umbralSubida] y regresa por debajo de [umbralRegreso] (histéresis).
 *
 * Lo que la hace difícil de falsificar:
 *  1. Solo se cuenta si se ve a la persona: los tres puntos de la
 *     articulación con confianza suficiente. Mover el teléfono con la mano
 *     o mostrarle una foto no dobla ninguna rodilla.
 *  2. El teléfono debe estar quieto (lo vigila el acelerómetro): si se mueve
 *     durante una repetición, esa repetición se descarta.
 *  3. Si la persona se deja de ver más de un segundo a mitad de una
 *     repetición, la repetición se descarta. Después de cualquier descarte
 *     hay que volver a la postura inicial: media repetición no cuenta.
 *  4. Una repetición más corta que [duracionMinimaMs] es un tirón, no un
 *     ejercicio, y se descarta.
 *  5. Un salto imposible del ángulo entre dos cuadros (falla del detector)
 *     se ignora.
 *
 * No depende de la cámara ni de la interfaz: recibe posturas y se prueba
 * con secuencias simuladas.
 */
class AnalizadorPostura(
    val articulacion: ArticulacionMedida,
    private val anguloObjetivo: Int?,
    private val duracionMinimaMs: Long = 700L,
    /** Peso del cuadro nuevo en el promedio exponencial que suaviza el temblor del detector. */
    private val suavizado: Float = 0.5f,
) {
    /** Grados (respecto de la postura inicial) a partir de los cuales empieza una repetición. */
    val umbralSubida: Float = anguloObjetivo?.let { max(UMBRAL_MINIMO, it * 0.5f) } ?: UMBRAL_SIN_OBJETIVO

    /** Grados por debajo de los cuales la persona regresó a la postura inicial. */
    val umbralRegreso: Float = umbralSubida * 0.4f

    /**
     * Umbral de regreso que se usa de verdad. En la sentadilla se mide la
     * flexión absoluta, y hay personas que de pie no estiran la rodilla del
     * todo (o la cámara la ve un poco doblada): si de pie ya marca 25°, nunca
     * bajaría de [umbralRegreso] y ninguna repetición terminaría. Por eso se
     * ajusta a la flexión que tenía de pie al calibrar, más un margen.
     */
    var regreso: Float = umbralRegreso
        private set

    /** Lado del cuerpo con que se calibró: se sigue midiendo con ese mientras se vea. */
    private var ladoCalibrado: Lado? = null

    // ---- Calibración ----
    private val ventanaCalibracion = ArrayDeque<Pair<Long, Float>>()

    /** Ángulo de la articulación en la postura inicial; null hasta calibrar. */
    var anguloBase: Float? = null
        private set

    // ---- Medición ----
    private var marcaAnterior = 0L
    private var amplitudAnterior: Float? = null
    private var saltosSeguidos = 0
    private var sinCuerpoDesde: Long? = null
    private var tiempoTotalMs = 0L
    private var tiempoVisibleMs = 0L

    private var enRepeticion = false
    /** Tras un descarte hay que volver a la postura inicial antes de empezar otra repetición. */
    private var esperandoRegreso = false
    private var inicioRepeticion = 0L
    private var maximoRepeticion = 0f
    private var repeticiones = 0
    private var completas = 0
    private val descartes = mutableMapOf<MotivoDescarte, Int>()

    /** Ángulo actual del ejercicio (flexión, o distancia a la postura inicial), suavizado. */
    var anguloActual: Float = 0f
        private set

    var anguloMaximo: Float = 0f
        private set

    var aviso: AvisoCamara = AvisoCamara.NINGUNO
        private set

    /** Lado del cuerpo con que se está midiendo (el que mejor se ve). */
    var lado: Lado? = null
        private set

    val totalRepeticiones: Int get() = repeticiones

    /**
     * FASE DE CALIBRACIÓN. Recibe cuadros hasta que la persona se ve completa
     * y quieta durante [DURACION_CALIBRACION_MS]; entonces fija la postura
     * inicial y devuelve true. Mientras tanto, [aviso] dice qué falta.
     */
    fun calibrar(postura: Postura, telefonoQuieto: Boolean = true): Boolean {
        if (!telefonoQuieto) {
            ventanaCalibracion.clear()
            aviso = AvisoCamara.TELEFONO_MOVIDO
            return false
        }
        val medida = articulacion.medir(postura)
        if (medida == null) {
            ventanaCalibracion.clear()
            aviso = AvisoCamara.NO_TE_VEO
            return false
        }
        lado = medida.lado
        if (articulacion.vistaDeFrente(postura)) {
            ventanaCalibracion.clear()
            aviso = AvisoCamara.PONTE_DE_LADO
            return false
        }
        if (articulacion == ArticulacionMedida.RODILLA && medida.angulo < ANGULO_MINIMO_DE_PIE) {
            // La sentadilla empieza de pie: con la rodilla doblada la referencia quedaría mal.
            ventanaCalibracion.clear()
            aviso = AvisoCamara.PONTE_DERECHO
            return false
        }
        ventanaCalibracion.addLast(postura.marcaTiempo to medida.angulo)
        val promedio = ventanaCalibracion.map { it.second }.average().toFloat()
        if (ventanaCalibracion.any { abs(it.second - promedio) > VARIACION_MAXIMA_CALIBRACION }) {
            // Se movió: la ventana vuelve a empezar desde este cuadro.
            ventanaCalibracion.clear()
            ventanaCalibracion.addLast(postura.marcaTiempo to medida.angulo)
            aviso = AvisoCamara.QUEDATE_QUIETO
            return false
        }
        val duracion = postura.marcaTiempo - ventanaCalibracion.first().first
        if (duracion < DURACION_CALIBRACION_MS) {
            aviso = AvisoCamara.QUEDATE_QUIETO
            return false
        }
        anguloBase = promedio
        ladoCalibrado = medida.lado
        regreso = if (articulacion.mideFlexion) {
            (180f - promedio + MARGEN_REGRESO).coerceIn(umbralRegreso, umbralSubida - MARGEN_REGRESO)
        } else {
            umbralRegreso
        }
        aviso = AvisoCamara.NINGUNO
        reiniciarMedicion(postura.marcaTiempo)
        return true
    }

    /**
     * FASE DE MEDICIÓN. Procesa un cuadro y devuelve el total de repeticiones.
     * [telefonoQuieto] lo informa el acelerómetro.
     */
    fun procesar(postura: Postura, telefonoQuieto: Boolean = true): Int {
        val base = anguloBase ?: return repeticiones
        val marca = postura.marcaTiempo
        val dt = if (marcaAnterior == 0L) 0L else (marca - marcaAnterior).coerceIn(0L, INTERVALO_MAXIMO_MS)
        marcaAnterior = marca
        tiempoTotalMs += dt

        if (!telefonoQuieto) {
            aviso = AvisoCamara.TELEFONO_MOVIDO
            if (enRepeticion) descartar(MotivoDescarte.TELEFONO_MOVIDO)
            amplitudAnterior = null
            return repeticiones
        }

        val medida = articulacion.medir(postura, preferido = ladoCalibrado)
        if (medida == null) {
            aviso = AvisoCamara.NO_TE_VEO
            val desde = sinCuerpoDesde ?: marca.also { sinCuerpoDesde = it }
            if (enRepeticion && marca - desde > TOLERANCIA_SIN_CUERPO_MS) descartar(MotivoDescarte.SE_DEJO_DE_VER)
            amplitudAnterior = null
            return repeticiones
        }
        sinCuerpoDesde = null
        tiempoVisibleMs += dt
        aviso = AvisoCamara.NINGUNO
        lado = medida.lado

        val amplitud = if (articulacion.mideFlexion) 180f - medida.angulo else abs(medida.angulo - base)
        val previa = amplitudAnterior
        if (previa != null && dt in 1..SALTO_VENTANA_MS && abs(amplitud - previa) > SALTO_MAXIMO && saltosSeguidos < 2) {
            // Ninguna articulación se mueve tanto en un cuadro: es una falla del detector.
            // Si el salto se repite en varios cuadros seguidos, sí era la postura real y se acepta.
            saltosSeguidos++
            return repeticiones
        }
        saltosSeguidos = 0
        amplitudAnterior = amplitud
        anguloActual = if (previa == null) amplitud else suavizado * amplitud + (1 - suavizado) * anguloActual
        if (anguloActual > anguloMaximo) anguloMaximo = anguloActual
        actualizarConteo(marca)
        return repeticiones
    }

    fun resultado(): ResultadoVerificacion = ResultadoVerificacion(
        repeticiones = repeticiones,
        repeticionesCompletas = completas,
        descartadas = descartes.toMap(),
        anguloMaximo = anguloMaximo,
        porcentajeVisible = if (tiempoTotalMs <= 0L) 0 else ((tiempoVisibleMs * 100) / tiempoTotalMs).toInt(),
    )

    /** Resumen con el mismo formato que el de los sensores, marcado como verificado con cámara. */
    fun resultadoEjercicio(idEjercicio: Int, repeticionesObjetivo: Int, duracionSegundos: Int) = ResultadoEjercicio(
        idEjercicio = idEjercicio,
        repeticionesDetectadas = repeticiones,
        repeticionesObjetivo = repeticionesObjetivo,
        anguloMaximo = anguloMaximo,
        anguloObjetivo = anguloObjetivo,
        duracionSegundos = duracionSegundos,
        metodo = MetodoMedicion.CAMARA,
    )

    private fun actualizarConteo(marca: Long) {
        if (esperandoRegreso) {
            // Media repetición (por ejemplo, la persona reaparece ya agachada): no cuenta.
            if (anguloActual <= regreso) esperandoRegreso = false
            return
        }
        if (!enRepeticion) {
            if (anguloActual >= umbralSubida) {
                enRepeticion = true
                inicioRepeticion = marca
                maximoRepeticion = anguloActual
            }
            return
        }
        if (anguloActual > maximoRepeticion) maximoRepeticion = anguloActual
        if (anguloActual <= regreso) {
            enRepeticion = false
            if (marca - inicioRepeticion < duracionMinimaMs) {
                sumarDescarte(MotivoDescarte.DEMASIADO_RAPIDA)
                return
            }
            repeticiones++
            if (anguloObjetivo == null || maximoRepeticion >= anguloObjetivo * 0.9f) completas++
        }
    }

    private fun descartar(motivo: MotivoDescarte) {
        enRepeticion = false
        esperandoRegreso = true
        sumarDescarte(motivo)
    }

    private fun sumarDescarte(motivo: MotivoDescarte) {
        descartes[motivo] = (descartes[motivo] ?: 0) + 1
    }

    private fun reiniciarMedicion(marca: Long) {
        marcaAnterior = marca
        amplitudAnterior = null
        sinCuerpoDesde = null
        tiempoTotalMs = 0L
        tiempoVisibleMs = 0L
        enRepeticion = false
        esperandoRegreso = false
        repeticiones = 0
        completas = 0
        descartes.clear()
        anguloActual = 0f
        anguloMaximo = 0f
    }

    companion object {
        /** Con la cámara hay más ruido que con el giroscopio: menos de 15° no es una repetición. */
        const val UMBRAL_MINIMO = 15f
        const val UMBRAL_SIN_OBJETIVO = 25f

        /** La persona debe verse completa y quieta este tiempo para fijar la postura inicial. */
        const val DURACION_CALIBRACION_MS = 1_500L
        const val VARIACION_MAXIMA_CALIBRACION = 6f

        /** Ángulo interno de la rodilla con la pierna prácticamente estirada (de pie). */
        const val ANGULO_MINIMO_DE_PIE = 150f

        /** Grados sobre la flexión de pie para dar por terminada una repetición. */
        const val MARGEN_REGRESO = 8f

        /** A mitad de repetición se tolera perder a la persona hasta un segundo. */
        const val TOLERANCIA_SIN_CUERPO_MS = 1_000L

        /** Más de 45° de diferencia entre dos cuadros seguidos (menos de 150 ms) es una falla del detector. */
        const val SALTO_MAXIMO = 45f
        const val SALTO_VENTANA_MS = 150L

        /** Pausas largas (la app en segundo plano) no se cuentan como tiempo medido. */
        const val INTERVALO_MAXIMO_MS = 200L
    }
}
