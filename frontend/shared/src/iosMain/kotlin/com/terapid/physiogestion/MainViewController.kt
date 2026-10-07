package com.terapid.physiogestion

import androidx.compose.ui.window.ComposeUIViewController

/** Puente hacia iOS: la app de Xcode (iosApp) muestra esta pantalla compartida. */
fun MainViewController() = ComposeUIViewController { App() }
