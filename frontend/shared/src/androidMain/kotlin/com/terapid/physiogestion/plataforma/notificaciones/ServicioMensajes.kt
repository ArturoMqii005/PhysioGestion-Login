package com.terapid.physiogestion.plataforma.notificaciones

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.terapid.physiogestion.dependencias.Dependencias
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * PUSH (Firebase Cloud Messaging). Android lo despierta aunque la app esté cerrada.
 *
 *  - [onMessageReceived]: la push trae solo el tipo y el id del aviso (nada de salud pasa
 *    por Google). La app pide sus avisos nuevos al servidor con su token y los muestra con
 *    el mismo código que la revisión en segundo plano, así ninguno sale dos veces.
 *  - [onRegistered] / [onNewToken]: Firebase cambió el token (app reinstalada, datos borrados): se avisa al
 *    servidor si hay una sesión abierta.
 *
 * Sin google-services.json no hay Firebase: [token] devuelve null y nada de esto corre.
 */
class ServicioMensajes : FirebaseMessagingService() {

    override fun onMessageReceived(mensaje: RemoteMessage) {
        val datos = mensaje.data
        val notificador = Dependencias.notificador as? NotificadorAndroid ?: return
        // Este método corre en un hilo aparte y Android le da unos segundos: se espera la respuesta.
        val revision = runBlocking {
            withTimeoutOrNull(8_000) { runCatching { Dependencias.revisor.revisar() }.getOrNull() }
        }
        // Sin red o con la sesión cerrada (cambio de contraseña): los avisos de seguridad y
        // los anuncios traen su texto. Si la revisión funcionó, ya se mostraron (o ya se veían).
        val titulo = datos["titulo"]
        if ((revision == null || !revision.sesionAbierta) && titulo != null) {
            val id = (datos["avisoId"] ?: datos["anuncioId"])?.toIntOrNull() ?: 0
            notificador.mostrarDesdePush(datos["tipo"], id, titulo, datos["mensaje"].orEmpty())
        }
    }

    /** Firebase 26 avisa del token con [onRegistered]; los Play Services anteriores, con este. */
    @Deprecated("Lo reemplaza onRegistered en Firebase Messaging 26.")
    override fun onNewToken(token: String) = registrar(token)

    override fun onRegistered(token: String) = registrar(token)

    private fun registrar(token: String) {
        if (Dependencias.autenticacion.sesion.value == null) return
        runBlocking {
            withTimeoutOrNull(8_000) { runCatching { Dependencias.avisos.registrarDispositivo(token) } }
        }
    }

    companion object {
        /** Token de FCM de este teléfono; null si Firebase no está configurado o no respondió. */
        suspend fun token(context: Context): String? {
            if (FirebaseApp.getApps(context).isEmpty()) return null
            return withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont ->
                    // getToken() sigue funcionando en Firebase 26 (register() + onRegistered es la forma nueva).
                    @Suppress("DEPRECATION")
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { tarea ->
                        cont.resume(if (tarea.isSuccessful) tarea.result else null)
                    }
                }
            }
        }
    }
}
