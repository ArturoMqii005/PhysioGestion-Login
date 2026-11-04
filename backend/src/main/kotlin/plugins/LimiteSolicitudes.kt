package com.terapid.physiogestion.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes

/** Límite para las rutas públicas de cuentas (login, registro, recuperación). */
val LIMITE_CUENTAS = RateLimitName("cuentas")

/**
 * LIMITACIÓN DE PETICIONES (SRV-10): cada dirección IP puede hacer como máximo
 * 30 peticiones por minuto a /auth. Frena los ataques de fuerza bruta y los
 * envíos masivos de códigos. Al pasarse, Ktor responde 429 con Retry-After.
 *
 * Railway pasa la IP real del cliente en X-Forwarded-For (el servidor solo ve el proxy).
 */
fun Application.configureLimiteSolicitudes(porMinuto: Int = 30) {
    install(RateLimit) {
        register(LIMITE_CUENTAS) {
            rateLimiter(limit = porMinuto, refillPeriod = 1.minutes)
            requestKey { call -> call.ipCliente() }
        }
    }
}

fun ApplicationCall.ipCliente(): String =
    request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.origin.remoteHost
