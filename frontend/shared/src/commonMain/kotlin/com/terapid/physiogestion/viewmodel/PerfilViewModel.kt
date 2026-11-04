package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.PreferenciasRepositorio
import com.terapid.physiogestion.datos.local.AlmacenArchivos
import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.local.VERSION_ESQUEMA
import com.terapid.physiogestion.datos.remoto.ConfiguracionApi
import com.terapid.physiogestion.plataforma.Entorno
import com.terapid.physiogestion.plataforma.Notificador
import com.terapid.physiogestion.plataforma.SinNotificaciones
import com.terapid.physiogestion.servicio.ReglasAvisos
import com.terapid.physiogestion.plataforma.Plataforma
import com.terapid.physiogestion.plataforma.permisos.GestorPermisos
import com.terapid.physiogestion.plataforma.permisos.Permiso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PerfilUiState(
    // Variables de entorno
    val ambiente: String = "",
    val plataforma: String = "",
    val version: String = "",
    val servidor: String = "",
    val servidorRecordado: String? = null,
    // Almacenamiento local
    val sesionesGuardadas: Int = 0,
    val pendientesEnvio: Int = 0,
    val mediciones: Int = 0,
    val versionEsquema: Int = VERSION_ESQUEMA,
    val cacheBytes: Long = 0,
    val cacheVideos: Int = 0,
    val carpetaCache: String = "",
    val ultimaSincronizacion: Long? = null,
    val racha: Int = 0,
    // Permisos
    val camaraActiva: Boolean = false,
    val aviso: String? = null,
    // Recordatorios (NOT-01, NOT-02)
    val notificacionesDisponibles: Boolean = false,
    val notificacionesPermitidas: Boolean = false,
    val recordatorioActivo: Boolean = false,
    val horaRecordatorio: Int = ReglasAvisos.HORA_SUGERIDA,
)

/**
 * VIEW MODEL del perfil: muestra las variables de entorno y lo que la app
 * tiene guardado en el teléfono (preferencias, base de datos y caché), y
 * permite activar el permiso de cámara.
 */
class PerfilViewModel(
    private val pacienteId: Int,
    private val preferencias: PreferenciasRepositorio,
    private val almacenamiento: AlmacenamientoLocal,
    private val archivos: AlmacenArchivos,
    private val permisos: GestorPermisos,
    private val notificador: () -> Notificador = { SinNotificaciones },
) : ViewModel() {

    private val _estado = MutableStateFlow(PerfilUiState())
    val estado: StateFlow<PerfilUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            try {
                val sesiones = almacenamiento.contarSesiones()
                val pendientes = almacenamiento.sesionesPendientesDeEnvio()
                val mediciones = almacenamiento.contarMediciones()
                val (bytes, cantidad, carpeta) = withContext(Dispatchers.IO) {
                    Triple(archivos.tamanoOcupado(), archivos.cantidad(), archivos.directorio())
                }
                _estado.update {
                    it.copy(
                        ambiente = Entorno.actual.name,
                        plataforma = Plataforma.nombre,
                        version = Plataforma.versionApp,
                        servidor = ConfiguracionApi.urlServidor,
                        servidorRecordado = preferencias.urlServidorGuardada(),
                        sesionesGuardadas = sesiones,
                        pendientesEnvio = pendientes,
                        mediciones = mediciones,
                        cacheBytes = bytes,
                        cacheVideos = cantidad,
                        carpetaCache = carpeta,
                        ultimaSincronizacion = preferencias.ultimaSincronizacion(),
                        racha = preferencias.racha(pacienteId),
                        camaraActiva = preferencias.verificacionCamaraActiva && permisos.estaConcedido(Permiso.CAMARA),
                        notificacionesDisponibles = notificador().disponible,
                        notificacionesPermitidas = notificador().permitidas(),
                        recordatorioActivo = preferencias.recordatorioRutina(pacienteId) != null,
                        horaRecordatorio = preferencias.recordatorioRutina(pacienteId) ?: it.horaRecordatorio,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _estado.update { it.copy(aviso = "No pudimos leer el almacenamiento del teléfono.") }
            }
        }
    }

    /** Vacía la caché de videos (ALMACENAMIENTO EN ARCHIVOS). */
    fun limpiarCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { archivos.limpiar() }
            _estado.update { it.copy(aviso = "Se vació la caché de videos.") }
            cargar()
        }
    }

    /** NOT-01: enciende o apaga el recordatorio diario de la rutina (al encenderlo pide el permiso de notificaciones). */
    fun cambiarRecordatorio(activar: Boolean) {
        viewModelScope.launch {
            if (activar) permisos.solicitar(Permiso.NOTIFICACIONES)
            val hora = _estado.value.horaRecordatorio
            guardarRecordatorio(if (activar) hora else null)
            val permitidas = notificador().permitidas()
            _estado.update {
                it.copy(
                    recordatorioActivo = activar,
                    notificacionesPermitidas = permitidas,
                    aviso = when {
                        !activar -> "Recordatorio de la rutina apagado."
                        permitidas -> "Te recordaremos tu rutina todos los días a las ${ReglasAvisos.horaLegible(hora)}."
                        else -> "Guardado, pero las notificaciones están apagadas: actívalas en Ajustes > Apps > PhysioGestion."
                    },
                )
            }
        }
    }

    /** Mueve la hora del recordatorio media hora antes (−1) o después (+1). */
    fun moverHora(pasos: Int) {
        val nueva = ReglasAvisos.ajustarHora(_estado.value.horaRecordatorio, pasos)
        _estado.update { it.copy(horaRecordatorio = nueva, aviso = null) }
        if (_estado.value.recordatorioActivo) guardarRecordatorio(nueva)
    }

    private fun guardarRecordatorio(minutos: Int?) {
        preferencias.guardarRecordatorioRutina(pacienteId, minutos)
        notificador().programarRecordatorioRutina(minutos)
    }

    /** HABILITACIÓN: al activarla se pide el permiso de cámara; la preferencia se guarda en el teléfono. */
    fun cambiarVerificacionCamara(activar: Boolean) {
        viewModelScope.launch {
            if (!activar) {
                preferencias.verificacionCamaraActiva = false
                _estado.update { it.copy(camaraActiva = false, aviso = null) }
                return@launch
            }
            val concedido = permisos.solicitar(Permiso.CAMARA)
            preferencias.verificacionCamaraActiva = concedido
            _estado.update {
                it.copy(
                    camaraActiva = concedido,
                    aviso = if (concedido) "Permiso de cámara concedido." else "Sin permiso de cámara: puedes activarlo después en Ajustes.",
                )
            }
        }
    }
}
