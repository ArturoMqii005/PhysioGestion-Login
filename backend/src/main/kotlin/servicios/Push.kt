package com.terapid.physiogestion.servicios

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Salida de las notificaciones push. En producción es Firebase Cloud Messaging
 * ([PushFcm]); sin credenciales, [PushEnRegistro] solo lo escribe en el registro
 * (la app sigue recibiendo los avisos con GET /avisos).
 *
 * Los mensajes son «de datos»: llevan el tipo y el id del aviso, no el texto. La app
 * los recibe aunque esté cerrada, pide el aviso al servidor con su token y muestra la
 * notificación. Así ningún dato de salud del paciente pasa por los servidores de Google.
 */
interface EnviadorPush {
    /** true si hay credenciales de Firebase (se publica en GET /salud). */
    val configurado: Boolean

    /** Manda [datos] a cada token. Devuelve los tokens que FCM reporta como dados de baja. */
    fun enviarATokens(tokens: List<String>, datos: Map<String, String>): Set<String>

    /** Manda [datos] a todos los teléfonos suscritos al tema. */
    fun enviarATema(tema: String, datos: Map<String, String>)

    fun suscribir(tokens: List<String>, tema: String)

    fun desuscribir(tokens: List<String>, tema: String)

    companion object {
        private val log = LoggerFactory.getLogger("Push")

        /**
         * FIREBASE_CREDENCIALES: el JSON de la cuenta de servicio de Firebase (tal cual o en
         * Base64). Se guarda solo como variable de Railway, nunca en el repositorio.
         */
        fun desdeEntorno(): EnviadorPush {
            val valor = System.getenv("FIREBASE_CREDENCIALES")?.trim()?.takeIf { it.isNotEmpty() } ?: return PushEnRegistro()
            return runCatching {
                val json = if (valor.startsWith("{")) valor else String(Base64.getDecoder().decode(valor))
                PushFcm(json)
            }.getOrElse {
                // No se escribe el valor: es una clave privada.
                log.error("FIREBASE_CREDENCIALES no es una cuenta de servicio válida ({}). Las push quedan desactivadas.", it.javaClass.simpleName)
                PushEnRegistro()
            }
        }
    }
}

/** Firebase Cloud Messaging con el SDK de administración (firebase-admin). */
class PushFcm(credencialesJson: String) : EnviadorPush {
    private val log = LoggerFactory.getLogger("Push")
    private val fcm: FirebaseMessaging

    init {
        val opciones = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(credencialesJson.byteInputStream()))
            .build()
        val app = FirebaseApp.getApps().firstOrNull { it.name == NOMBRE_APP } ?: FirebaseApp.initializeApp(opciones, NOMBRE_APP)
        fcm = FirebaseMessaging.getInstance(app)
        log.info("Firebase Cloud Messaging listo (proyecto {}).", opciones.projectId ?: "sin id")
    }

    override val configurado = true

    /** Prioridad alta para que Android despierte la app; si el teléfono está apagado, FCM lo guarda 12 horas. */
    private fun android(): AndroidConfig = AndroidConfig.builder()
        .setPriority(AndroidConfig.Priority.HIGH)
        .setTtl(12 * 60 * 60 * 1000L)
        .build()

    override fun enviarATokens(tokens: List<String>, datos: Map<String, String>): Set<String> {
        val bajas = mutableSetOf<String>()
        tokens.chunked(500).forEach { grupo ->
            val respuesta = fcm.sendEach(grupo.map { Message.builder().setToken(it).putAllData(datos).setAndroidConfig(android()).build() })
            respuesta.responses.forEachIndexed { i, r ->
                if (r.isSuccessful) return@forEachIndexed
                when (r.exception?.messagingErrorCode) {
                    MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT, MessagingErrorCode.SENDER_ID_MISMATCH -> bajas += grupo[i]
                    else -> log.warn("FCM no entregó una push: {}", r.exception?.messagingErrorCode ?: r.exception?.message)
                }
            }
        }
        return bajas
    }

    override fun enviarATema(tema: String, datos: Map<String, String>) {
        fcm.send(Message.builder().setTopic(tema).putAllData(datos).setAndroidConfig(android()).build())
    }

    override fun suscribir(tokens: List<String>, tema: String) {
        if (tokens.isNotEmpty()) fcm.subscribeToTopic(tokens, tema)
    }

    override fun desuscribir(tokens: List<String>, tema: String) {
        if (tokens.isNotEmpty()) fcm.unsubscribeFromTopic(tokens, tema)
    }

    private companion object {
        const val NOMBRE_APP = "physiogestion"
    }
}

/**
 * Sin Firebase: deja constancia en el registro y guarda lo enviado en memoria
 * (las pruebas lo revisan). [bajas] simula los tokens que FCM rechazaría.
 */
class PushEnRegistro : EnviadorPush {
    data class Enviado(val destino: String, val datos: Map<String, String>)

    private val log = LoggerFactory.getLogger("Push")
    val enviados = CopyOnWriteArrayList<Enviado>()
    val temas = ConcurrentHashMap<String, MutableSet<String>>()
    var bajas: Set<String> = emptySet()

    override val configurado = false

    override fun enviarATokens(tokens: List<String>, datos: Map<String, String>): Set<String> {
        tokens.forEach { enviados += Enviado(it, datos) }
        log.info("SIN FIREBASE · push a {} teléfono(s) · {}", tokens.size, datos["tipo"])
        return tokens.filter { it in bajas }.toSet()
    }

    override fun enviarATema(tema: String, datos: Map<String, String>) {
        enviados += Enviado("/topics/$tema", datos)
        log.info("SIN FIREBASE · push al tema {} · {}", tema, datos["tipo"])
    }

    override fun suscribir(tokens: List<String>, tema: String) {
        temas.getOrPut(tema) { ConcurrentHashMap.newKeySet() }.addAll(tokens)
    }

    override fun desuscribir(tokens: List<String>, tema: String) {
        temas[tema]?.removeAll(tokens.toSet())
    }
}
