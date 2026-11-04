package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.AvisosApi
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.Aviso
import com.terapid.physiogestion.modelo.BandejaAvisos
import com.terapid.physiogestion.modelo.MarcarLeidosSolicitud
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.plataforma.Notificador
import com.terapid.physiogestion.servicio.ReglasAvisos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lo que muestran las vistas de cada rol: cuántos avisos faltan por leer y cuál
 * destacar (el urgente más reciente; si no hay urgentes, el más reciente).
 */
data class ResumenAvisos(val sinLeer: Int = 0, val destacado: Aviso? = null)

/**
 * Repositorio de los avisos del servidor. Lo usan la bandeja, las vistas de
 * cada rol (revisan cada 30 s mientras están abiertas) y la revisión en
 * segundo plano de Android; las tres comparten [resumen] y el último aviso
 * notificado, por eso una misma notificación nunca sale dos veces.
 */
class AvisosRepositorio(
    private val api: AvisosApi,
    private val gestor: GestorSesion,
    private val preferencias: PreferenciasRepositorio,
) {
    private val _resumen = MutableStateFlow(ResumenAvisos())
    val resumen: StateFlow<ResumenAvisos> = _resumen.asStateFlow()

    /** Evita que la vista y el trabajo en segundo plano notifiquen lo mismo al mismo tiempo. */
    private val candado = Mutex()

    /** Bandeja completa (los más recientes primero). */
    suspend fun bandeja(): BandejaAvisos = conSesion { api.bandeja(it) }.also(::actualizarResumen)

    /**
     * Pide los avisos y devuelve los que hay que notificar (nuevos y sin leer).
     * Anota el más reciente para no repetirlo en la próxima revisión.
     */
    suspend fun revisar(): List<Aviso> = candado.withLock {
        val usuario = gestor.sesion.value?.usuario ?: return emptyList()
        val bandeja = bandeja()
        val visto = preferencias.ultimoAvisoVisto(usuario.id)
        val nuevos = ReglasAvisos.paraNotificar(bandeja, visto)
        if (bandeja.ultimoId > (visto ?: -1)) preferencias.guardarUltimoAvisoVisto(usuario.id, bandeja.ultimoId)
        nuevos
    }

    suspend fun marcarLeidos(ids: List<Int>): BandejaAvisos =
        conSesion { api.marcarLeidos(it, MarcarLeidosSolicitud(ids = ids)) }.also(::actualizarResumen)

    suspend fun marcarTodos(): BandejaAvisos =
        conSesion { api.marcarLeidos(it, MarcarLeidosSolicitud(todos = true)) }.also(::actualizarResumen)

    /** Al cerrar sesión: el siguiente usuario no debe ver el contador del anterior. */
    fun limpiar() {
        _resumen.value = ResumenAvisos()
    }

    private fun actualizarResumen(b: BandejaAvisos) {
        val sinLeer = b.avisos.filter { !it.leido }
        _resumen.value = ResumenAvisos(b.sinLeer, sinLeer.firstOrNull { it.urgente } ?: sinLeer.firstOrNull())
    }

    private suspend fun <T> conSesion(llamada: suspend (String) -> T): T =
        try {
            llamada(gestor.token())
        } catch (e: SesionVencida) {
            gestor.cerrar(e.message)
            throw e
        }
}

/** Resultado de una revisión en segundo plano (para las pruebas y el registro del trabajo). */
data class ResultadoRevision(
    val sesionAbierta: Boolean,
    val notificados: Int = 0,
    val pendientesSinEnviar: Int = 0,
    val recordatoriosCita: Int = 0,
)

/**
 * Lo que hace el trabajo periódico de Android (cada 15 min, con internet) y
 * también la app al abrirse. La lógica vive aquí, en código común, para
 * probarla en la computadora sin un teléfono:
 *
 *  1. Avisos nuevos del servidor -> notificación (NOT-03 dolor alto al fisioterapeuta).
 *  2. Paciente: intenta enviar las sesiones guardadas sin conexión; si siguen
 *     pendientes, lo avisa una vez al día (NOT-04).
 *  3. Paciente: vuelve a programar los recordatorios de sus próximas citas (NOT-02).
 */
class RevisorSegundoPlano(
    private val avisos: AvisosRepositorio,
    private val clinica: ClinicaRepositorio,
    private val sesiones: SesionesRepositorio,
    private val gestor: GestorSesion,
    private val preferencias: PreferenciasRepositorio,
    private val notificador: () -> Notificador,
    private val hoy: () -> String,
) {
    suspend fun revisar(): ResultadoRevision {
        val usuario = gestor.sesion.value?.usuario ?: return ResultadoRevision(sesionAbierta = false)
        val n = notificador()
        val nuevos = avisos.revisar()
        n.mostrarAvisos(nuevos)
        if (usuario.rol != Rol.PACIENTE) return ResultadoRevision(true, nuevos.size)

        val pendientes = try {
            sesiones.enviarPendientes().pendientes
        } catch (e: SesionVencida) {
            throw e
        } catch (_: ErrorApi) {
            sesiones.pendientesDeEnvio()
        }
        if (pendientes > 0 && preferencias.tocaAvisarPendientes()) n.mostrarPendientes(pendientes)

        val recordatorios = ReglasAvisos.recordatoriosDeCitas(clinica.citas(), hoy())
        n.programarRecordatoriosDeCitas(recordatorios)
        return ResultadoRevision(true, nuevos.size, pendientes, recordatorios.size)
    }
}
