package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.ResultadoEjercicioSolicitud
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post

/**
 * CONSUMO DE API · envío del resumen medido con sensores.
 *
 * Usa POST porque cada ejecución de un ejercicio crea un registro nuevo.
 * No viaja ninguna lectura del acelerómetro ni del giroscopio: una sesión de
 * diez minutos produce más de sesenta mil lecturas y aquí viajan seis campos.
 */
class EjercicioApi(private val cliente: HttpClient) {

    /** POST /resultados-ejercicio -> 201 Created */
    suspend fun registrarResultado(solicitud: ResultadoEjercicioSolicitud, token: String): Respuesta<Unit> = llamarApi {
        val respuesta = cliente.post(ConfiguracionApi.api("resultados-ejercicio")) {
            bearerAuth(token)
            cuerpoJson(solicitud)
        }
        Respuesta(Unit, respuesta.detalle())
    }
}
