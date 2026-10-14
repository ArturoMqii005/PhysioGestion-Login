package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.CrearSesionSolicitud
import com.terapid.physiogestion.modelo.ModificarSesionSolicitud
import com.terapid.physiogestion.modelo.PaginaSesiones
import com.terapid.physiogestion.modelo.ReemplazarSesionSolicitud
import com.terapid.physiogestion.modelo.SesionTerapia
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** Encabezado con el que el servidor reconoce un POST repetido y no duplica la sesión. */
const val ENCABEZADO_IDEMPOTENCIA = "Idempotency-Key"

/**
 * CONSUMO DE API · los cinco métodos HTTP sobre el mismo recurso (/sesiones).
 *
 *  GET     consultar sin modificar nada        seguro e idempotente
 *  POST    crear un recurso nuevo              ni seguro ni idempotente (salvo con Idempotency-Key)
 *  PUT     reemplazar el recurso completo      idempotente
 *  PATCH   modificar solo una parte            parcial
 *  DELETE  eliminar                            idempotente (la segunda vez responde 404)
 */
class SesionApi(private val cliente: HttpClient) {

    // ---------------------------------------------------------------- GET

    /** GET /sesiones?idPaciente=1&pagina=0&tamano=20 — historial del paciente (RFN-31). */
    suspend fun listar(idPaciente: Int, token: String, pagina: Int = 0, tamano: Int = 20): Respuesta<PaginaSesiones> =
        llamarApi {
            val respuesta = cliente.get(ConfiguracionApi.api("sesiones")) {
                bearerAuth(token)
                // Los parámetros de consulta filtran y paginan el listado.
                parameter("idPaciente", idPaciente)
                parameter("pagina", pagina)
                parameter("tamano", tamano)
            }
            val sesiones = respuesta.body<List<SesionTerapia>>()
            val total = respuesta.headers["X-Total-Count"]?.toIntOrNull() ?: sesiones.size
            Respuesta(PaginaSesiones(sesiones, total), respuesta.detalle())
        }

    /** GET /sesiones/{id} — una sesión identificada en la propia ruta. */
    suspend fun obtener(idSesion: Int, token: String): Respuesta<SesionTerapia> = llamarApi {
        val respuesta = cliente.get(ConfiguracionApi.api("sesiones/$idSesion")) { bearerAuth(token) }
        Respuesta(respuesta.body(), respuesta.detalle())
    }

    // ---------------------------------------------------------------- POST

    /**
     * POST /sesiones — registra una sesión terminada (RFN-29). El servidor
     * responde 201 Created y en Location la dirección del recurso nuevo.
     *
     * Con [claveIdempotencia], si la conexión se cae y la app reintenta, el
     * servidor reconoce la clave, responde 200 con la sesión ya creada y no la duplica.
     */
    suspend fun registrar(
        solicitud: CrearSesionSolicitud,
        claveIdempotencia: String,
        token: String,
    ): Respuesta<SesionTerapia> = llamarApi {
        val respuesta = cliente.post(ConfiguracionApi.api("sesiones")) {
            bearerAuth(token)
            header(ENCABEZADO_IDEMPOTENCIA, claveIdempotencia)
            cuerpoJson(solicitud)
        }
        Respuesta(respuesta.body(), respuesta.detalle())
    }

    // ---------------------------------------------------------------- PUT

    /** PUT /sesiones/{id} — sustituye TODOS los datos editables; lo que va nulo queda vacío. */
    suspend fun reemplazar(idSesion: Int, solicitud: ReemplazarSesionSolicitud, token: String): Respuesta<SesionTerapia> =
        llamarApi {
            val respuesta = cliente.put(ConfiguracionApi.api("sesiones/$idSesion")) {
                bearerAuth(token)
                cuerpoJson(solicitud)
            }
            Respuesta(respuesta.body(), respuesta.detalle())
        }

    // ---------------------------------------------------------------- PATCH

    /** PATCH /sesiones/{id} — solo viajan los campos no nulos; el servidor conserva los demás. */
    suspend fun modificar(idSesion: Int, solicitud: ModificarSesionSolicitud, token: String): Respuesta<SesionTerapia> =
        llamarApi {
            val respuesta = cliente.patch(ConfiguracionApi.api("sesiones/$idSesion")) {
                bearerAuth(token)
                cuerpoJson(solicitud)
            }
            Respuesta(respuesta.body(), respuesta.detalle())
        }

    // ---------------------------------------------------------------- DELETE

    /** DELETE /sesiones/{id} — elimina una sesión registrada por error. Responde 204 No Content. */
    suspend fun eliminar(idSesion: Int, token: String): Respuesta<Unit> = llamarApi {
        val respuesta = cliente.delete(ConfiguracionApi.api("sesiones/$idSesion")) { bearerAuth(token) }
        Respuesta(Unit, respuesta.detalle())
    }
}

/** Cuerpo JSON: el serializador convierte el objeto @Serializable a texto JSON. */
internal inline fun <reified T> HttpRequestBuilder.cuerpoJson(cuerpo: T) {
    contentType(ContentType.Application.Json)
    setBody(cuerpo)
}
