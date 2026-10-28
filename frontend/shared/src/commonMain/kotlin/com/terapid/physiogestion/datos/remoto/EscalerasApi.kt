package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.PruebaEscalerasRegistrada
import com.terapid.physiogestion.modelo.PruebaEscalerasSolicitud
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post

/**
 * CONSUMO DE API · prueba de escaleras. Igual que con los ejercicios, solo
 * viaja el resumen (altura, pisos, escalones, pasos y duración), nunca las
 * lecturas del barómetro.
 */
class EscalerasApi(private val cliente: HttpClient) {

    /** POST /pruebas-escaleras -> 201 Created */
    suspend fun registrar(solicitud: PruebaEscalerasSolicitud, token: String): Respuesta<PruebaEscalerasRegistrada> =
        llamarApi {
            val respuesta = cliente.post(ConfiguracionApi.api("pruebas-escaleras")) {
                bearerAuth(token)
                cuerpoJson(solicitud)
            }
            Respuesta(respuesta.body(), respuesta.detalle())
        }

    /** GET /pruebas-escaleras: el paciente ve las suyas; el fisioterapeuta, las de sus pacientes. */
    suspend fun listar(token: String): List<PruebaEscalerasRegistrada> =
        llamarApi { cliente.get(ConfiguracionApi.api("pruebas-escaleras")) { bearerAuth(token) }.body() }
}
