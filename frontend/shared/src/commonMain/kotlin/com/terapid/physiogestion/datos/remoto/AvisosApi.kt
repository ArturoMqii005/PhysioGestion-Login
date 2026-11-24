package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.BandejaAvisos
import com.terapid.physiogestion.modelo.DispositivoRegistrado
import com.terapid.physiogestion.modelo.DispositivoSolicitud
import com.terapid.physiogestion.modelo.MarcarLeidosSolicitud
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart

/**
 * CONSUMO DE API: avisos del usuario. El servidor los manda como push de Firebase
 * (solo el tipo y el id) y la app los pide aquí para mostrar el texto.
 */
class AvisosApi(private val cliente: HttpClient) {

    /** GET /api/v1/avisos?desde= (con [desde], solo los avisos de id mayor). */
    suspend fun bandeja(token: String, desde: Int? = null): BandejaAvisos = llamarApi {
        cliente.get(ConfiguracionApi.api("avisos")) {
            bearerAuth(token)
            desde?.let { parameter("desde", it) }
        }.body()
    }

    /** POST /api/v1/dispositivos: registra el token de Firebase de este teléfono. */
    suspend fun registrarDispositivo(token: String, solicitud: DispositivoSolicitud): DispositivoRegistrado = llamarApi {
        cliente.post(ConfiguracionApi.api("dispositivos")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(solicitud)
        }.body()
    }

    /** DELETE /api/v1/dispositivos/{token}: al cerrar sesión deja de recibir push de esta cuenta. */
    suspend fun quitarDispositivo(token: String, tokenPush: String) {
        llamarApi { cliente.delete(ConfiguracionApi.api("dispositivos/" + tokenPush.encodeURLPathPart())) { bearerAuth(token) } }
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
