package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.remoto.EjercicioApi
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.MedicionEjercicio
import com.terapid.physiogestion.modelo.ResultadoEjercicioSolicitud
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.claveDelDia
import com.terapid.physiogestion.servicio.movimiento.ResultadoEjercicio
import kotlin.math.roundToInt

/** Qué pasó con una medición: siempre queda en el teléfono; [enviada] dice si además llegó al servidor. */
data class EnvioMedicion(
    val medicion: MedicionEjercicio,
    val enviada: Boolean,
    val detalle: String?,
)

/**
 * Decide qué se hace con el resultado de un ejercicio medido con sensores.
 *
 * NO TODOS LOS DATOS SE ENVÍAN: de miles de lecturas queda un resumen de
 * seis campos. Primero se guarda en el teléfono y después se intenta enviar;
 * si se enviara primero y la conexión fallara, se perdería el ejercicio.
 */
class SensorRepositorio(
    private val api: EjercicioApi,
    private val almacenamiento: AlmacenamientoLocal,
    private val gestor: GestorSesion,
    private val reloj: () -> Long = ::ahoraEnMilisegundos,
) {
    suspend fun registrar(resultado: ResultadoEjercicio): EnvioMedicion {
        val ahora = reloj()
        val medicion = MedicionEjercicio(
            idEjercicio = resultado.idEjercicio,
            claveDia = claveDelDia(ahora),
            medidoEn = ahora,
            repeticiones = resultado.repeticionesDetectadas,
            repeticionesObjetivo = resultado.repeticionesObjetivo,
            anguloMaximo = resultado.anguloMaximo.roundToInt(),
            anguloObjetivo = resultado.anguloObjetivo,
            duracionSegundos = resultado.duracionSegundos,
        )
        val guardada = medicion.copy(id = almacenamiento.insertarMedicion(medicion))
        return try {
            val respuesta = api.registrarResultado(guardada.aSolicitud(), gestor.token())
            almacenamiento.marcarMedicionEnviada(guardada.id)
            EnvioMedicion(guardada.copy(enviado = true), enviada = true, detalle = respuesta.detalle)
        } catch (e: SesionVencida) {
            gestor.cerrar(e.message)
            EnvioMedicion(guardada, enviada = false, detalle = null)
        } catch (e: ErrorApi) {
            // Sin conexión: queda guardada y se reenvía después con enviarPendientes().
            EnvioMedicion(guardada, enviada = false, detalle = e.message)
        }
    }

    /** Última medición de hoy de cada ejercicio (para marcar en la rutina lo que ya se midió). */
    suspend fun medicionesDeHoy(): Map<Int, MedicionEjercicio> =
        almacenamiento.medicionesDelDia(claveDelDia(reloj()))
            .groupBy { it.idEjercicio }
            .mapValues { (_, lista) -> lista.first() }

    /** Reintenta las mediciones que no llegaron al servidor. Devuelve cuántas se enviaron. */
    suspend fun enviarPendientes(): Int {
        var enviadas = 0
        for (m in almacenamiento.medicionesPendientes()) {
            try {
                api.registrarResultado(m.aSolicitud(), gestor.token())
                almacenamiento.marcarMedicionEnviada(m.id)
                enviadas++
            } catch (e: SesionVencida) {
                gestor.cerrar(e.message)
                break
            } catch (_: ErrorApi) {
                break
            }
        }
        return enviadas
    }

    suspend fun cantidadMediciones(): Int = almacenamiento.contarMediciones()
}

private fun MedicionEjercicio.aSolicitud() = ResultadoEjercicioSolicitud(
    idEjercicio = idEjercicio,
    repeticiones = repeticiones,
    repeticionesObjetivo = repeticionesObjetivo,
    anguloMaximo = anguloMaximo,
    anguloObjetivo = anguloObjetivo,
    duracionSegundos = duracionSegundos,
)
