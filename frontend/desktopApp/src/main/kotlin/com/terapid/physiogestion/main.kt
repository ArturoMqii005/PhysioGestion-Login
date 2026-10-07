package com.terapid.physiogestion

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Versión de escritorio: abre una ventana del tamaño de un teléfono con la misma App(). */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PhysioGestion",
        state = rememberWindowState(width = 420.dp, height = 860.dp),
    ) {
        App()
    }
}
