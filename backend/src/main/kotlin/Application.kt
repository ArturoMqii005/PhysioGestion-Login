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
import com.terapid.physiogestion.servicios.AutenticacionServicio
import com.terapid.physiogestion.servicios.AvisosServicio
import com.terapid.physiogestion.servicios.ClinicaServicio
import com.terapid.physiogestion.servicios.CorreoEnRegistro
import com.terapid.physiogestion.servicios.EnviadorCorreo
import com.terapid.physiogestion.servicios.SesionesServicio
import io.ktor.server.application.Application
import kotlinx.serialization.json.Json
import java.time.Clock

/**
 * Módulo principal del servidor (lo indica application.yaml). Toma la
 * configuración de las variables de entorno: proveedor de correo y base de datos.
 */
fun Application.module() {
    modulo(correo = EnviadorCorreo.desdeEntorno(), almacenDocumentos = AlmacenJdbc.desdeEntorno())
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

    configureMonitoreo()
    configureHttp()
    configureSerialization(json)
    configureStatusPages()
    configureLimiteSolicitudes(limitePorMinuto)
    configureSeguridad(autenticacion)
    configureRouting(almacen, autenticacion, sesiones, clinica, avisos, almacenDocumentos.tipo)
}
