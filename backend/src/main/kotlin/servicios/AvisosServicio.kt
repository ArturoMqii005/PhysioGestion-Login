package com.terapid.physiogestion.servicios

import com.terapid.physiogestion.datos.AlmacenDocumentos
import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.datos.Coleccion
import com.terapid.physiogestion.modelos.Aviso
import com.terapid.physiogestion.modelos.BandejaAvisos
import com.terapid.physiogestion.modelos.Cita
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.Rutina
import com.terapid.physiogestion.modelos.SesionTerapia
import com.terapid.physiogestion.modelos.TipoAviso
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * AVISOS: el servidor decide a quién avisar y guarda el aviso (colección «avisos»).
 *
 * La entrega no depende de Firebase: la app pide sus avisos con GET /avisos
 * (con la pantalla abierta cada 30 s; en Android, también en segundo plano con
 * WorkManager) y ella misma muestra la notificación del sistema.
 */
class AvisosServicio(
    private val cuentas: AutenticacionServicio,
    private val reloj: Clock = Clock.systemUTC(),
    almacen: AlmacenDocumentos = AlmacenEnMemoria(),
    json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        /** Se guardan los últimos 100 avisos de cada usuario; los más viejos se borran. */
        const val MAXIMO_POR_USUARIO = 100
        private val ES_MX: Locale = Locale.forLanguageTag("es-MX")
        private val DIA = DateTimeFormatter.ofPattern("EEE dd/MM", ES_MX)
        private val DIA_HORA = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm", ES_MX)
    }

    private val col = Coleccion(almacen, "avisos", Aviso.serializer(), json) { it.id.toString() }
    private val avisos = ConcurrentHashMap<Int, Aviso>().apply { col.cargar().forEach { put(it.id, it) } }
    private val siguiente = AtomicInteger((avisos.keys.maxOrNull() ?: 0) + 1)

    // ======================================================================= CONSULTA

    /**
     * Bandeja del usuario, del más reciente al más antiguo. Con [desde] solo
     * llegan los avisos con id mayor (lo que la app todavía no conoce).
     */
    fun bandeja(usuarioId: Int, desde: Int? = null, limite: Int = 50): BandejaAvisos {
        val suyos = avisos.values.filter { it.usuarioId == usuarioId }
        return BandejaAvisos(
            avisos = suyos.filter { desde == null || it.id > desde }.sortedByDescending { it.id }.take(limite),
            sinLeer = suyos.count { !it.leido },
            ultimoId = suyos.maxOfOrNull { it.id } ?: 0,
        )
    }

    /** Marca como leídos los avisos indicados (solo los del propio usuario). Devuelve cuántos cambiaron. */
    fun marcarLeidos(usuarioId: Int, ids: Collection<Int>, todos: Boolean): Int {
        val objetivo = avisos.values.filter { it.usuarioId == usuarioId && !it.leido && (todos || it.id in ids) }
        objetivo.forEach { guardar(it.copy(leido = true)) }
        return objetivo.size
    }

    // ======================================================================= GENERACIÓN

    /**
     * NOT-03: la sesión llegó en rojo. Se avisa al fisioterapeuta asignado una
     * sola vez por sesión, aunque el paciente la corrija después.
     */
    fun dolorAlto(sesion: SesionTerapia, rutina: Rutina?, fisioterapeutaId: Int?) {
        val destino = fisioterapeutaId ?: rutina?.let { r -> personal(Rol.FISIOTERAPEUTA).firstOrNull { it.nombre == r.fisioterapeuta }?.id }
            ?: return
        if (avisos.values.any { it.tipo == TipoAviso.DOLOR_ALTO && it.sesionId == sesion.id }) return
        val umbral = rutina?.umbralDolor ?: SesionesServicio.UMBRAL_DOLOR_PREDETERMINADO
        val cuando = runCatching { LocalDateTime.parse(sesion.fechaHoraInicio).format(DIA_HORA) }.getOrDefault(sesion.fechaHoraInicio)
        val comentario = sesion.observacion?.let { " Comentó: «$it»." } ?: ""
        crear(
            usuarioId = destino,
            tipo = TipoAviso.DOLOR_ALTO,
            titulo = "Dolor alto: ${sesion.paciente.ifBlank { "paciente ${sesion.pacienteId}" }}",
            mensaje = "Terminó su sesión del $cuando con dolor ${sesion.dolorDespues}/10 (su límite es $umbral); " +
                "al empezar tenía ${sesion.dolorAntes}/10.$comentario",
            pacienteId = sesion.pacienteId,
            sesionId = sesion.id,
        )
    }

    /** Recepción agendó una cita: se avisa al paciente si tiene cuenta. */
    fun citaNueva(cita: Cita) {
        val paciente = cuentaDelPaciente(cita.pacienteId) ?: return
        crear(
            paciente, TipoAviso.CITA_NUEVA, "Tienes una cita nueva",
            "${dia(cita.fecha).mayuscula()} a las ${cita.hora} con ${cita.fisioterapeuta} · ${cita.motivo}.",
            pacienteId = cita.pacienteId, citaId = cita.id,
        )
    }

    /** La cita cambió de día u hora: se avisa al paciente y al fisioterapeuta (al nuevo, si cambió). */
    fun citaReprogramada(antes: Cita, ahora: Cita) {
        val texto = "${dia(antes.fecha).mayuscula()} ${antes.hora} → ${dia(ahora.fecha)} a las ${ahora.hora}."
        cuentaDelPaciente(ahora.pacienteId)?.let {
            crear(it, TipoAviso.CITA_REPROGRAMADA, "Tu cita cambió de horario", "$texto Con ${ahora.fisioterapeuta}.", ahora.pacienteId, citaId = ahora.id)
        }
        crear(ahora.fisioterapeutaId, TipoAviso.CITA_REPROGRAMADA, "Cita reprogramada: ${ahora.paciente}", texto, ahora.pacienteId, citaId = ahora.id)
    }

    /**
     * Se canceló una cita. Si la canceló el paciente, se avisa a su fisioterapeuta
     * y a recepción (para ofrecer la hora a otra persona); si la canceló la clínica,
     * se avisa al paciente.
     */
    fun citaCancelada(cita: Cita, porElPaciente: Boolean, quienCancela: Int) {
        val cuando = "${dia(cita.fecha)} a las ${cita.hora}"
        if (porElPaciente) {
            val texto = "Canceló su cita del $cuando con ${cita.fisioterapeuta}. La hora quedó libre."
            (listOf(cita.fisioterapeutaId) + personal(Rol.RECEPCIONISTA).map { it.id }).distinct().forEach {
                crear(it, TipoAviso.CITA_CANCELADA, "Cita cancelada: ${cita.paciente}", texto, cita.pacienteId, citaId = cita.id)
            }
        } else {
            cuentaDelPaciente(cita.pacienteId)?.let {
                crear(
                    it, TipoAviso.CITA_CANCELADA, "Se canceló tu cita",
                    "Tu cita del $cuando con ${cita.fisioterapeuta} se canceló." + (cita.nota?.let { n -> " Motivo: $n." } ?: "") +
                        " Llama a recepción para agendar otra.",
                    cita.pacienteId, citaId = cita.id,
                )
            }
            if (cita.fisioterapeutaId != quienCancela) {
                crear(cita.fisioterapeutaId, TipoAviso.CITA_CANCELADA, "Cita cancelada: ${cita.paciente}", "Recepción canceló la cita del $cuando.", cita.pacienteId, citaId = cita.id)
            }
        }
    }

    /** El fisioterapeuta guardó la rutina: se avisa al paciente. */
    fun rutinaActualizada(rutina: Rutina) {
        val paciente = cuentaDelPaciente(rutina.pacienteId) ?: return
        val n = rutina.ejercicios.size
        crear(
            paciente, TipoAviso.RUTINA_ACTUALIZADA, "Tu rutina cambió",
            "${rutina.fisioterapeuta} actualizó tu rutina: $n ${if (n == 1) "ejercicio" else "ejercicios"}. Ábrela para ver los cambios.",
            pacienteId = rutina.pacienteId,
        )
    }

    /** Se asignó un paciente al fisioterapeuta. */
    fun pacienteAsignado(fisioterapeutaId: Int, pacienteId: Int, nombrePaciente: String) {
        crear(
            fisioterapeutaId, TipoAviso.PACIENTE_ASIGNADO, "Nuevo paciente: $nombrePaciente",
            "Ahora $nombrePaciente está en tu seguimiento. Revisa su ficha y su rutina.", pacienteId = pacienteId,
        )
    }

    // ======================================================================= AUXILIARES

    private fun crear(
        usuarioId: Int,
        tipo: TipoAviso,
        titulo: String,
        mensaje: String,
        pacienteId: Int? = null,
        sesionId: Int? = null,
        citaId: Int? = null,
    ): Aviso {
        val aviso = Aviso(
            id = siguiente.getAndIncrement(),
            usuarioId = usuarioId,
            tipo = tipo,
            titulo = titulo.take(90),
            mensaje = mensaje.take(400),
            creado = reloj.millis(),
            pacienteId = pacienteId,
            sesionId = sesionId,
            citaId = citaId,
        )
        guardar(aviso)
        recortar(usuarioId)
        return aviso
    }

    private fun guardar(a: Aviso) {
        avisos[a.id] = a
        col.guardar(a)
    }

    private fun recortar(usuarioId: Int) {
        avisos.values.filter { it.usuarioId == usuarioId }.sortedByDescending { it.id }.drop(MAXIMO_POR_USUARIO).forEach {
            avisos.remove(it.id)
            col.borrar(it.id.toString())
        }
    }

    private fun cuentaDelPaciente(pacienteId: Int?): Int? = pacienteId?.let { id ->
        cuentas.usuarios().firstOrNull { it.rol == Rol.PACIENTE && it.pacienteId == id && it.activo }?.id
    }

    private fun personal(rol: Rol) = cuentas.usuarios().filter { it.rol == rol && it.activo }

    /** «2026-11-05» -> «jue 05/11». */
    private fun dia(iso: String): String = runCatching { LocalDate.parse(iso).format(DIA).replace(".", "") }.getOrDefault(iso)

    private fun String.mayuscula() = replaceFirstChar { it.uppercase() }
}
