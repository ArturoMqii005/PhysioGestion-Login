package com.terapid.physiogestion.servicios

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Correo que el servidor necesita enviar (código de verificación o de recuperación). */
data class CorreoSaliente(val para: String, val asunto: String, val texto: String)

/** Quién envía los correos. Se elige con variables de entorno en [EnviadorCorreo.desdeEntorno]. */
fun interface EnviadorCorreo {
    fun enviar(correo: CorreoSaliente)

    companion object {
        /**
         * VARIABLES DE ENTORNO:
         *  BREVO_API_KEY     clave de la API de Brevo (plan gratuito: 300 correos al día)
         *  CORREO_REMITENTE  correo verificado en Brevo desde el que se envía
         *  NOMBRE_REMITENTE  nombre que ve el paciente (opcional)
         * Si faltan, los correos se escriben en el registro del servidor (modo de desarrollo).
         */
        fun desdeEntorno(): EnviadorCorreo {
            val clave = System.getenv("BREVO_API_KEY")?.takeIf { it.isNotBlank() }
            val remitente = System.getenv("CORREO_REMITENTE")?.takeIf { it.isNotBlank() }
            return if (clave != null && remitente != null) {
                CorreoBrevo(clave, remitente, System.getenv("NOMBRE_REMITENTE") ?: "PhysioGestion")
            } else {
                CorreoEnRegistro()
            }
        }
    }
}

/**
 * Sin proveedor de correo: el mensaje (con el código) se escribe en el registro
 * del servidor. Sirve en desarrollo y para la demostración; en producción se
 * configura Brevo para que el código llegue al correo del paciente.
 */
class CorreoEnRegistro : EnviadorCorreo {
    private val log = LoggerFactory.getLogger("Correo")
    override fun enviar(correo: CorreoSaliente) {
        log.warn("SIN PROVEEDOR DE CORREO · para {} · {} · {}", enmascarar(correo.para), correo.asunto, correo.texto.replace('\n', ' '))
    }
}

/**
 * Envío con la API HTTP de Brevo. Se usa HTTPS (puerto 443) porque Railway
 * bloquea el SMTP saliente en sus planes gratuitos. El envío es asíncrono:
 * la respuesta al paciente no espera a que el correo salga.
 */
class CorreoBrevo(
    private val claveApi: String,
    private val remitente: String,
    private val nombreRemitente: String,
) : EnviadorCorreo {
    private val log = LoggerFactory.getLogger("Correo")
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override fun enviar(correo: CorreoSaliente) {
        val cuerpo = buildJsonObject {
            putJsonObject("sender") {
                put("name", nombreRemitente)
                put("email", remitente)
            }
            putJsonArray("to") { addJsonObject { put("email", correo.para) } }
            put("subject", correo.asunto)
            put("textContent", correo.texto)
        }
        val peticion = HttpRequest.newBuilder(URI("https://api.brevo.com/v3/smtp/email"))
            .timeout(Duration.ofSeconds(15))
            .header("api-key", claveApi)
            .header("accept", "application/json")
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(Json.encodeToString(cuerpo)))
            .build()
        http.sendAsync(peticion, HttpResponse.BodyHandlers.ofString()).whenComplete { r, error ->
            when {
                error != null -> log.error("No se pudo enviar el correo a {}: {}", enmascarar(correo.para), error.message)
                r.statusCode() !in 200..299 -> log.error("Brevo respondió {} al enviar a {}", r.statusCode(), enmascarar(correo.para))
                else -> log.info("Correo enviado a {} · {}", enmascarar(correo.para), correo.asunto)
            }
        }
    }
}

/** «jorge.ramirez@gmail.com» → «jo***@gmail.com»: para mostrarlo sin exponer el correo completo. */
fun enmascarar(correo: String): String {
    val arroba = correo.indexOf('@')
    if (arroba <= 0) return "***"
    val nombre = correo.substring(0, arroba)
    return nombre.take(2.coerceAtMost(nombre.length)) + "***" + correo.substring(arroba)
}

/** Textos de los correos, en un solo lugar. */
object PlantillasCorreo {
    fun verificacion(nombre: String, codigo: String) = CorreoSaliente(
        para = "",
        asunto = "Tu código para activar PhysioGestion: $codigo",
        texto = """
            Hola, $nombre:

            Tu código para confirmar tu correo en PhysioGestion es: $codigo

            El código vence en 15 minutos. Si no creaste una cuenta, ignora este mensaje.

            Clínica Terapid By Farmacias
        """.trimIndent(),
    )

    fun recuperacion(nombre: String, codigo: String) = CorreoSaliente(
        para = "",
        asunto = "Código para restablecer tu contraseña: $codigo",
        texto = """
            Hola, $nombre:

            Recibimos una solicitud para restablecer tu contraseña de PhysioGestion.
            Tu código es: $codigo

            Vence en 15 minutos y solo se puede usar una vez. Si no lo pediste, ignora
            este mensaje: tu contraseña sigue siendo la misma.

            Clínica Terapid By Farmacias
        """.trimIndent(),
    )

    fun contrasenaCambiada(nombre: String) = CorreoSaliente(
        para = "",
        asunto = "Tu contraseña de PhysioGestion cambió",
        texto = """
            Hola, $nombre:

            La contraseña de tu cuenta de PhysioGestion acaba de cambiar y se cerraron
            tus otras sesiones. Si no fuiste tú, comunícate con la clínica.

            Clínica Terapid By Farmacias
        """.trimIndent(),
    )
}
