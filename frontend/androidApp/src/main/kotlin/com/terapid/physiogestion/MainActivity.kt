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
import com.terapid.physiogestion.plataforma.permisos.SolicitanteDePermisos

/**
 * Único archivo propio de la app Android: prepara la ventana, entrega al
 * código común lo que solo Android tiene (Context, BuildConfig y el
 * lanzador de permisos) y muestra la interfaz compartida App().
 */
class MainActivity : ComponentActivity() {

    /** Si el usuario niega el permiso, el login mostrará el mensaje de que no hay conexión. */
    private val pedirRedLocal = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Permisos que pide el código común (cámara y actividad física). Se
     * registra como propiedad porque Android exige hacerlo antes de que la
     * Activity llegue a RESUMED.
     */
    private val pedirPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        SolicitanteDePermisos.entregarRespuesta(concedido)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras del sistema con iconos claros sobre el fondo oscuro de la app.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Context para la base de datos, las preferencias y los sensores; y las variables de BuildConfig.
        ContextoAndroid.inicializar(
            context = applicationContext,
            esDepuracion = BuildConfig.DEBUG,
            versionApp = BuildConfig.VERSION_NAME,
            servidorConfigurado = BuildConfig.SERVIDOR_URL,
        )
        // El código común no tiene Activity: aquí se le presta la forma de pedir permisos.
        SolicitanteDePermisos.lanzador = { permiso -> pedirPermiso.launch(permiso) }
        pedirPermisoRedLocal()

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        // Evita conservar una referencia a una Activity que ya no existe.
        SolicitanteDePermisos.lanzador = null
        super.onDestroy()
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
