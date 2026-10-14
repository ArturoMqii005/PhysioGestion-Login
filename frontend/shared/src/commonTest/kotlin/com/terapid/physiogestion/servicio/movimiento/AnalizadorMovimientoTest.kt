package com.terapid.physiogestion.servicio.movimiento

import com.terapid.physiogestion.datos.sensores.Calibracion
import com.terapid.physiogestion.datos.sensores.FiltroSenal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Procesamiento de la señal: se prueba con señales simuladas, sin emulador ni teléfono. */
class AnalizadorMovimientoTest {

    /** Calibra, inicia y pasa todas las lecturas por el filtro y el analizador, como lo hace el ViewModel. */
    private fun medir(
        movimiento: SenalSimulada.Movimiento,
        duracionMs: Long,
        anguloObjetivo: Int? = 90,
        conGiroscopio: Boolean,
        sesgoGiro: Float = 0f,
        ruido: Double = 0.0,
    ): AnalizadorMovimiento {
        val filtro = FiltroSenal()
        val (acel, giro) = SenalSimulada.reposo(sesgoGiro = sesgoGiro)
        assertEquals(Calibracion.CORRECTA, filtro.calibrar(acel, if (conGiroscopio) giro else emptyList()))
        val analizador = AnalizadorMovimiento(anguloObjetivo)
        analizador.iniciar(filtro.referencia!!)
        var ms = 0L
        while (ms <= duracionMs) {
            val aceleracion = SenalSimulada.aceleracion(movimiento, ms, ruido)
            if (conGiroscopio) {
                analizador.procesarGiro(filtro.corregirGiro(SenalSimulada.giro(movimiento, ms, sesgoGiro)))
                analizador.procesarAceleracion(aceleracion) // la fusión ya lo suaviza
            } else {
                analizador.procesarAceleracion(filtro.filtrarAceleracion(aceleracion))
            }
            ms += SenalSimulada.PERIODO_MS
        }
        return analizador
    }

    @Test
    fun cuentaTresRepeticionesSoloConAcelerometro() {
        val a = medir(SenalSimulada.repeticiones(3, 90.0), 9_500, conGiroscopio = false)
        assertEquals(3, a.totalRepeticiones)
        assertTrue(a.anguloMaximo in 80f..92f, "ángulo máximo ${a.anguloMaximo}")
    }

    @Test
    fun conGiroscopioElAnguloEsMasExacto() {
        val a = medir(SenalSimulada.repeticiones(3, 90.0), 9_500, conGiroscopio = true)
        assertEquals(3, a.totalRepeticiones)
        assertTrue(a.anguloMaximo in 86f..94f, "ángulo máximo ${a.anguloMaximo}")
        a.repeticionesDetectadas.forEach { assertTrue(it.anguloAlcanzado in 86f..94f) }
    }

    @Test
    fun elSesgoDelGiroscopioSeCorrigeAlCalibrar() {
        // Sin corregir, 0.05 rad/s de sesgo sumarían casi 30° de error en 10 s.
        val a = medir(SenalSimulada.repeticiones(3, 90.0), 9_500, conGiroscopio = true, sesgoGiro = 0.05f)
        assertEquals(3, a.totalRepeticiones)
        assertTrue(a.anguloMaximo in 86f..94f, "ángulo máximo ${a.anguloMaximo}")
    }

    @Test
    fun elRuidoNoAgregaRepeticiones() {
        val a = medir(SenalSimulada.repeticiones(2, 90.0), 6_500, conGiroscopio = false, ruido = 0.6)
        assertEquals(2, a.totalRepeticiones)
    }

    @Test
    fun laFusionTambienSoportaElRuido() {
        val a = medir(SenalSimulada.repeticiones(2, 90.0), 6_500, conGiroscopio = true, ruido = 0.6)
        assertEquals(2, a.totalRepeticiones)
        assertTrue(a.anguloMaximo in 86f..95f, "ángulo máximo ${a.anguloMaximo}")
    }

    @Test
    fun unTironCortoNoEsUnaRepeticion() {
        // Sube a 70° y regresa en 300 ms: más rápido que cualquier repetición de rehabilitación.
        val tiron = SenalSimulada.repeticiones(1, 70.0, duracionMs = 300, pausaMs = 2_000)
        assertEquals(0, medir(tiron, 2_000, conGiroscopio = false).totalRepeticiones)
    }

    @Test
    fun unTemblorPequenoNoEsUnaRepeticion() {
        val temblor = SenalSimulada.repeticiones(4, 8.0, duracionMs = 1_500, pausaMs = 200)
        assertEquals(0, medir(temblor, 7_000, anguloObjetivo = 20, conGiroscopio = false).totalRepeticiones)
    }

    @Test
    fun elUmbralDependeDelObjetivo() {
        assertEquals(45f, AnalizadorMovimiento(90).umbralSubida)
        assertEquals(AnalizadorMovimiento.UMBRAL_MINIMO, AnalizadorMovimiento(20).umbralSubida)
        assertEquals(AnalizadorMovimiento.UMBRAL_SIN_OBJETIVO, AnalizadorMovimiento(null).umbralSubida)
        assertTrue(AnalizadorMovimiento(90).umbralRegreso < AnalizadorMovimiento(90).umbralSubida) // histéresis
    }

    @Test
    fun elResultadoResumeElEjercicio() {
        val a = medir(SenalSimulada.repeticiones(3, 95.0), 9_500, conGiroscopio = true)
        val resultado = a.resultado(idEjercicio = 1, repeticionesObjetivo = 3, duracionSegundos = 9)
        assertEquals(3, resultado.repeticionesDetectadas)
        assertEquals(100, resultado.porcentajeRepeticiones)
        assertTrue(resultado.cumpleObjetivo)

        val incompleto = a.resultado(idEjercicio = 1, repeticionesObjetivo = 30, duracionSegundos = 9)
        assertEquals(10, incompleto.porcentajeRepeticiones)
        assertTrue(!incompleto.cumpleObjetivo)
    }
}
