package com.terapid.physiogestion.plataforma.permisos

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMMotionActivityManager
import kotlin.coroutines.resume

/**
 * Implementación iOS. No hay un diálogo genérico: la cámara se pide con
 * AVCaptureDevice y el movimiento con CoreMotion, que muestra su diálogo la
 * primera vez que se usa el podómetro. Los textos del diálogo están en
 * Info.plist (NSCameraUsageDescription y NSMotionUsageDescription).
 */
actual class GestorPermisos actual constructor() {

    actual fun estaConcedido(permiso: Permiso): Boolean = when (permiso) {
        Permiso.CAMARA -> AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized
        Permiso.ACTIVIDAD_FISICA -> CMMotionActivityManager.authorizationStatus() == CMAuthorizationStatusAuthorized
    }

    actual suspend fun solicitar(permiso: Permiso): Boolean = when (permiso) {
        Permiso.CAMARA -> suspendCancellableCoroutine { continuacion ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { concedido ->
                if (continuacion.isActive) continuacion.resume(concedido)
            }
        }
        // CoreMotion pregunta al usuario cuando el podómetro empieza a contar.
        Permiso.ACTIVIDAD_FISICA -> true
    }
}
