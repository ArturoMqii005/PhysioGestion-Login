package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.crearClienteHttp
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

val jsonPrueba = Json { ignoreUnknownKeys = true }

/** Cliente HTTP real de la app, pero con un servidor simulado (MockEngine). */
fun clienteSimulado(manejador: MockRequestHandler): HttpClient =
    crearClienteHttp(jsonPrueba, MockEngine(manejador))

/** Respuesta JSON del servidor simulado. */
fun MockRequestHandleScope.json(texto: String, estado: HttpStatusCode = HttpStatusCode.OK) =
    respond(texto, estado, headersOf(HttpHeaders.ContentType, "application/json"))
