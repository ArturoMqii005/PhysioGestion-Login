package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.plataforma.Entorno
import com.terapid.physiogestion.plataforma.crearMotorHttp
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cliente HTTP compartido (Ktor Client).
 *
 * Conexión con el serializador: el plugin ContentNegotiation usa
 * kotlinx.serialization para convertir los objetos @Serializable a JSON al
 * enviar (setBody) y el JSON recibido a objetos al leer (body<T>()).
 * El motor HTTP es lo único que cambia por plataforma.
 *
 * Los tiempos de espera y el registro de peticiones salen de [Entorno].
 */
fun crearClienteHttp(
    json: Json,
    motor: HttpClientEngine = crearMotorHttp(),
    registro: Boolean = Entorno.registroActivo,
): HttpClient =
    HttpClient(motor) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = Entorno.tiempoConexionMs
            requestTimeoutMillis = Entorno.tiempoPeticionMs
        }
        if (registro) {
            // Método, ruta y código de cada petición en Logcat (filtrar por "PhysioGestion HTTP").
            install(Logging) {
                level = LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) = println("PhysioGestion HTTP · $message")
                }
                // El token nunca se escribe en el registro.
                sanitizeHeader { it == HttpHeaders.Authorization }
            }
        }
        // Respuestas 4xx y 5xx se convierten en excepción; ErroresApi.kt las traduce.
        expectSuccess = true
    }
