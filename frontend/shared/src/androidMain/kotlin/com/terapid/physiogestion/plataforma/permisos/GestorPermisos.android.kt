package com.terapid.physiogestion.plataforma.permisos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.terapid.physiogestion.plataforma.ContextoAndroid
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Puente entre el código común y los permisos de Android.
 *
 * Pedir un permiso exige un ActivityResultLauncher registrado por una
 * Activity, y el código común no tiene Activity. MainActivity registra aquí
 * su lanzador al crearse y entrega la respuesta con [entregarRespuesta].
 * La respuesta pendiente vive en este objeto (no en la Activity), así no se
 * pierde si la pantalla gira mientras el diálogo está abierto.
 */
object SolicitanteDePermisos {

    /** Lo asigna MainActivity: recibe el nombre del permiso de Android y abre el diálogo. */
    var lanzador: ((String) -> Unit)? = null

    private var respuestaPendiente: ((Boolean) -> Unit)? = null

    /** false si no hay Activity que pueda mostrar el diálogo. */
    fun solicitar(nombrePermiso: String, respuesta: (Boolean) -> Unit): Boolean {
        val lanzar = lanzador ?: return false
        respuestaPendiente?.invoke(false) // el sistema muestra un diálogo a la vez
        respuestaPendiente = respuesta
        lanzar(nombrePermiso)
        return true
    }

    fun entregarRespuesta(concedido: Boolean) {
        val respuesta = respuestaPendiente
        respuestaPendiente = null
        respuesta?.invoke(concedido)
    }
}

actual class GestorPermisos actual constructor() {

    actual fun estaConcedido(permiso: Permiso): Boolean {
        val nombre = nombreAndroid(permiso) ?: return true // esta versión de Android no lo exige
        return ContextoAndroid.aplicacion.checkSelfPermission(nombre) == PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun solicitar(permiso: Permiso): Boolean {
        if (estaConcedido(permiso)) return true
        val nombre = nombreAndroid(permiso) ?: return true
        return suspendCancellableCoroutine { continuacion ->
            val enviada = SolicitanteDePermisos.solicitar(nombre) { concedido ->
                if (continuacion.isActive) continuacion.resume(concedido)
            }
            if (!enviada) continuacion.resume(false)
        }
    }

    /** Nombre del permiso en Android, o null si la versión instalada no lo pide. */
    private fun nombreAndroid(permiso: Permiso): String? = when (permiso) {
        Permiso.CAMARA -> Manifest.permission.CAMERA
        // ACTIVITY_RECOGNITION existe desde Android 10 (API 29); antes no se pedía.
        Permiso.ACTIVIDAD_FISICA ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACTIVITY_RECOGNITION else null
    }
}
