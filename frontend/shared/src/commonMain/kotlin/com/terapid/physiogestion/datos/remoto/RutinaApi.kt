package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.Rutina
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import kotlinx.serialization.json.Json

/**
 * CONSUMO DE API: descarga la rutina vigente del paciente y la convierte
 * en el modelo [Rutina]. También la pasa a texto JSON para guardarla en SQLite.
 */
class RutinaApi(
    private val cliente: HttpClient,
    private val json: Json,
) {
    /** GET /api/v1/pacientes/{id}/rutina */
    suspend fun descargarRutinaVigente(pacienteId: Int, token: String): Rutina =
        llamarApi { cliente.get(ConfiguracionApi.api("pacientes/$pacienteId/rutina")) { bearerAuth(token) }.body() }

    /** Rutina -> texto JSON (para la copia local). */
    fun aJson(rutina: Rutina): String = json.encodeToString(Rutina.serializer(), rutina)

    /** Texto JSON -> Rutina (al leer la copia local). */
    fun leerRutina(texto: String): Rutina = json.decodeFromString(Rutina.serializer(), texto)
}
