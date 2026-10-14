package com.terapid.physiogestion.dependencias

import com.terapid.physiogestion.datos.AutenticacionRepositorio
import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.datos.GestorSesion
import com.terapid.physiogestion.datos.PreferenciasRepositorio
import com.terapid.physiogestion.datos.RutinaRepositorio
import com.terapid.physiogestion.datos.SensorRepositorio
import com.terapid.physiogestion.datos.SesionesRepositorio
import com.terapid.physiogestion.datos.local.AlmacenArchivos
import com.terapid.physiogestion.datos.local.AlmacenPreferencias
import com.terapid.physiogestion.datos.local.AlmacenSeguro
import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.local.BaseDatosLocal
import com.terapid.physiogestion.datos.local.PersistenciaSesion
import com.terapid.physiogestion.datos.remoto.AutenticacionApi
import com.terapid.physiogestion.datos.remoto.ClinicaApi
import com.terapid.physiogestion.datos.remoto.ConfiguracionApi
import com.terapid.physiogestion.datos.remoto.EjercicioApi
import com.terapid.physiogestion.datos.remoto.RutinaApi
import com.terapid.physiogestion.datos.remoto.SesionApi
import com.terapid.physiogestion.datos.remoto.crearClienteHttp
import com.terapid.physiogestion.datos.sensores.SensorMovimiento
import com.terapid.physiogestion.datos.sensores.SensorPasos
import com.terapid.physiogestion.plataforma.permisos.GestorPermisos
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

    // ---- Almacenamiento local: base de datos, preferencias, archivos y almacén cifrado ----
    private val baseDatos by lazy { BaseDatosLocal(rutaBaseDatos("physiogestion.db")) }
    val almacenamiento: AlmacenamientoLocal by lazy { AlmacenamientoLocal(baseDatos, json) }
    private val almacenPreferencias by lazy { AlmacenPreferencias() }
    val archivos: AlmacenArchivos by lazy { AlmacenArchivos() }

    val servicio: SeguimientoServicio by lazy { SeguimientoServicio() }

    val preferencias: PreferenciasRepositorio by lazy { PreferenciasRepositorio(almacenPreferencias, servicio) }

    private val gestorSesion by lazy {
        // Si ya se inició sesión antes, se usa el mismo servidor de esa vez (antes de restaurar la sesión).
        preferencias.urlServidorGuardada()?.let { ConfiguracionApi.urlServidor = it }
        GestorSesion(PersistenciaSesion(almacenPreferencias, AlmacenSeguro(), json))
    }

    // ---- Repositorios ----
    val autenticacion: AutenticacionRepositorio by lazy {
        AutenticacionRepositorio(AutenticacionApi(clienteHttp), gestorSesion)
    }

    val clinica: ClinicaRepositorio by lazy { ClinicaRepositorio(ClinicaApi(clienteHttp), gestorSesion) }

    val rutinas: RutinaRepositorio by lazy {
        RutinaRepositorio(RutinaApi(clienteHttp, json), almacenamiento, gestorSesion)
    }

    val sesiones: SesionesRepositorio by lazy { SesionesRepositorio(SesionApi(clienteHttp), almacenamiento, gestorSesion) }

    val sensores: SensorRepositorio by lazy { SensorRepositorio(EjercicioApi(clienteHttp), almacenamiento, gestorSesion) }

    // ---- Plataforma: permisos y sensores ----
    val permisos: GestorPermisos by lazy { GestorPermisos() }

    val sensorPasos: SensorPasos by lazy { SensorPasos() }

    /** Un sensor por pantalla de medición: se apaga cuando esa pantalla se cierra. */
    fun nuevoSensorMovimiento(): SensorMovimiento = SensorMovimiento()
}
