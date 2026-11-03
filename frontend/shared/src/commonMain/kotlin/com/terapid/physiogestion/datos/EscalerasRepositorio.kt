package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenamientoLocal
import com.terapid.physiogestion.datos.remoto.DatosInvalidos
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.datos.remoto.EscalerasApi
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.PruebaEscaleras
import com.terapid.physiogestion.modelo.PruebaEscalerasSolicitud
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.claveDelDia
import com.terapid.physiogestion.plataforma.fechaHoraIso
import com.terapid.physiogestion.servicio.escaleras.ResultadoEscaleras

/** Qué pasó con una prueba: siempre queda en el teléfono; [enviada] dice si además llegó al servidor. */
data class EnvioEscaleras(val prueba: PruebaEscaleras, val enviada: Boolean, val detalle: String?)

/**
 * Pruebas de escaleras: primero se guardan en el teléfono (tabla
 * prueba_escaleras) y después se envían; sin conexión quedan pendientes y se
 * reenvían con [enviarPendientes], igual que las mediciones de ejercicios.
 *
 * Si el servidor la rechaza por datos inválidos (422), reintentarla nunca
 * funcionaría y además taparía la cola: se queda en el teléfono, pero sale
 * de la cola de envío.
 */
class EscalerasRepositorio(
    private val api: EscalerasApi,
    private val almacenamiento: AlmacenamientoLocal,
    private val gestor: GestorSesion,
    private val reloj: () -> Long = ::ahoraEnMilisegundos,
) {
    suspend fun registrar(resultado: ResultadoEscaleras): EnvioEscaleras {
        val ahora = reloj()
        val prueba = PruebaEscaleras(
            claveDia = claveDelDia(ahora),
            medidoEn = ahora,
            alturaSubidaCm = resultado.alturaSubidaCm,
            pisos = resultado.pisos,
            escalones = resultado.escalonesEstimados,
            pasos = resultado.pasos,
            duracionSegundos = resultado.duracionSegundos,
        )
        val guardada = prueba.copy(id = almacenamiento.insertarPruebaEscaleras(prueba))
        return try {
            val respuesta = api.registrar(guardada.aSolicitud(), gestor.token())
            almacenamiento.marcarPruebaEscalerasEnviada(guardada.id)
            EnvioEscaleras(guardada.copy(enviado = true), enviada = true, detalle = respuesta.detalle)
        } catch (e: SesionVencida) {
            gestor.cerrar(e.message)
            EnvioEscaleras(guardada, enviada = false, detalle = null)
        } catch (e: DatosInvalidos) {
            almacenamiento.marcarPruebaEscalerasEnviada(guardada.id)
            EnvioEscaleras(guardada, enviada = false, detalle = e.message)
        } catch (e: ErrorApi) {
            EnvioEscaleras(guardada, enviada = false, detalle = e.message)
        }
    }

    /** Últimas pruebas guardadas en el teléfono, la más reciente primero (para ver el avance). */
    suspend fun historial(limite: Int = 10): List<PruebaEscaleras> = almacenamiento.pruebasEscaleras(limite)

    /** Reintenta las pruebas que no llegaron al servidor. Devuelve cuántas se enviaron. */
    suspend fun enviarPendientes(): Int {
        var enviadas = 0
        for (p in almacenamiento.pruebasEscalerasPendientes()) {
            try {
                api.registrar(p.aSolicitud(), gestor.token())
                almacenamiento.marcarPruebaEscalerasEnviada(p.id)
                enviadas++
            } catch (e: SesionVencida) {
                gestor.cerrar(e.message)
                break
            } catch (_: DatosInvalidos) {
                almacenamiento.marcarPruebaEscalerasEnviada(p.id) // rechazada para siempre: no bloquea a las demás
            } catch (_: ErrorApi) {
                break
            }
        }
        return enviadas
    }
}

private fun PruebaEscaleras.aSolicitud() = PruebaEscalerasSolicitud(
    fecha = fechaHoraIso(medidoEn),
    alturaSubidaCm = alturaSubidaCm,
    pisos = pisos,
    escalonesEstimados = escalones,
    pasos = pasos,
    duracionSegundos = duracionSegundos,
)
