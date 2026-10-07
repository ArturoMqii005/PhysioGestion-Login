package com.terapid.physiogestion

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.terapid.physiogestion.plataforma.ContextoAndroid

/**
 * Único archivo propio de la app Android: prepara la ventana y muestra
 * la interfaz compartida App(), que vive en el módulo shared.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras del sistema con iconos claros sobre el fondo oscuro de la app.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // La base de datos local necesita el Context de Android para ubicar su archivo.
        ContextoAndroid.inicializar(applicationContext)

        setContent {
            App()
        }
    }
}
