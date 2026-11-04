package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.modelo.AltaUsuarioRespuesta
import com.terapid.physiogestion.modelo.AltaUsuarioSolicitud
import com.terapid.physiogestion.modelo.AvisoPrivacidad
import com.terapid.physiogestion.modelo.CambiarContrasenaSolicitud
import com.terapid.physiogestion.modelo.ConsentimientoSolicitud
import com.terapid.physiogestion.modelo.CorreoSolicitud
import com.terapid.physiogestion.modelo.EstadoCuentaSolicitud
import com.terapid.physiogestion.modelo.Invitacion
import com.terapid.physiogestion.modelo.InvitacionSolicitud
import com.terapid.physiogestion.modelo.MensajeRespuesta
import com.terapid.physiogestion.modelo.RegistroRespuesta
import com.terapid.physiogestion.modelo.RegistroSolicitud
import com.terapid.physiogestion.modelo.RestablecerSolicitud
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.modelo.VerificarCorreoSolicitud
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * CONSUMO DE API de las cuentas: registro, verificación del correo,
 * recuperación y restablecimiento de la contraseña, aviso de privacidad,
 * invitaciones y alta del personal.
 */
class CuentaApi(private val cliente: HttpClient) {

    // ---- Públicas (sin token) ----

    suspend fun registrar(s: RegistroSolicitud): RegistroRespuesta = enviar("auth/registro", s)

    suspend fun verificarCorreo(correo: String, codigo: String): MensajeRespuesta =
        enviar("auth/verificar-correo", VerificarCorreoSolicitud(correo.trim(), codigo.trim()))

    suspend fun reenviarVerificacion(correo: String): MensajeRespuesta = enviar("auth/reenviar-verificacion", CorreoSolicitud(correo.trim()))

    suspend fun solicitarRecuperacion(correo: String): MensajeRespuesta = enviar("auth/recuperacion", CorreoSolicitud(correo.trim()))

    suspend fun restablecer(correo: String, codigo: String, nueva: String): MensajeRespuesta =
        enviar("auth/restablecer", RestablecerSolicitud(correo.trim(), codigo.trim(), nueva))

    suspend fun avisoPrivacidad(): AvisoPrivacidad = llamarApi { cliente.get(ConfiguracionApi.api("aviso-privacidad")).body() }

    // ---- Con token ----

    suspend fun aceptarAviso(version: String, token: String): MensajeRespuesta =
        enviar("usuarios/yo/consentimiento", ConsentimientoSolicitud(version), token)

    suspend fun cambiarContrasena(actual: String, nueva: String, token: String): MensajeRespuesta = llamarApi {
        cliente.patch(ConfiguracionApi.api("usuarios/yo/contrasena")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CambiarContrasenaSolicitud(actual, nueva))
        }.body()
    }

    suspend fun invitaciones(token: String): List<Invitacion> =
        llamarApi { cliente.get(ConfiguracionApi.api("invitaciones")) { bearerAuth(token) }.body() }

    suspend fun crearInvitacion(nombrePaciente: String, token: String): Invitacion =
        enviar("invitaciones", InvitacionSolicitud(nombrePaciente.trim()), token)

    suspend fun darDeAlta(s: AltaUsuarioSolicitud, token: String): AltaUsuarioRespuesta = enviar("usuarios", s, token)

    suspend fun editarUsuario(id: Int, cambios: EstadoCuentaSolicitud, token: String): Usuario = llamarApi {
        cliente.patch(ConfiguracionApi.api("usuarios/$id")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(cambios)
        }.body()
    }

    private suspend inline fun <reified S, reified R> enviar(ruta: String, cuerpo: S, token: String? = null): R = llamarApi {
        cliente.post(ConfiguracionApi.api(ruta)) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }.body()
    }
}
