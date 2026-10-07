package com.terapid.physiogestion

import androidx.compose.runtime.Composable
import com.terapid.physiogestion.navegacion.NavegacionPrincipal
import com.terapid.physiogestion.vista.tema.TemaPhysioGestion

/**
 * Punto de entrada común de la interfaz. Android (MainActivity), escritorio
 * (main.kt) e iOS (MainViewController.kt) solo llaman a esta función.
 */
@Composable
fun App() {
    TemaPhysioGestion {
        NavegacionPrincipal()
    }
}
