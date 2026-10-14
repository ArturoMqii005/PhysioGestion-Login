package com.terapid.physiogestion.datos.sensores

import com.terapid.physiogestion.servicio.movimiento.SenalSimulada
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Calibración y filtrado de la señal. */
class FiltroSenalTest {

    @Test
    fun enReposoLaReferenciaEsLaGravedad() {
        val filtro = FiltroSenal()
        val (acel, _) = SenalSimulada.reposo()
        assertEquals(Calibracion.CORRECTA, filtro.calibrar(acel))
        val ref = filtro.referencia!!
        assertTrue(abs(ref.z - FiltroSenal.GRAVEDAD) < 0.01f && abs(ref.y) < 0.01f)
    }

    @Test
    fun siElPacienteSeMueveHayQueRepetirLaCalibracion() {
        val acel = (0 until 75).map { LecturaEje(0f, 0f, if (it % 2 == 0) 9f else 11f, it * 20L) }
        assertEquals(Calibracion.HUBO_MOVIMIENTO, FiltroSenal().calibrar(acel))
    }

    @Test
    fun conPocasLecturasNoSeCalibra() {
        val (acel, _) = SenalSimulada.reposo(muestras = 10)
        assertEquals(Calibracion.POCAS_MUESTRAS, FiltroSenal().calibrar(acel))
    }

    @Test
    fun alGiroscopioSeLeRestaElSesgo() {
        val filtro = FiltroSenal()
        val (acel, giro) = SenalSimulada.reposo(sesgoGiro = 0.04f)
        filtro.calibrar(acel, giro)
        val corregida = filtro.corregirGiro(LecturaEje(0.04f, 0.04f, 0.54f, 0))
        assertTrue(abs(corregida.x) < 1e-4f && abs(corregida.z - 0.5f) < 1e-4f)
    }

    @Test
    fun elPasoBajoSuavizaLosSaltos() {
        val filtro = FiltroSenal(alfa = 0.15f)
        filtro.filtrarAceleracion(LecturaEje(0f, 0f, 0f, 0))
        val salida = filtro.filtrarAceleracion(LecturaEje(10f, 0f, 0f, 20))
        assertEquals(1.5f, salida.x, 1e-4f) // solo avanza el 15 % del salto
    }
}
