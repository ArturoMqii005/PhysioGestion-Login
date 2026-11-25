@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.terapid.physiogestion

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/**
 * Versión WEB: el navegador muestra la misma App() que Android, iOS y escritorio,
 * dibujada por Compose dentro de la página (index.html, en webMain/resources).
 *
 * En desarrollo: gradlew.bat :shared:wasmJsBrowserDevelopmentRun (abre http://localhost:8081).
 * Para publicar: gradlew.bat :shared:wasmJsBrowserDistribution.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    quitarAvisoDeCarga()
    ComposeViewport {
        App()
    }
}

/** index.html muestra «Cargando…» mientras el navegador descarga la app. */
private fun quitarAvisoDeCarga(): Unit = js("document.getElementById('cargando') && document.getElementById('cargando').remove()")
