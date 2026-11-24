package com.terapid.physiogestion.servicios

import com.terapid.physiogestion.datos.AlmacenDocumentos
import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.datos.Coleccion
import com.terapid.physiogestion.modelos.Aviso
import com.terapid.physiogestion.modelos.BandejaAvisos
import com.terapid.physiogestion.modelos.Cita
import com.terapid.physiogestion.modelos.EstadoSolicitud
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.Rutina
import com.terapid.physiogestion.modelos.SesionTerapia
import com.terapid.physiogestion.modelos.SolicitudReagenda
import com.terapid.physiogestion.modelos.UsuarioPublico
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
 * Entrega: cada aviso nuevo se manda también como notificación push con Firebase
 * ([alCrear], que conecta Application con PushServicio). Si Firebase no está
 * configurado o el teléfono no tiene red, la app los encuentra igual con GET /avisos
 * (con la pantalla abierta cada 30 s; en Android, también en segundo plano con WorkManager).
 */
class AvisosServicio(
    private val cuentas: AutenticacionServicio,
    private val reloj: Clock = Clock.systemUTC(),
    almacen: AlmacenDocumentos = AlmacenEnMemoria(),
    json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Se llama con cada aviso recién creado (Application lo usa para mandar la push). */
    var alCrear: (Aviso) -> Unit = {}

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

    /**
     * La cita cambió de día, de hora o de fisioterapeuta. Se avisa a quien no hizo el cambio:
     * al paciente, al fisioterapeuta (al anterior y al nuevo, si cambió) y, si la movió el
     * paciente o el fisioterapeuta, a recepción.
     */
    fun citaReprogramada(antes: Cita, ahora: Cita, razon: String? = null, porQuien: UsuarioPublico? = null) {
        val mismaHora = antes.fecha == ahora.fecha && antes.hora == ahora.hora
        val otroFisio = antes.fisioterapeutaId != ahora.fisioterapeutaId
        val horario = if (mismaHora) "${dia(ahora.fecha).mayuscula()} a las ${ahora.hora}"
        else "${dia(antes.fecha).mayuscula()} ${antes.hora} → ${dia(ahora.fecha)} a las ${ahora.hora}"
        val porque = razon?.let { " Motivo: $it." } ?: ""
        val actor = porQuien?.id

        cuentaDelPaciente(ahora.pacienteId)?.takeIf { it != actor }?.let {
            val (titulo, texto) = when {
                mismaHora && otroFisio -> "Cambió tu fisioterapeuta" to "$horario te atiende ${ahora.fisioterapeuta}.$porque"
                else -> "Tu cita cambió de horario" to "$horario. Con ${ahora.fisioterapeuta}.$porque"
            }
            crear(it, TipoAviso.CITA_REPROGRAMADA, titulo, texto, ahora.pacienteId, citaId = ahora.id)
        }
        val quien = when (porQuien?.rol) {
            Rol.PACIENTE -> "El paciente la reagendó"
            Rol.FISIOTERAPEUTA -> "${porQuien.nombre} no puede atenderla$porque".trimEnd('.')
            else -> null
        }
        val textoPersonal = horario + "." + (quien?.let { " $it." } ?: porque)
        if (ahora.fisioterapeutaId != actor) {
            val titulo = if (otroFisio) "Te pasaron una cita: ${ahora.paciente}" else "Cita reprogramada: ${ahora.paciente}"
            crear(ahora.fisioterapeutaId, TipoAviso.CITA_REPROGRAMADA, titulo, textoPersonal, ahora.pacienteId, citaId = ahora.id)
        }
        if (otroFisio && antes.fisioterapeutaId != actor) {
            crear(antes.fisioterapeutaId, TipoAviso.CITA_REPROGRAMADA, "Ya no atiendes a ${ahora.paciente}",
                "Su cita de ${dia(antes.fecha)} a las ${antes.hora} pasó con ${ahora.fisioterapeuta}.", ahora.pacienteId, citaId = ahora.id)
        }
        if (porQuien?.rol == Rol.PACIENTE || porQuien?.rol == Rol.FISIOTERAPEUTA) {
            personal(Rol.RECEPCIONISTA).forEach {
                crear(it.id, TipoAviso.CITA_REPROGRAMADA, "Cita movida: ${ahora.paciente}", "$textoPersonal Con ${ahora.fisioterapeuta}.", ahora.pacienteId, citaId = ahora.id)
            }
        }
    }

    /** El paciente pidió cambiar su cita: a recepción (decide) y a su fisioterapeuta (para que lo sepa). */
    fun solicitudReagenda(s: SolicitudReagenda) {
        val propuesta = if (s.fechaPropuesta != null && s.horaPropuesta != null) " Propone ${dia(s.fechaPropuesta)} a las ${s.horaPropuesta}." else ""
        val texto = "Su cita de ${dia(s.fechaActual)} a las ${s.horaActual}.$propuesta «${s.mensaje}»"
        (personal(Rol.RECEPCIONISTA).map { it.id } + s.fisioterapeutaId).distinct().forEach {
            crear(it, TipoAviso.SOLICITUD_REAGENDA, "Quiere cambiar su cita: ${s.paciente}", texto, s.pacienteId, citaId = s.citaId)
        }
    }

    /** Recepción respondió la solicitud del paciente. Si la aprobó, el aviso del cambio de horario ya salió aparte. */
    fun solicitudResuelta(s: SolicitudReagenda) {
        val paciente = cuentaDelPaciente(s.pacienteId) ?: return
        if (s.estado == EstadoSolicitud.APROBADA) {
            crear(paciente, TipoAviso.SOLICITUD_RESUELTA, "Aceptamos tu cambio de cita",
                "Recepción movió tu cita." + (s.respuesta?.let { " $it" } ?: ""), s.pacienteId, citaId = s.citaId)
        } else {
            crear(paciente, TipoAviso.SOLICITUD_RESUELTA, "No se pudo cambiar tu cita",
                "Tu cita sigue el ${dia(s.fechaActual)} a las ${s.horaActual}. ${s.respuesta ?: ""}".trim(), s.pacienteId, citaId = s.citaId)
        }
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

    /** Cuenta: inicio de sesión en un teléfono nuevo o cambio de contraseña. */
    fun seguridad(usuarioId: Int, titulo: String, mensaje: String): Aviso = crear(usuarioId, TipoAviso.SEGURIDAD, titulo, mensaje)

    /** Copia en la bandeja del anuncio general de la clínica (la push sale una vez por tema). */
    fun anuncio(usuarioId: Int, titulo: String, texto: String): Aviso = crear(usuarioId, TipoAviso.ANUNCIO, titulo, texto)

    /** Revisión diaria: pacientes del fisioterapeuta que no han hecho su rutina. */
    fun pacientesInactivos(fisioterapeutaId: Int, pacientes: List<RevisionDiariaServicio.Inactivo>) {
        if (pacientes.isEmpty()) return
        fun detalle(p: RevisionDiariaServicio.Inactivo) = p.nombre + when (p.dias) {
            null -> " (todavía sin sesiones)"
            else -> " (${p.dias} días)"
        }
        val uno = pacientes.singleOrNull()
        crear(
            usuarioId = fisioterapeutaId,
            tipo = TipoAviso.PACIENTES_INACTIVOS,
            titulo = if (uno != null) "${uno.nombre} no ha hecho su rutina" else "${pacientes.size} pacientes sin hacer su rutina",
            mensaje = if (uno != null) {
                (if (uno.dias == null) "Todavía no registra ninguna sesión de su rutina." else "Lleva ${uno.dias} días sin registrar una sesión de su rutina.") +
                    " Quizá le ayude un mensaje o una llamada."
            }
            else pacientes.take(6).joinToString(", ") { detalle(it) } + (if (pacientes.size > 6) " y ${pacientes.size - 6} más" else "") +
                ". Llevan ${RevisionDiariaServicio.DIAS_SIN_ACTIVIDAD} días o más sin registrar una sesión.",
            pacienteId = uno?.pacienteId,
        )
    }

    /** true si el usuario ya recibió un aviso de ese tipo desde [desdeMillis]. */
    fun tieneDesde(usuarioId: Int, tipo: TipoAviso, desdeMillis: Long): Boolean =
        avisos.values.any { it.usuarioId == usuarioId && it.tipo == tipo && it.creado >= desdeMillis }

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
        runCatching { alCrear(aviso) }
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
