package com.terapid.physiogestion.servicio.escaleras

import com.terapid.physiogestion.datos.sensores.LecturaPresion
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Presión a [metros] sobre un punto con presión [base] (inversa de la fórmula barométrica). */
private fun presionA(metros: Float, base: Float): Float =
    (base * (1.0 - metros / 44_330.0).pow(5.255)).toFloat()

/** Ruido determinista de ±[amplitud] hPa, para que las pruebas siempre den lo mismo. */
private fun ruido(i: Int, amplitud: Float) = amplitud * sin(i * 1.7f) * sin(i * 0.37f + 1f)

/** Lecturas a 10 Hz siguiendo [altura](segundos), con ruido. */
private fun recorrido(segundos: Int, base: Float, ruidoHpa: Float = 0.03f, inicioMs: Long = 0, altura: (Float) -> Float): List<LecturaPresion> =
    (0 until segundos * 10).map { i ->
        val t = i / 10f
        LecturaPresion(presionA(altura(t), base) + ruido(i, ruidoHpa), inicioMs + i * 100L)
    }

private fun AnalizadorEscaleras.calibrarEn(base: Float) {
    assertEquals(CalibracionPresion.CORRECTA, calibrar(recorrido(3, base) { 0f }))
}

class AnalizadorEscalerasTest {

    @Test
    fun laFormulaDaTresMetrosConTresDecimasDeHectopascal() {
        assertEquals(3.0f, AnalizadorEscaleras.alturaRelativa(1013.25f - 0.36f, 1013.25f), 0.1f)
        assertEquals(0f, AnalizadorEscaleras.alturaRelativa(780f, 780f), 0.001f)
    }

    @Test
    fun subirDosPisosCuentaDosPisosYUnosTreintaYCincoEscalones() {
        val a = AnalizadorEscaleras()
        a.calibrarEn(1013.25f)
        // 6 m en 40 s, a ritmo constante, y 5 s arriba.
        recorrido(45, 1013.25f) { t -> minOf(t, 40f) * 6f / 40f }.forEach { a.procesar(it) }
        val r = a.resultado(pasos = 40, duracionSegundos = 45)
        assertEquals(2, r.pisos)
        assertTrue(r.metrosSubidos in 5.6f..6.3f, "subió ${r.metrosSubidos} m")
        assertTrue(r.escalonesEstimados in 33..37, "escalones ${r.escalonesEstimados}")
        assertTrue(r.metrosPorMinuto in 7.4f..8.4f)
    }

    @Test
    fun enTulancingoTambienFunciona() {
        // A unos 2200 m la presión ronda 780 hPa: un metro cambia menos hPa que al nivel del mar.
        val a = AnalizadorEscaleras()
        a.calibrarEn(780f)
        recorrido(35, 780f) { t -> minOf(t, 30f) * 3f / 30f }.forEach { a.procesar(it) }
        assertEquals(1, a.pisos)
        assertTrue(a.subidaAcumulada in 2.7f..3.2f, "subió ${a.subidaAcumulada} m")
    }

    @Test
    fun bajarNoSumaNiResta() {
        val a = AnalizadorEscaleras()
        a.calibrarEn(1013.25f)
        // Sube 3 m en 20 s y baja 3 m en 20 s.
        recorrido(40, 1013.25f) { t -> if (t < 20f) t * 3f / 20f else 3f - (t - 20f) * 3f / 20f }.forEach { a.procesar(it) }
        assertTrue(a.subidaAcumulada in 2.6f..3.2f, "subió ${a.subidaAcumulada} m")
        assertTrue(a.alturaActual < 0.6f)
    }

    @Test
    fun elRuidoEnReposoNoSumaAltura() {
        val a = AnalizadorEscaleras()
        a.calibrarEn(1013.25f)
        recorrido(60, 1013.25f, ruidoHpa = 0.05f) { 0f }.forEach { a.procesar(it) }
        assertTrue(a.subidaAcumulada < 0.3f, "con el teléfono quieto sumó ${a.subidaAcumulada} m")
        assertEquals(0, a.pisos)
    }

    @Test
    fun laCalibracionExigeQuietudYSuficientesLecturas() {
        val a = AnalizadorEscaleras()
        assertEquals(CalibracionPresion.POCAS_MUESTRAS, a.calibrar(recorrido(3, 1013.25f) { 0f }.take(5)))
        // Subió 2 m mientras calibraba.
        assertEquals(CalibracionPresion.INESTABLE, a.calibrar(recorrido(3, 1013.25f) { t -> t * 2f / 3f }))
    }
}
