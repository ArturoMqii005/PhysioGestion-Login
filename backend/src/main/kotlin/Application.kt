package com.terapid.physiogestion

import com.terapid.physiogestion.datos.AlmacenDocumentos
import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.datos.AlmacenJdbc
import com.terapid.physiogestion.datos.AlmacenJson
import com.terapid.physiogestion.plugins.configureHttp
import com.terapid.physiogestion.plugins.configureLimiteSolicitudes
import com.terapid.physiogestion.plugins.configureMonitoreo
import com.terapid.physiogestion.plugins.configureSeguridad
import com.terapid.physiogestion.plugins.configureSerialization
import com.terapid.physiogestion.plugins.configureStatusPages
import com.terapid.physiogestion.rutas.configureRouting
import com.terapid.physiogestion.servicios.AnunciosServicio
import com.terapid.physiogestion.servicios.AutenticacionServicio
import com.terapid.physiogestion.servicios.AvisosServicio
import com.terapid.physiogestion.servicios.ClinicaServicio
import com.terapid.physiogestion.servicios.CorreoEnRegistro
import com.terapid.physiogestion.servicios.EnviadorCorreo
import com.terapid.physiogestion.servicios.EnviadorPush
import com.terapid.physiogestion.servicios.PushEnRegistro
import com.terapid.physiogestion.servicios.PushServicio
import com.terapid.physiogestion.servicios.RevisionDiariaServicio
import com.terapid.physiogestion.servicios.SesionesServicio
import io.ktor.server.application.Application
import kotlinx.serialization.json.Json
import java.time.Clock
import java.util.concurrent.Executor

/**
 * Módulo principal del servidor (lo indica application.yaml). Toma la
 * configuración de las variables de entorno: proveedor de correo, base de datos,
 * Firebase (FIREBASE_CREDENCIALES) y la clave de las tareas programadas (TAREA_CLAVE).
 */
fun Application.module() {
    println("Despliegue de prueba: PhysioGestion Backend iniciado.")
    modulo(
        correo = EnviadorCorreo.desdeEntorno(),
        almacenDocumentos = AlmacenJdbc.desdeEntorno(),
        enviadorPush = EnviadorPush.desdeEntorno(),
        claveTareas = System.getenv("TAREA_CLAVE")?.takeIf { it.isNotBlank() },
    )
}

/**
 * Crea los objetos compartidos e instala los plugins en orden. Las pruebas lo
 * llaman con un correo simulado y un reloj fijo para leer los códigos y
 * adelantar el tiempo.
 */
fun Application.modulo(
    correo: EnviadorCorreo = CorreoEnRegistro(),
    reloj: Clock = Clock.systemUTC(),
    almacenDocumentos: AlmacenDocumentos = AlmacenEnMemoria(),
    limitePorMinuto: Int = 30,
    enviadorPush: EnviadorPush = PushEnRegistro(),
    claveTareas: String? = null,
    /** Las pruebas mandan las push en el mismo hilo para revisarlas enseguida. */
    ejecutorPush: Executor? = null,
) {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    val almacen = AlmacenJson(json)
    val autenticacion = AutenticacionServicio(
        usuariosIniciales = almacen.usuarios,
        invitacionesIniciales = almacen.invitaciones,
        correo = correo,
        reloj = reloj,
        almacen = almacenDocumentos,
        json = json,
    )
    val avisos = AvisosServicio(autenticacion, reloj, almacenDocumentos, json)
    val clinica = ClinicaServicio(
        rutinasIniciales = almacen.rutinas,
        citasIniciales = almacen.citas,
        catalogo = almacen.ejercicios,
        cuentas = autenticacion,
        reloj = reloj,
        almacen = almacenDocumentos,
        json = json,
        avisos = avisos,
    )
    val sesiones = SesionesServicio(clinica::rutinas, almacen.sesiones, almacenDocumentos, json) { sesion ->
        // NOT-03: al fisioterapeuta que atiende al paciente.
        avisos.dolorAlto(sesion, clinica.rutinaDe(sesion.pacienteId), clinica.asignacionDe(sesion.pacienteId)?.fisioterapeutaId)
    }

    val push = if (ejecutorPush != null) {
        PushServicio(autenticacion, avisos, enviadorPush, reloj, almacenDocumentos, json, ejecutor = ejecutorPush)
    } else {
        PushServicio(autenticacion, avisos, enviadorPush, reloj, almacenDocumentos, json)
    }
    // Cada aviso guardado sale también como push a los teléfonos de quien lo recibe.
    avisos.alCrear = push::enviarAviso
    autenticacion.alCambiarContrasena = { usuarioId, porRecuperacion ->
        avisos.seguridad(
            usuarioId,
            if (porRecuperacion) "Restablecieron tu contraseña" else "Tu contraseña cambió",
            if (porRecuperacion) "Se usó el código de tu correo para poner una contraseña nueva. Si no fuiste tú, avisa a la clínica."
            else "Se cambió la contraseña de tu cuenta y cerramos tus otras sesiones. Si no fuiste tú, avisa a la clínica.",
        )
    }
    val anuncios = AnunciosServicio(autenticacion, avisos, push, reloj, almacenDocumentos, json)
    val revision = RevisionDiariaServicio(autenticacion, clinica, sesiones, avisos, reloj)

    configureMonitoreo()
    configureHttp()
    configureSerialization(json)
    configureStatusPages()
    configureLimiteSolicitudes(limitePorMinuto)
    configureSeguridad(autenticacion)
    configureRouting(almacen, autenticacion, sesiones, clinica, avisos, push, anuncios, revision, claveTareas, almacenDocumentos.tipo)
}
