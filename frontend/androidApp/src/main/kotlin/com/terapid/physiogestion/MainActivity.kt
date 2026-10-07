package com.terapid.physiogestion

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.terapid.physiogestion.plataforma.ContextoAndroid

/**
 * Único archivo propio de la app Android: prepara la ventana y muestra
 * la interfaz compartida App(), que vive en el módulo shared.
 */
class MainActivity : ComponentActivity() {

    /** Si el usuario niega el permiso, el login mostrará el mensaje de que no hay conexión. */
    private val pedirRedLocal = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras del sistema con iconos claros sobre el fondo oscuro de la app.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // La base de datos local necesita el Context de Android para ubicar su archivo.
        ContextoAndroid.inicializar(applicationContext)
        pedirPermisoRedLocal()

        setContent {
            App()
        }
    }

    /**
     * Desde Android 17 (API 37), una app necesita el permiso ACCESS_LOCAL_NETWORK para
     * conectarse a direcciones de la red local, como 10.0.2.2 (la computadora vista desde
     * el emulador) o 192.168.x.x. localhost (adb reverse) no lo necesita.
     */
    private fun pedirPermisoRedLocal() {
        if (Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission(PERMISO_RED_LOCAL) != PackageManager.PERMISSION_GRANTED
        ) {
            pedirRedLocal.launch(PERMISO_RED_LOCAL)
        }
    }

    private companion object {
        const val PERMISO_RED_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}