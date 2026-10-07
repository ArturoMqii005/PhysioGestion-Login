package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.plataforma.crearMotorHttp
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cliente HTTP compartido (Ktor Client).
 *
 * Conexión con el serializador: el plugin ContentNegotiation usa
 * kotlinx.serialization para convertir los objetos @Serializable a JSON al
 * enviar (setBody) y el JSON recibido a objetos al leer (body<T>()).
 * El motor HTTP es lo único que cambia por plataforma.
 */
fun crearClienteHttp(json: Json, motor: HttpClientEngine = crearMotorHttp()): HttpClient =
    HttpClient(motor) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 15_000
        }
        // Respuestas 4xx y 5xx se convierten en excepción; ErroresApi.kt las traduce.
        expectSuccess = true
    }
