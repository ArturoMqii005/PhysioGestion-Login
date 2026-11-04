package com.terapid.physiogestion.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * CORS: permite que una versión web de la app consuma esta API (SRV-04).
 *
 * VARIABLE DE ENTORNO CORS_ORIGENES: lista separada por comas de los dominios
 * permitidos («https://physiogestion.web.app»). Si no existe (desarrollo local),
 * se acepta cualquier origen. La app móvil no usa CORS: solo aplica a navegadores.
 */
fun Application.configureHttp(origenes: List<String> = origenesPermitidos()) {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("Idempotency-Key")
        exposeHeader(HttpHeaders.Location)
        exposeHeader("X-Total-Count")
        if (origenes.isEmpty()) {
            anyHost()
        } else {
            origenes.forEach { origen ->
                val esquema = origen.substringBefore("://", "https")
                allowHost(origen.substringAfter("://").trimEnd('/'), schemes = listOf(esquema))
            }
        }
    }
}

fun origenesPermitidos(): List<String> =
    System.getenv("CORS_ORIGENES").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
