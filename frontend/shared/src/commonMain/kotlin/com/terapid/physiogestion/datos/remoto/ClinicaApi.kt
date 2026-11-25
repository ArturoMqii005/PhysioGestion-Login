package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.Anuncio
import com.terapid.physiogestion.modelo.AnuncioSolicitud
import com.terapid.physiogestion.modelo.AsignacionSolicitud
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.CitaSolicitud
import com.terapid.physiogestion.modelo.Ejercicio
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.EstadoCitaSolicitud
import com.terapid.physiogestion.modelo.FichaPaciente
import com.terapid.physiogestion.modelo.FisioterapeutaResumen
import com.terapid.physiogestion.modelo.PacienteResumen
import com.terapid.physiogestion.modelo.NuevaSolicitudReagenda
import com.terapid.physiogestion.modelo.ReagendarSolicitud
import com.terapid.physiogestion.modelo.SolicitudReagenda
import com.terapid.physiogestion.modelo.ReprogramarCitaSolicitud
import com.terapid.physiogestion.modelo.ResolverSolicitud
import com.terapid.physiogestion.modelo.Rutina
import com.terapid.physiogestion.modelo.RutinaSolicitud
import com.terapid.physiogestion.modelo.PruebaEscalerasRegistrada
import com.terapid.physiogestion.modelo.SesionTerapia
import com.terapid.physiogestion.modelo.Usuario
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** CONSUMO DE API: información de la clínica para el personal. */
class ClinicaApi(private val cliente: HttpClient) {

    /** GET /api/v1/usuarios (solo ADMINISTRADOR). */
    suspend fun usuarios(token: String): List<Usuario> =
        llamarApi { cliente.get(ConfiguracionApi.api("usuarios")) { bearerAuth(token) }.body() }

    /** GET /api/v1/citas?fecha= (el fisioterapeuta recibe solo las suyas; el paciente, sus próximas). */
    suspend fun citas(token: String, fecha: String? = null): List<Cita> =
        llamarApi {
            cliente.get(ConfiguracionApi.api("citas")) {
                bearerAuth(token)
                fecha?.let { parameter("fecha", it) }
            }.body()
        }

    /** GET /api/v1/citas/libres: horas de inicio disponibles de un fisioterapeuta. */
    suspend fun horasLibres(token: String, fisioterapeutaId: Int, fecha: String, duracion: Int, excepto: Int?): List<String> =
        llamarApi {
            cliente.get(ConfiguracionApi.api("citas/libres")) {
                bearerAuth(token)
                parameter("fisioterapeutaId", fisioterapeutaId)
                parameter("fecha", fecha)
                parameter("duracion", duracion)
                excepto?.let { parameter("excepto", it) }
            }.body()
        }

    /** GET /api/v1/citas/{id}/libres: horas libres para mover esa cita (con su fisioterapeuta u otro). */
    suspend fun horasLibresDeCita(token: String, id: Int, fecha: String, fisioterapeutaId: Int?): List<String> = llamarApi {
        cliente.get(ConfiguracionApi.api("citas/$id/libres")) {
            bearerAuth(token)
            parameter("fecha", fecha)
            fisioterapeutaId?.let { parameter("fisioterapeutaId", it) }
        }.body()
    }

    /** GET /api/v1/citas/{id}/disponibles: fisioterapeutas libres a la misma hora. */
    suspend fun fisiosDisponibles(token: String, id: Int): List<FisioterapeutaResumen> = llamarApi {
        cliente.get(ConfiguracionApi.api("citas/$id/disponibles")) { bearerAuth(token) }.body()
    }

    /** POST /api/v1/citas/{id}/reagendar (paciente). */
    suspend fun reagendar(token: String, id: Int, s: ReagendarSolicitud): Cita = llamarApi {
        cliente.post(ConfiguracionApi.api("citas/$id/reagendar")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(s)
        }.body()
    }

    /** POST /api/v1/citas/{id}/solicitudes (paciente, cuando faltan menos de 24 h). */
    suspend fun solicitarCambio(token: String, id: Int, s: NuevaSolicitudReagenda): SolicitudReagenda = llamarApi {
        cliente.post(ConfiguracionApi.api("citas/$id/solicitudes")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(s)
        }.body()
    }

    /** POST /api/v1/citas (CIT-05). */
    suspend fun agendar(token: String, s: CitaSolicitud): Cita = llamarApi {
        cliente.post(ConfiguracionApi.api("citas")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(s)
        }.body()
    }

