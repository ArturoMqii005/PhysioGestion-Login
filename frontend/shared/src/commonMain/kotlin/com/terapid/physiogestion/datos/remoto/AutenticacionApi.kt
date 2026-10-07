package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.LoginRespuesta
import com.terapid.physiogestion.modelo.LoginSolicitud
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** CONSUMO DE API: inicio y cierre de sesión contra el backend Ktor. */
class AutenticacionApi(private val cliente: HttpClient) {

    /** POST /api/v1/auth/login con {"usuario": "...", "contrasena": "..."}. */
    suspend fun iniciarSesion(usuario: String, contrasena: String): LoginRespuesta =
        llamarApi(esLogin = true) {
            cliente.post(ConfiguracionApi.api("auth/login")) {
                contentType(ContentType.Application.Json)
                setBody(LoginSolicitud(usuario.trim(), contrasena)) // objeto -> JSON
            }.body() // JSON -> LoginRespuesta
        }

    /** POST /api/v1/auth/logout: invalida el token en el servidor. */
    suspend fun cerrarSesion(token: String) {
        llamarApi { cliente.post(ConfiguracionApi.api("auth/logout")) { bearerAuth(token) } }
    }
}
