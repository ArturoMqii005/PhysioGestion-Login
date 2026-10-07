package com.terapid.physiogestion.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * Conexión con el serializador: ContentNegotiation usa kotlinx.serialization
 * para convertir automáticamente los objetos @Serializable a JSON en cada
 * respuesta (call.respond) y el JSON recibido a objetos (call.receive).
 */
fun Application.configureSerialization(json: Json) {
    install(ContentNegotiation) {
        json(json)
    }
}
