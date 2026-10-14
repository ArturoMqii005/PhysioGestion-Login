package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.SesionTerapia
import com.terapid.physiogestion.modelo.Usuario
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** CONSUMO DE API: información de la clínica para el personal. */
class ClinicaApi(private val cliente: HttpClient) {

    /** GET /api/v1/usuarios (solo ADMINISTRADOR). */
    suspend fun usuarios(token: String): List<Usuario> =
        llamarApi { cliente.get(ConfiguracionApi.api("usuarios")) { bearerAuth(token) }.body() }

    /** GET /api/v1/citas (el fisioterapeuta recibe solo las suyas). */
    suspend fun citas(token: String): List<Cita> =
        llamarApi { cliente.get(ConfiguracionApi.api("citas")) { bearerAuth(token) }.body() }

    /** GET /api/v1/sesiones (FISIOTERAPEUTA): últimas sesiones que enviaron sus pacientes desde casa. */
    suspend fun sesionesDeMisPacientes(token: String, tamano: Int = 10): List<SesionTerapia> =
        llamarApi {
            cliente.get(ConfiguracionApi.api("sesiones")) {
                bearerAuth(token)
                parameter("tamano", tamano)
            }.body()
        }
}
