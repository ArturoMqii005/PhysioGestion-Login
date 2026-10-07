package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.remoto.RutinaApi
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.modelo.RutinaConOrigen
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import kotlinx.coroutines.CancellationException

/** No hay conexión y tampoco existe una rutina guardada en el teléfono. */
class SinRutinaDisponible(causa: Throwable) : Exception("No hay rutina disponible", causa)

/**
 * Repositorio de la rutina: une la API y el almacenamiento local para que
 * el ViewModel no tenga que saber de dónde vienen los datos.
 *
 * Estrategia: primero intenta descargar la rutina; si lo logra, guarda una
 * copia local. Si no hay conexión, usa la última copia guardada (RFN-21).
 */
class RutinaRepositorio(
    private val api: RutinaApi,
    private val almacenamiento: AlmacenamientoLocal,
    private val gestor: GestorSesion,
    private val reloj: () -> Long = ::ahoraEnMilisegundos,
) {
    suspend fun obtenerRutina(pacienteId: Int): RutinaConOrigen =
        try {
            val rutina = api.descargarRutinaVigente(pacienteId, gestor.token())
            val ahora = reloj()
            almacenamiento.guardarRutina(pacienteId, api.aJson(rutina), ahora)
            RutinaConOrigen(rutina, OrigenDatos.SERVIDOR, ahora)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SesionVencida) {
            gestor.cerrar()
            throw e
        } catch (e: Exception) {
            val guardada = almacenamiento.leerRutina(pacienteId) ?: throw SinRutinaDisponible(e)
            RutinaConOrigen(
                rutina = api.leerRutina(guardada.json),
                origen = OrigenDatos.ALMACENAMIENTO_LOCAL,
                actualizadaEn = guardada.descargadaEn,
            )
        }

    /** Guarda la sesión en el teléfono; queda en la cola de envío pendiente (RFX-03). */
    suspend fun registrarSesion(sesion: RegistroSesion): Long =
        almacenamiento.insertarSesion(sesion.copy(pendienteEnvio = true))

    suspend fun historial(limite: Int = 20): List<RegistroSesion> = almacenamiento.listarSesiones(limite)

    suspend fun ultimoDolorRegistrado(): Int? = almacenamiento.ultimoDolorRegistrado()

    suspend fun puntosDelDia(claveDia: String): Int = almacenamiento.puntosDelDia(claveDia)

    suspend fun puntosTotales(): Int = almacenamiento.puntosTotales()
}
