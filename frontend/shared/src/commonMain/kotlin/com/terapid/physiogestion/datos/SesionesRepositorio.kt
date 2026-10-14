package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.remoto.Conflicto
import com.terapid.physiogestion.datos.remoto.DatosInvalidos
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.datos.remoto.Respuesta
import com.terapid.physiogestion.datos.remoto.SesionApi
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.CrearSesionSolicitud
import com.terapid.physiogestion.modelo.EjercicioRealizado
import com.terapid.physiogestion.modelo.ModificarSesionSolicitud
import com.terapid.physiogestion.modelo.PaginaSesiones
import com.terapid.physiogestion.modelo.ReemplazarSesionSolicitud
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.modelo.SesionTerapia
import com.terapid.physiogestion.plataforma.fechaHoraIso

/** Cómo terminó un intento de vaciar la cola de envío. */
data class ResultadoEnvio(
    val enviadas: Int,
    val pendientes: Int,
    /** Motivo por el que se detuvo (sin conexión, etc.); null si todo se envió. */
    val error: ErrorApi? = null,
    val detalle: String? = null,
)

/**
 * Repositorio de las sesiones de terapia: une la API de /sesiones con la
 * copia del teléfono.
 *
 * Flujo de una sesión nueva: primero se guarda en SQLite (nunca se pierde,
 * aunque no haya red) y después [enviarPendientes] la manda con POST. Si la
 * conexión falla, queda en la cola y se reintenta más tarde; la clave de
 * idempotencia evita que un reintento cree una sesión duplicada.
 */
class SesionesRepositorio(
    private val api: SesionApi,
    private val almacenamiento: AlmacenamientoLocal,
    private val gestor: GestorSesion,
) {
    // ---- GET ----
    suspend fun historial(pagina: Int = 0, tamano: Int = 20): Respuesta<PaginaSesiones> =
        conSesion { token -> api.listar(gestor.pacienteId(), token, pagina, tamano) }

    suspend fun obtener(idSesion: Int): Respuesta<SesionTerapia> = conSesion { api.obtener(idSesion, it) }

    /** Copia del teléfono, para mostrar algo cuando no hay conexión. */
    suspend fun historialLocal(limite: Int = 50): List<RegistroSesion> = almacenamiento.listarSesiones(limite)

    suspend fun pendientesDeEnvio(): Int = almacenamiento.sesionesPendientesDeEnvio()

    // ---- PUT / PATCH / DELETE ----
    suspend fun reemplazar(idSesion: Int, solicitud: ReemplazarSesionSolicitud): Respuesta<SesionTerapia> =
        conSesion { api.reemplazar(idSesion, solicitud, it) }

    suspend fun modificar(idSesion: Int, solicitud: ModificarSesionSolicitud): Respuesta<SesionTerapia> =
        conSesion { api.modificar(idSesion, solicitud, it) }

    suspend fun eliminar(idSesion: Int): Respuesta<Unit> {
        val respuesta = conSesion { api.eliminar(idSesion, it) }
        almacenamiento.eliminarSesionDelServidor(idSesion) // también se quita la copia del teléfono
        return respuesta
    }

    // ---- POST: cola de envío (RFX-03) ----

    /** Envía, en orden, las sesiones guardadas que el servidor todavía no tiene. */
    suspend fun enviarPendientes(): ResultadoEnvio {
        val cola = almacenamiento.sesionesPendientes()
        var enviadas = 0
        var detalle: String? = null
        for (sesion in cola) {
            try {
                val respuesta = conSesion { api.registrar(sesion.aSolicitud(), claveIdempotencia(sesion), it) }
                almacenamiento.marcarSesionEnviada(sesion.id, respuesta.datos.id)
                detalle = respuesta.detalle
                enviadas++
            } catch (e: Conflicto) {
                // El servidor ya tiene una sesión con esa misma hora de inicio: no se reintenta.
                almacenamiento.marcarSesionEnviada(sesion.id, null)
                enviadas++
            } catch (e: DatosInvalidos) {
                // El servidor la rechazó por su contenido; se deja en el teléfono y se sigue con las demás.
                detalle = e.message
            } catch (e: SesionVencida) {
                throw e
            } catch (e: ErrorApi) {
                // Sin conexión u otro problema: se detiene y se reintenta después.
                return ResultadoEnvio(enviadas, cola.size - enviadas, e, detalle)
            }
        }
        return ResultadoEnvio(enviadas, almacenamiento.sesionesPendientesDeEnvio(), null, detalle)
    }

    /** La misma sesión local siempre genera la misma clave, así un reintento no la duplica. */
    private fun claveIdempotencia(s: RegistroSesion) = "sesion-local-${s.id}-${s.inicioMillis}"

    private suspend fun <T> conSesion(llamada: suspend (String) -> T): T =
        try {
            llamada(gestor.token())
        } catch (e: SesionVencida) {
            gestor.cerrar(e.message)
            throw e
        }
}

/** Sesión del teléfono -> cuerpo de POST /sesiones. */
fun RegistroSesion.aSolicitud() = CrearSesionSolicitud(
    idRutina = rutinaId,
    fechaHoraInicio = fechaHoraIso(inicioMillis),
    fechaHoraFin = fechaHoraIso(finMillis),
    dolorAntes = dolorAntes,
    dolorDespues = dolorDespues,
    dificultad = dificultad,
    observacion = observacion.ifBlank { null },
    ejercicios = detalle.ifEmpty { ejerciciosRealizados.map { EjercicioRealizado(it, repeticionesRealizadas = 0) } },
)
