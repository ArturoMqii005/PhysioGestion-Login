package com.terapid.physiogestion.servicio.movimiento

import com.terapid.physiogestion.datos.sensores.LecturaEje
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * PROCESAMIENTO DE LA INFORMACIÓN de los sensores.
 *
 * Con el teléfono sujeto a la extremidad, cada repetición inclina el teléfono
 * y lo regresa. El analizador mide esa inclinación como el ÁNGULO entre la
 * gravedad actual y la gravedad de la postura inicial (la referencia que se
 * obtuvo al calibrar), y cuenta una repetición cada vez que el ángulo sube
 * por encima de [umbralSubida] y luego vuelve por debajo de [umbralRegreso].
 * Usar dos umbrales distintos (histéresis) evita que el temblor alrededor de
 * un solo umbral cuente repeticiones de más.
 *
 * Fusión de sensores (filtro complementario):
 *  - El giroscopio responde rápido y sin ruido, pero acumula error con el tiempo.
 *  - El acelerómetro no acumula error, pero es ruidoso y se altera con los tirones.
 *  Se combinan: la estimación de la gravedad se gira con el giroscopio y se
 *  corrige un poco (2 %) con cada lectura del acelerómetro. Si el teléfono
 *  no tiene giroscopio, se usa solo el acelerómetro filtrado.
 *
 * No depende de la red, del almacenamiento ni de la interfaz, por eso se
 * prueba con pruebas unitarias sin emulador.
 */
