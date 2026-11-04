package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.BandejaAvisos
import com.terapid.physiogestion.modelo.MarcarLeidosSolicitud
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** CONSUMO DE API: avisos del usuario (no usa Firebase: la app los pide al servidor). */
class AvisosApi(private val cliente: HttpClient) {

    /** GET /api/v1/avisos?desde= (con [desde], solo los avisos de id mayor). */
    suspend fun bandeja(token: String, desde: Int? = null): BandejaAvisos = llamarApi {
        cliente.get(ConfiguracionApi.api("avisos")) {
            bearerAuth(token)
            desde?.let { parameter("desde", it) }
        }.body()
    }

    /** POST /api/v1/avisos/leidos. */
    suspend fun marcarLeidos(token: String, solicitud: MarcarLeidosSolicitud): BandejaAvisos = llamarApi {
        cliente.post(ConfiguracionApi.api("avisos/leidos")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(solicitud)
        }.body()
    }
}
