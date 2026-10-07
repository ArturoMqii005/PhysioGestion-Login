package com.terapid.physiogestion.dependencias

import com.terapid.physiogestion.datos.AutenticacionRepositorio
import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.datos.GestorSesion
import com.terapid.physiogestion.datos.RutinaRepositorio
import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.local.BaseDatosLocal
import com.terapid.physiogestion.datos.remoto.AutenticacionApi
import com.terapid.physiogestion.datos.remoto.ClinicaApi
import com.terapid.physiogestion.datos.remoto.RutinaApi
import com.terapid.physiogestion.datos.remoto.crearClienteHttp
import com.terapid.physiogestion.plataforma.rutaBaseDatos
import com.terapid.physiogestion.servicio.SeguimientoServicio
import kotlinx.serialization.json.Json

/**
 * Punto único donde se crean y conectan las piezas de la app
 * (inyección de dependencias manual). Cada objeto se crea una sola vez.
 */
object Dependencias {
    /** Configuración del serializador, compartida por el cliente HTTP y la copia local. */
    private val json = Json { ignoreUnknownKeys = true }

    private val clienteHttp by lazy { crearClienteHttp(json) }

    private val baseDatos by lazy { BaseDatosLocal(rutaBaseDatos("physiogestion.db")) }

    private val gestorSesion by lazy { GestorSesion() }

    val autenticacion: AutenticacionRepositorio by lazy {
        AutenticacionRepositorio(AutenticacionApi(clienteHttp), gestorSesion)
    }

    val clinica: ClinicaRepositorio by lazy { ClinicaRepositorio(ClinicaApi(clienteHttp), gestorSesion) }

    val rutinas: RutinaRepositorio by lazy {
        RutinaRepositorio(RutinaApi(clienteHttp, json), AlmacenamientoLocal(baseDatos), gestorSesion)
    }

    val servicio: SeguimientoServicio by lazy { SeguimientoServicio() }
}