class AnalizadorMovimiento(
    /** Ángulo que pidió el fisioterapeuta; define a partir de cuántos grados cuenta una repetición. */
    private val anguloObjetivo: Int?,
    /** Una repetición más corta que esto es un tirón, no un movimiento de rehabilitación. */
    private val duracionMinimaMs: Long = 600L,
    /** Peso del giroscopio en la fusión (filtro complementario). */
    private val pesoGiroscopio: Float = 0.98f,
) {
    /** Grados a partir de los cuales empieza una repetición. */
    val umbralSubida: Float = anguloObjetivo?.let { max(UMBRAL_MINIMO, it * 0.5f) } ?: UMBRAL_SIN_OBJETIVO

    /** Grados por debajo de los cuales la repetición terminó (regresó a la postura inicial). */
    val umbralRegreso: Float = umbralSubida * 0.4f

    private var referencia: FloatArray? = null
    private var gravedad = floatArrayOf(0f, 0f, 1f)
    private var hayGiroscopio = false
    private var marcaUltimoGiro = 0L

    private var enRepeticion = false
    private var inicioRepeticion = 0L
    private var maximoRepeticion = 0f
    private val detectadas = mutableListOf<Repeticion>()

    /** Ángulo actual respecto de la postura inicial, en grados. */
    var anguloActual: Float = 0f
        private set

    /** Mayor ángulo alcanzado en todo el ejercicio. */
    var anguloMaximo: Float = 0f
        private set

    val totalRepeticiones: Int get() = detectadas.size

    val repeticionesDetectadas: List<Repeticion> get() = detectadas.toList()

    /** Empieza un ejercicio con la gravedad promedio medida en la calibración. */
    fun iniciar(referenciaReposo: LecturaEje) {
        val unitaria = unitario(referenciaReposo.x, referenciaReposo.y, referenciaReposo.z)
            ?: floatArrayOf(0f, 0f, 1f)
        referencia = unitaria
        gravedad = unitaria.copyOf()
        hayGiroscopio = false
        marcaUltimoGiro = 0L
        enRepeticion = false
        inicioRepeticion = 0L
        maximoRepeticion = 0f
        anguloActual = 0f
        anguloMaximo = 0f
        detectadas.clear()
    }

    /**
     * Procesa una lectura del acelerómetro. Sin giroscopio debe llegar ya
     * filtrada (paso bajo de FiltroSenal); con giroscopio puede llegar tal
     * cual, porque en la fusión solo pesa un 2 % y eso ya la suaviza.
     * Devuelve el total de repeticiones.
     */
    fun procesarAceleracion(lectura: LecturaEje): Int {
        if (referencia == null) return totalRepeticiones
        val medida = unitario(lectura.x, lectura.y, lectura.z) ?: return totalRepeticiones
        gravedad = if (hayGiroscopio) {
            unitario(
                pesoGiroscopio * gravedad[0] + (1 - pesoGiroscopio) * medida[0],
                pesoGiroscopio * gravedad[1] + (1 - pesoGiroscopio) * medida[1],
                pesoGiroscopio * gravedad[2] + (1 - pesoGiroscopio) * medida[2],
            ) ?: medida
        } else {
            medida
        }
        actualizar(lectura.marcaTiempo)
        return totalRepeticiones
    }

    /**
     * Procesa una lectura del giroscopio (rad/s, sin sesgo). Gira la
     * estimación de la gravedad: vista desde el teléfono, la gravedad gira al
     * revés que el teléfono (dg/dt = −ω × g). Devuelve el ángulo actual.
     */
    fun procesarGiro(lectura: LecturaEje): Float {
        if (referencia == null) return anguloActual
        hayGiroscopio = true
        if (marcaUltimoGiro == 0L) {
            marcaUltimoGiro = lectura.marcaTiempo
            return anguloActual
        }
        val dt = (lectura.marcaTiempo - marcaUltimoGiro) / 1000f
        marcaUltimoGiro = lectura.marcaTiempo
        // Descarta intervalos absurdos (por ejemplo, una pausa del sistema).
        if (dt <= 0f || dt > INTERVALO_MAXIMO_S) return anguloActual

        val (gx, gy, gz) = gravedad
        val cruzX = lectura.y * gz - lectura.z * gy
        val cruzY = lectura.z * gx - lectura.x * gz
        val cruzZ = lectura.x * gy - lectura.y * gx
        gravedad = unitario(gx - cruzX * dt, gy - cruzY * dt, gz - cruzZ * dt) ?: gravedad
        actualizar(lectura.marcaTiempo)
        return anguloActual
    }

    /** Arma el resumen que se guarda y se envía al servidor. */
    fun resultado(idEjercicio: Int, repeticionesObjetivo: Int, duracionSegundos: Int) = ResultadoEjercicio(
        idEjercicio = idEjercicio,
        repeticionesDetectadas = totalRepeticiones,
        repeticionesObjetivo = repeticionesObjetivo,
        anguloMaximo = anguloMaximo,
        anguloObjetivo = anguloObjetivo,
        duracionSegundos = duracionSegundos,
    )

    private fun actualizar(marca: Long) {
        val ref = referencia ?: return
        val coseno = (ref[0] * gravedad[0] + ref[1] * gravedad[1] + ref[2] * gravedad[2]).coerceIn(-1f, 1f)
        anguloActual = acos(coseno) * GRADOS_POR_RADIAN
        if (anguloActual > anguloMaximo) anguloMaximo = anguloActual

        if (!enRepeticion) {
            if (anguloActual >= umbralSubida) {
                enRepeticion = true
                inicioRepeticion = marca
                maximoRepeticion = anguloActual
            }
        } else {
            if (anguloActual > maximoRepeticion) maximoRepeticion = anguloActual
            if (anguloActual <= umbralRegreso) {
                enRepeticion = false
                val duracion = marca - inicioRepeticion
                if (duracion >= duracionMinimaMs) {
                    detectadas += Repeticion(detectadas.size + 1, duracion, maximoRepeticion)
                }
            }
        }
    }

    private fun unitario(x: Float, y: Float, z: Float): FloatArray? {
        val n = sqrt(x * x + y * y + z * z)
        return if (n < 1e-3f) null else floatArrayOf(x / n, y / n, z / n)
    }

    companion object {
        const val GRADOS_POR_RADIAN = 57.29578f

        /** Aunque el objetivo sea pequeño, menos de 12° no cuenta como repetición (es temblor). */
        const val UMBRAL_MINIMO = 12f

        /** Umbral cuando el fisioterapeuta no indicó ángulo. */
        const val UMBRAL_SIN_OBJETIVO = 25f

        const val INTERVALO_MAXIMO_S = 0.5f
    }
}