    /** PATCH /api/v1/citas/{id} (CIT-06). */
    suspend fun reprogramar(token: String, id: Int, s: ReprogramarCitaSolicitud): Cita = llamarApi {
        cliente.patch(ConfiguracionApi.api("citas/$id")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(s)
        }.body()
    }

    /** POST /api/v1/citas/{id}/estado (CIT-06, CIT-07). */
    suspend fun cambiarEstadoCita(token: String, id: Int, estado: EstadoCita, nota: String?): Cita = llamarApi {
        cliente.post(ConfiguracionApi.api("citas/$id/estado")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(EstadoCitaSolicitud(estado, nota))
        }.body()
    }

    suspend fun fisioterapeutas(token: String): List<FisioterapeutaResumen> =
        llamarApi { cliente.get(ConfiguracionApi.api("fisioterapeutas")) { bearerAuth(token) }.body() }

    /** GET /api/v1/pacientes (PAC-02). */
    suspend fun pacientes(token: String): List<PacienteResumen> =
        llamarApi { cliente.get(ConfiguracionApi.api("pacientes")) { bearerAuth(token) }.body() }

    /** GET /api/v1/pacientes/{id} (PAC-03). */
    suspend fun ficha(token: String, pacienteId: Int): FichaPaciente =
        llamarApi { cliente.get(ConfiguracionApi.api("pacientes/$pacienteId")) { bearerAuth(token) }.body() }

    /** PATCH /api/v1/pacientes/{id}/fisioterapeuta (PAC-04). */
    suspend fun asignar(token: String, pacienteId: Int, fisioterapeutaId: Int): PacienteResumen = llamarApi {
        cliente.patch(ConfiguracionApi.api("pacientes/$pacienteId/fisioterapeuta")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(AsignacionSolicitud(fisioterapeutaId))
        }.body()
    }

    /** GET /api/v1/ejercicios: catálogo para armar rutinas. */
    suspend fun catalogo(token: String): List<Ejercicio> =
        llamarApi { cliente.get(ConfiguracionApi.api("ejercicios")) { bearerAuth(token) }.body() }

    /** PUT /api/v1/pacientes/{id}/rutina (RUT-08). */
    suspend fun guardarRutina(token: String, pacienteId: Int, s: RutinaSolicitud): Rutina = llamarApi {
        cliente.put(ConfiguracionApi.api("pacientes/$pacienteId/rutina")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(s)
        }.body()
    }

    /** GET /api/v1/sesiones (FISIOTERAPEUTA): últimas sesiones que enviaron sus pacientes desde casa. */
    suspend fun sesionesDeMisPacientes(token: String, tamano: Int = 10): List<SesionTerapia> =
        llamarApi {
            cliente.get(ConfiguracionApi.api("sesiones")) {
                bearerAuth(token)
                parameter("tamano", tamano)
            }.body()
        }

    /** GET /api/v1/pruebas-escaleras (FISIOTERAPEUTA): pruebas de escaleras de sus pacientes. */
    suspend fun pruebasEscaleras(token: String): List<PruebaEscalerasRegistrada> =
        llamarApi { cliente.get(ConfiguracionApi.api("pruebas-escaleras")) { bearerAuth(token) }.body() }

    /** GET /api/v1/solicitudes-reagenda?estado= (recepción y administración ven las de toda la clínica). */
    suspend fun solicitudes(token: String, estado: String?): List<SolicitudReagenda> = llamarApi {
        cliente.get(ConfiguracionApi.api("solicitudes-reagenda")) {
            bearerAuth(token)
            estado?.let { parameter("estado", it) }
        }.body()
    }

    /** POST /api/v1/solicitudes-reagenda/{id}/resolver: aprobar (mueve la cita) o rechazar. */
    suspend fun resolverSolicitud(token: String, id: Int, s: ResolverSolicitud): SolicitudReagenda = llamarApi {
        cliente.post(ConfiguracionApi.api("solicitudes-reagenda/$id/resolver")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(s)
        }.body()
    }

    /** GET /api/v1/anuncios (recepción y administración): los más recientes primero. */
    suspend fun anuncios(token: String): List<Anuncio> =
        llamarApi { cliente.get(ConfiguracionApi.api("anuncios")) { bearerAuth(token) }.body() }

    /** POST /api/v1/anuncios: se manda como push y queda en la bandeja de avisos. */
    suspend fun publicarAnuncio(token: String, s: AnuncioSolicitud): Anuncio = llamarApi {
        cliente.post(ConfiguracionApi.api("anuncios")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(s)
        }.body()
    }
}
