package com.terapid.physiogestion.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/** CORS: permite que una futura versión web de la app consuma esta API. */
fun Application.configureHttp() {
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
        anyHost() // Solo para la etapa local; en producción se limita al dominio de la clínica.
    }
}
