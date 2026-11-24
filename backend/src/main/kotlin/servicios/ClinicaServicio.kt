package com.terapid.physiogestion.servicios

import com.terapid.physiogestion.datos.AlmacenDocumentos
import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.datos.Coleccion
import com.terapid.physiogestion.modelos.Asignacion
import com.terapid.physiogestion.modelos.Cita
import com.terapid.physiogestion.modelos.CitaSolicitud
import com.terapid.physiogestion.modelos.Ejercicio
import com.terapid.physiogestion.modelos.EstadoCita
import com.terapid.physiogestion.modelos.FisioterapeutaResumen
import com.terapid.physiogestion.modelos.EstadoSolicitud
import com.terapid.physiogestion.modelos.NuevaSolicitudReagenda
import com.terapid.physiogestion.modelos.ReagendarSolicitud
import com.terapid.physiogestion.modelos.ReprogramarCitaSolicitud
import com.terapid.physiogestion.modelos.ResolverSolicitud
import com.terapid.physiogestion.modelos.SolicitudReagenda
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.Rutina
import com.terapid.physiogestion.modelos.RutinaSolicitud
import com.terapid.physiogestion.modelos.UsuarioPublico
import com.terapid.physiogestion.plugins.AccesoDenegado
import com.terapid.physiogestion.plugins.Conflicto
import com.terapid.physiogestion.plugins.DatosNoProcesables
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap

/** Paciente que la clínica conoce: por su cuenta, por su invitación o por su rutina. */
data class PacienteConocido(val pacienteId: Int, val nombre: String, val correo: String?, val tieneCuenta: Boolean)

/**
 * OPERACIÓN CLÍNICA (fase 2 del inventario):
 *
 *  - Citas con fecha: agendar (CIT-05), reprogramar y cancelar (CIT-06), marcar
 *    asistencia (CIT-07) y las próximas citas del paciente (CIT-08). Se valida que el
 *    fisioterapeuta no tenga dos citas al mismo tiempo y que la cita esté en horario.
 *  - Asignación de cada paciente a su fisioterapeuta (PAC-04).
 *  - Rutinas que el fisioterapeuta arma con el catálogo de ejercicios (RUT-08).
 *
 * Todo se guarda en el [almacen] (PostgreSQL en Railway). Las citas y rutinas de
 * ejemplo solo se cargan la primera vez, cuando la base está vacía.
 */
class ClinicaServicio(
    rutinasIniciales: List<Rutina>,
    citasIniciales: List<Cita>,
    val catalogo: List<Ejercicio>,
    private val cuentas: AutenticacionServicio,
    private val reloj: Clock = Clock.systemUTC(),
    almacen: AlmacenDocumentos = AlmacenEnMemoria(),
    json: Json = Json { ignoreUnknownKeys = true },
    /** Avisos para el paciente y el personal cuando cambia una cita, una rutina o una asignación. */
    private val avisos: AvisosServicio? = null,
) {
    companion object {
        /** Zona de la clínica (Tulancingo): «hoy» y la hora actual se calculan aquí. */
        val ZONA: ZoneId = ZoneId.of("America/Mexico_City")
        val APERTURA: LocalTime = LocalTime.of(7, 0)
        val CIERRE: LocalTime = LocalTime.of(21, 0)
        val DURACIONES = setOf(30, 45, 50, 60, 90)
        private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")
        private val FORMATO_MX = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val TERMINADAS = setOf(EstadoCita.ATENDIDA, EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO)

        /** El paciente mueve su cita él mismo solo si faltan más de 24 h; si no, manda una solicitud. */
        const val HORAS_ANTICIPACION_REAGENDA = 24L

        /** Veces que el paciente puede mover una misma cita desde la app. */
        const val MAXIMO_REAGENDAS_PACIENTE = 2
    }

    private val colRutinas = Coleccion(almacen, "rutinas", Rutina.serializer(), json) { it.pacienteId.toString() }
    private val colCitas = Coleccion(almacen, "citas", Cita.serializer(), json) { it.id.toString() }
    private val colAsignaciones = Coleccion(almacen, "asignaciones", Asignacion.serializer(), json) { it.pacienteId.toString() }
    private val colSolicitudes = Coleccion(almacen, "solicitudes_reagenda", SolicitudReagenda.serializer(), json) { it.id.toString() }

    private val rutinas = ConcurrentHashMap<Int, Rutina>()
    private val citas = ConcurrentHashMap<Int, Cita>()
    private val asignaciones = ConcurrentHashMap<Int, Asignacion>()
    private val solicitudes = ConcurrentHashMap<Int, SolicitudReagenda>()

    init {
        colRutinas.cargar(rutinasIniciales).forEach { rutinas[it.pacienteId] = it }
        // Las citas de ejemplo no traen fecha: se ponen en el día en que se cargan por primera vez.
        val hoy = hoy().toString()
        colCitas.cargar(citasIniciales.map { if (it.fecha.isBlank()) it.copy(fecha = hoy) else it }).forEach { citas[it.id] = it }
        val semilla = rutinas.values.mapNotNull { r -> fisioPorNombre(r.fisioterapeuta)?.let { Asignacion(r.pacienteId, it.id) } }
        colAsignaciones.cargar(semilla).forEach { asignaciones[it.pacienteId] = it }
        colSolicitudes.cargar().forEach { solicitudes[it.id] = it }
    }

    fun hoy(): LocalDate = LocalDate.now(reloj.withZone(ZONA))

    // ======================================================================= FISIOTERAPEUTAS Y PACIENTES

    fun fisioterapeutas(): List<FisioterapeutaResumen> =
        cuentas.usuarios().filter { it.rol == Rol.FISIOTERAPEUTA && it.activo }.sortedBy { it.nombre }
            .map { FisioterapeutaResumen(it.id, it.nombre) }

    private fun fisio(id: Int): FisioterapeutaResumen =
        fisioterapeutas().firstOrNull { it.id == id } ?: throw DatosNoProcesables("Ese fisioterapeuta no existe o está desactivado.")

    private fun fisioPorNombre(nombre: String) = cuentas.usuarios().firstOrNull { it.rol == Rol.FISIOTERAPEUTA && it.nombre == nombre }

    /** Pacientes con cuenta, con invitación pendiente o con rutina; uno por pacienteId. */
    fun pacientesConocidos(): List<PacienteConocido> {
        val porId = linkedMapOf<Int, PacienteConocido>()
        cuentas.usuarios().filter { it.rol == Rol.PACIENTE && it.pacienteId != null }.forEach {
            porId[it.pacienteId!!] = PacienteConocido(it.pacienteId, it.nombre, it.correo, tieneCuenta = true)
        }
        cuentas.invitaciones().forEach { inv ->
            porId.putIfAbsent(inv.pacienteId, PacienteConocido(inv.pacienteId, inv.nombrePaciente, null, tieneCuenta = false))
        }
        rutinas.values.forEach { r -> porId.putIfAbsent(r.pacienteId, PacienteConocido(r.pacienteId, r.paciente, null, tieneCuenta = false)) }
        return porId.values.sortedBy { it.nombre }
    }

    fun paciente(pacienteId: Int): PacienteConocido =
        pacientesConocidos().firstOrNull { it.pacienteId == pacienteId } ?: throw NotFoundException("Ese paciente no existe.")

    fun asignacionDe(pacienteId: Int): Asignacion? = asignaciones[pacienteId]

    /** Pacientes que atiende el fisioterapeuta [fisioId]. */
    fun pacientesDe(fisioId: Int): Set<Int> = asignaciones.values.filter { it.fisioterapeutaId == fisioId }.map { it.pacienteId }.toSet()

    /** Pacientes que el usuario puede consultar: el fisioterapeuta, solo los suyos; recepción y administración, todos. */
    fun pacientesVisibles(usuario: UsuarioPublico): List<PacienteConocido> = when (usuario.rol) {
        Rol.FISIOTERAPEUTA -> pacientesConocidos().filter { it.pacienteId in pacientesDe(usuario.id) }
        Rol.ADMINISTRADOR, Rol.RECEPCIONISTA -> pacientesConocidos()
        Rol.PACIENTE -> pacientesConocidos().filter { it.pacienteId == usuario.pacienteId }
    }

    fun puedeVer(usuario: UsuarioPublico, pacienteId: Int): Boolean = pacientesVisibles(usuario).any { it.pacienteId == pacienteId }

    /** Asigna (o cambia) el fisioterapeuta de un paciente; su rutina pasa a nombre del nuevo (PAC-04). */
    fun asignar(pacienteId: Int, fisioterapeutaId: Int): Asignacion {
        val p = paciente(pacienteId)
        val f = fisio(fisioterapeutaId)
        val anterior = asignaciones[pacienteId]?.fisioterapeutaId
        val a = Asignacion(pacienteId, f.id)
        asignaciones[pacienteId] = a
        colAsignaciones.guardar(a)
        rutinas[pacienteId]?.let { guardarRutina(it.copy(fisioterapeuta = f.nombre)) }
        if (anterior != f.id) avisar { pacienteAsignado(f.id, pacienteId, p.nombre) }
        return a
    }

    // ======================================================================= RUTINAS (RUT-08)

    fun rutinas(): List<Rutina> = rutinas.values.toList()

    fun rutinaDe(pacienteId: Int): Rutina? = rutinas[pacienteId]

    /**
     * Crea o reemplaza la rutina con ejercicios del catálogo. Conserva el id para que
     * las sesiones ya registradas sigan ligadas a ella.
     */
    fun guardarRutina(pacienteId: Int, s: RutinaSolicitud, fisioterapeuta: UsuarioPublico): Rutina {
        val p = paciente(pacienteId)
        val asignado = asignaciones[pacienteId]?.fisioterapeutaId
        if (asignado != null && asignado != fisioterapeuta.id) throw AccesoDenegado("Este paciente lo atiende otro fisioterapeuta.")
        val meta = s.metaFuncional.trim()
        if (meta.length !in 5..120) throw DatosNoProcesables("La meta funcional debe tener de 5 a 120 caracteres.")
        if (s.umbralDolor !in 1..10) throw DatosNoProcesables("El umbral de dolor va de 1 a 10.")
        if (s.ejercicios.isEmpty() || s.ejercicios.size > 10) throw DatosNoProcesables("La rutina lleva de 1 a 10 ejercicios.")
        if (s.ejercicios.map { it.idEjercicio }.toSet().size != s.ejercicios.size) throw DatosNoProcesables("Un ejercicio aparece dos veces.")
        val ejercicios = s.ejercicios.map { e ->
            val base = catalogo.firstOrNull { it.id == e.idEjercicio } ?: throw DatosNoProcesables("El ejercicio ${e.idEjercicio} no está en el catálogo.")
            if (e.series !in 1..10 || e.repeticiones !in 1..50 || e.descansoSegundos !in 0..600) {
                throw DatosNoProcesables("«${base.nombre}»: usa de 1 a 10 series, de 1 a 50 repeticiones y hasta 600 s de descanso.")
            }
            if (e.anguloObjetivo != null && e.anguloObjetivo !in 5..180) throw DatosNoProcesables("«${base.nombre}»: el ángulo objetivo va de 5° a 180°.")
            base.copy(series = e.series, repeticiones = e.repeticiones, descansoSegundos = e.descansoSegundos, anguloObjetivo = e.anguloObjetivo ?: base.anguloObjetivo)
        }
        val actual = rutinas[pacienteId]
        val rutina = Rutina(
            id = actual?.id ?: ((rutinas.values.maxOfOrNull { it.id } ?: 100) + 1),
            pacienteId = pacienteId,
            paciente = p.nombre,
            fisioterapeuta = fisioterapeuta.nombre,
            metaFuncional = meta,
            umbralDolor = s.umbralDolor,
            vigenteDesde = hoy().format(FORMATO_MX),
            ejercicios = ejercicios,
        )
        guardarRutina(rutina)
        if (asignado == null) {
            val a = Asignacion(pacienteId, fisioterapeuta.id)
            asignaciones[pacienteId] = a
            colAsignaciones.guardar(a)
        }
        avisar { rutinaActualizada(rutina) }
        return rutina
    }

    private fun guardarRutina(r: Rutina) {
        rutinas[r.pacienteId] = r
        colRutinas.guardar(r)
    }

    // ======================================================================= CITAS (CIT-05..08)

    /** Agenda de un día: el fisioterapeuta solo ve las suyas. */
    fun agenda(usuario: UsuarioPublico, fecha: LocalDate): List<Cita> = citas.values
        .filter { it.fecha == fecha.toString() }
        .filter { usuario.rol != Rol.FISIOTERAPEUTA || it.fisioterapeutaId == usuario.id }
        .sortedWith(compareBy({ it.hora }, { it.id }))

    /** Citas de hoy en adelante de un paciente (CIT-08). */
    fun proximasDe(pacienteId: Int): List<Cita> {
        val hoy = hoy().toString()
        return citas.values.filter { it.pacienteId == pacienteId && it.fecha >= hoy }
            .sortedWith(compareBy({ it.fecha }, { it.hora }))
    }

    fun cita(id: Int): Cita = citas[id] ?: throw NotFoundException("La cita $id no existe.")

    fun agendar(s: CitaSolicitud): Cita {
        val nombre = s.paciente.trim().replace(Regex("\\s+"), " ")
        val pacienteId = s.pacienteId
        val nombreFinal = if (pacienteId != null) paciente(pacienteId).nombre else nombre
        if (nombreFinal.length !in 5..80) throw DatosNoProcesables("Escribe el nombre completo del paciente.")
        val motivo = s.motivo.trim()
        if (motivo.length !in 3..120) throw DatosNoProcesables("Escribe el motivo de la cita (de 3 a 120 caracteres).")
        val f = fisio(s.fisioterapeutaId)
        val (fecha, hora) = validarHorario(s.fecha, s.hora, s.duracionMinutos)
        return synchronized(this) {
            verificarChoques(null, f.id, pacienteId, fecha, hora, s.duracionMinutos)
            val nueva = Cita(
                id = (citas.keys.maxOrNull() ?: 0) + 1,
                hora = hora.format(FORMATO_HORA),
                paciente = nombreFinal,
                fisioterapeutaId = f.id,
                fisioterapeuta = f.nombre,
                motivo = motivo,
                estado = EstadoCita.PENDIENTE,
                fecha = fecha.toString(),
                pacienteId = pacienteId,
                duracionMinutos = s.duracionMinutos,
            )
            guardarCita(nueva)
        }.also { avisar { citaNueva(it) } }
    }

    /**
     * Reprograma (CIT-06). Una cita terminada (atendida, cancelada o no asistió) ya no se mueve.
     *
     * Recepción y administración mueven cualquier cita. El fisioterapeuta solo las suyas, cuando
     * no puede atender: la pasa a otro día u hora, o a otro fisioterapeuta, y debe decir por qué.
     */
    fun reprogramar(id: Int, s: ReprogramarCitaSolicitud, porQuien: UsuarioPublico? = null): Cita = synchronized(this) {
        val actual = cita(id)
        if (actual.estado in TERMINADAS) throw Conflicto("La cita ya está ${actual.estado.texto()}; agenda una nueva.")
        val razon = s.nota?.trim()?.take(200)?.ifBlank { null }
        if (porQuien?.rol == Rol.FISIOTERAPEUTA) {
            if (actual.fisioterapeutaId != porQuien.id) throw AccesoDenegado("Esa cita es de otro fisioterapeuta.")
            if (razon == null || razon.length < 3) throw DatosNoProcesables("Escribe por qué no puedes atender la cita; se le avisa al paciente.")
        }
        val f = s.fisioterapeutaId?.let(::fisio) ?: FisioterapeutaResumen(actual.fisioterapeutaId, actual.fisioterapeuta)
        val duracion = s.duracionMinutos ?: actual.duracionMinutos
        val (fecha, hora) = validarHorario(s.fecha ?: actual.fecha, s.hora ?: actual.hora, duracion)
        verificarChoques(actual.id, f.id, actual.pacienteId, fecha, hora, duracion)
        val motivo = s.motivo?.trim()?.takeIf { it.length in 3..120 } ?: actual.motivo
        val cambio = fecha.toString() != actual.fecha || hora.format(FORMATO_HORA) != actual.hora
        val otroFisio = f.id != actual.fisioterapeutaId
        val notaNueva = when {
            !cambio && !otroFisio -> actual.nota
            porQuien?.rol == Rol.FISIOTERAPEUTA && !cambio -> "La pasó ${actual.fisioterapeuta}: $razon"
            porQuien?.rol == Rol.FISIOTERAPEUTA -> "Reprogramada por ${actual.fisioterapeuta}: $razon (antes ${fechaMx(actual.fecha)} ${actual.hora})"
            !cambio -> "Ahora la atiende ${f.nombre}" + (razon?.let { ": $it" } ?: "")
            else -> "Reprogramada (antes ${fechaMx(actual.fecha)} ${actual.hora})" + (razon?.let { ": $it" } ?: "")
        }
        val nueva = guardarCita(
            actual.copy(
                fecha = fecha.toString(),
                hora = hora.format(FORMATO_HORA),
                fisioterapeutaId = f.id,
                fisioterapeuta = f.nombre,
                duracionMinutos = duracion,
                motivo = motivo,
                // Al moverla vuelve a quedar pendiente de confirmar.
                estado = if (cambio) EstadoCita.PENDIENTE else actual.estado,
                nota = notaNueva,
            )
        )
        if (cambio || otroFisio) avisar { citaReprogramada(actual, nueva, razon, porQuien) }
        nueva
    }

    // ======================================================================= REAGENDA DEL PACIENTE

    /**
     * El paciente mueve su cita a otra hora libre de su mismo fisioterapeuta. Solo si faltan
     * más de 24 h y no la ha movido ya 2 veces; si no, debe mandar una solicitud a recepción.
     */
    fun reagendarPorPaciente(id: Int, s: ReagendarSolicitud, usuario: UsuarioPublico): Cita = synchronized(this) {
        val actual = citaDelPaciente(id, usuario)
        if (!faltanMasDe(actual, HORAS_ANTICIPACION_REAGENDA)) {
            throw DatosNoProcesables("Faltan menos de 24 horas para tu cita. Pide el cambio a recepción con una solicitud.", "REQUIERE_SOLICITUD")
        }
        if (actual.reagendasPaciente >= MAXIMO_REAGENDAS_PACIENTE) {
            throw DatosNoProcesables("Ya cambiaste esta cita $MAXIMO_REAGENDAS_PACIENTE veces. Pide el cambio a recepción con una solicitud.", "REQUIERE_SOLICITUD")
        }
        val (fecha, hora) = validarHorario(s.fecha, s.hora, actual.duracionMinutos)
        if (fecha.toString() == actual.fecha && hora.format(FORMATO_HORA) == actual.hora) throw DatosNoProcesables("Elige un día u hora distintos a los de tu cita.")
        verificarChoques(actual.id, actual.fisioterapeutaId, actual.pacienteId, fecha, hora, actual.duracionMinutos)
        val nueva = guardarCita(
            actual.copy(
                fecha = fecha.toString(),
                hora = hora.format(FORMATO_HORA),
                estado = EstadoCita.PENDIENTE,
                nota = "Reagendada por el paciente (antes ${fechaMx(actual.fecha)} ${actual.hora})",
                reagendasPaciente = actual.reagendasPaciente + 1,
            )
        )
        // Si tenía una solicitud pendiente para esta cita, ya no hace falta.
        solicitudes.values.filter { it.citaId == id && it.estado == EstadoSolicitud.PENDIENTE }.forEach {
            guardarSolicitud(it.copy(estado = EstadoSolicitud.RECHAZADA, respuesta = "El paciente movió la cita él mismo."))
        }
        avisar { citaReprogramada(actual, nueva, null, usuario) }
        nueva
    }

    /** Horas libres para mover una cita: siempre con el mismo fisioterapeuta, salvo que se pida otro. */
    fun horasLibresParaCita(id: Int, fecha: LocalDate, usuario: UsuarioPublico, fisioterapeutaId: Int? = null): List<String> {
        val c = when (usuario.rol) {
            Rol.PACIENTE -> citaDelPaciente(id, usuario)
            Rol.FISIOTERAPEUTA -> cita(id).also { if (it.fisioterapeutaId != usuario.id) throw AccesoDenegado("Esa cita es de otro fisioterapeuta.") }
            else -> cita(id)
        }
        val fisioId = if (usuario.rol == Rol.PACIENTE) c.fisioterapeutaId else fisioterapeutaId ?: c.fisioterapeutaId
        return horasLibres(fisioId, fecha, c.duracionMinutos, excepto = c.id)
    }

    /** Fisioterapeutas libres a la misma hora de la cita (para pasarla cuando el suyo no puede). */
    fun fisiosDisponibles(id: Int, usuario: UsuarioPublico): List<FisioterapeutaResumen> {
        val c = cita(id)
        if (usuario.rol == Rol.FISIOTERAPEUTA && c.fisioterapeutaId != usuario.id) throw AccesoDenegado("Esa cita es de otro fisioterapeuta.")
        val fecha = LocalDate.parse(c.fecha)
        val hora = LocalTime.parse(c.hora, FORMATO_HORA)
        return fisioterapeutas().filter { f ->
            f.id != c.fisioterapeutaId && citasQueOcupan(f.id, fecha, c.id).none { traslapa(hora, c.duracionMinutos, it) }
        }
    }

    // ======================================================================= SOLICITUDES DE REAGENDA

    /** Solicitudes visibles: recepción y administración, todas; el fisioterapeuta, las de sus citas; el paciente, las suyas. */
    fun solicitudes(usuario: UsuarioPublico, estado: EstadoSolicitud? = null): List<SolicitudReagenda> = solicitudes.values
        .filter { estado == null || it.estado == estado }
        .filter {
            when (usuario.rol) {
                Rol.ADMINISTRADOR, Rol.RECEPCIONISTA -> true
                Rol.FISIOTERAPEUTA -> it.fisioterapeutaId == usuario.id
                Rol.PACIENTE -> it.pacienteId == usuario.pacienteId
            }
        }
        .sortedByDescending { it.id }

    /** El paciente pide cambiar su cita; recepción decide. Una sola solicitud pendiente por cita. */
    fun solicitarReagenda(citaId: Int, s: NuevaSolicitudReagenda, usuario: UsuarioPublico): SolicitudReagenda = synchronized(this) {
        val c = citaDelPaciente(citaId, usuario)
        if (solicitudes.values.any { it.citaId == citaId && it.estado == EstadoSolicitud.PENDIENTE }) {
            throw Conflicto("Ya enviaste una solicitud para esta cita; recepción te responderá pronto.")
        }
        val mensaje = s.mensaje.trim().replace(Regex("\\s+"), " ")
        if (mensaje.length !in 3..200) throw DatosNoProcesables("Cuéntale a recepción qué día u horario te acomoda (de 3 a 200 caracteres).")
        val fechaP = s.fechaPropuesta?.trim()?.ifBlank { null }
        val horaP = s.horaPropuesta?.trim()?.ifBlank { null }
        if (fechaP != null || horaP != null) {
            if (fechaP == null || horaP == null) throw DatosNoProcesables("Si propones un horario, indica la fecha y la hora.")
            validarHorario(fechaP, horaP, c.duracionMinutos)
        }
        val nueva = SolicitudReagenda(
            id = (solicitudes.keys.maxOrNull() ?: 0) + 1,
            citaId = c.id,
            pacienteId = c.pacienteId!!,
            paciente = c.paciente,
            fisioterapeutaId = c.fisioterapeutaId,
            fisioterapeuta = c.fisioterapeuta,
            fechaActual = c.fecha,
            horaActual = c.hora,
            fechaPropuesta = fechaP,
            horaPropuesta = horaP,
            mensaje = mensaje,
            creada = LocalDateTime.now(reloj.withZone(ZONA)).withNano(0).toString(),
        )
        guardarSolicitud(nueva)
        avisar { solicitudReagenda(nueva) }
        nueva
    }

    /** Recepción aprueba (mueve la cita) o rechaza (con motivo) la solicitud del paciente. */
    fun resolverSolicitud(id: Int, s: ResolverSolicitud, usuario: UsuarioPublico): SolicitudReagenda = synchronized(this) {
        val sol = solicitudes[id] ?: throw NotFoundException("Esa solicitud no existe.")
        if (sol.estado != EstadoSolicitud.PENDIENTE) throw Conflicto("Esa solicitud ya se resolvió.")
        val nota = s.nota?.trim()?.take(200)?.ifBlank { null }
        val resuelta = if (s.aprobar) {
            val fecha = s.fecha ?: sol.fechaPropuesta
            val hora = s.hora ?: sol.horaPropuesta
            if (fecha == null || hora == null) throw DatosNoProcesables("Elige la fecha y la hora nuevas de la cita.")
            reprogramar(sol.citaId, ReprogramarCitaSolicitud(fecha = fecha, hora = hora, fisioterapeutaId = s.fisioterapeutaId, nota = "Solicitud del paciente"), usuario)
            sol.copy(estado = EstadoSolicitud.APROBADA, respuesta = nota, atendidaPor = usuario.nombre)
        } else {
            if (nota == null || nota.length < 3) throw DatosNoProcesables("Escribe por qué no se puede hacer el cambio; se le avisa al paciente.")
            sol.copy(estado = EstadoSolicitud.RECHAZADA, respuesta = nota, atendidaPor = usuario.nombre)
        }
        guardarSolicitud(resuelta)
        avisar { solicitudResuelta(resuelta) }
        resuelta
    }

    private fun citaDelPaciente(id: Int, usuario: UsuarioPublico): Cita {
        val c = cita(id)
        if (c.pacienteId == null || c.pacienteId != usuario.pacienteId) throw AccesoDenegado("Esa cita no es tuya.")
        if (c.estado in TERMINADAS) throw Conflicto("La cita ya está ${c.estado.texto()}.")
        if (LocalDate.parse(c.fecha).isBefore(hoy())) throw DatosNoProcesables("Esa cita ya pasó.")
        return c
    }

    private fun faltanMasDe(c: Cita, horas: Long): Boolean {
        val inicio = LocalDateTime.of(LocalDate.parse(c.fecha), LocalTime.parse(c.hora, FORMATO_HORA))
        return inicio.isAfter(LocalDateTime.now(reloj.withZone(ZONA)).plusHours(horas))
    }

    private fun guardarSolicitud(s: SolicitudReagenda) {
        solicitudes[s.id] = s
        colSolicitudes.guardar(s)
    }

    /**
     * Cambio de estado según quién lo pide:
     *  - recepción y administración: confirmar, cancelar o registrar la asistencia;
     *  - el fisioterapeuta, en sus citas de hoy o anteriores: atendida o no asistió (CIT-07);
     *  - el paciente, en sus citas futuras: cancelar.
     */
    fun cambiarEstado(id: Int, nuevo: EstadoCita, nota: String?, usuario: UsuarioPublico): Cita = synchronized(this) {
        val actual = cita(id)
        if (actual.estado in TERMINADAS) throw Conflicto("La cita ya está ${actual.estado.texto()}.")
        if (nuevo == actual.estado) return actual
        val dia = LocalDate.parse(actual.fecha)
        when (usuario.rol) {
            Rol.ADMINISTRADOR, Rol.RECEPCIONISTA -> if (nuevo == EstadoCita.PENDIENTE) throw DatosNoProcesables("Una cita no puede regresar a pendiente.")
            Rol.FISIOTERAPEUTA -> {
                if (actual.fisioterapeutaId != usuario.id) throw AccesoDenegado("Esa cita es de otro fisioterapeuta.")
                if (nuevo != EstadoCita.ATENDIDA && nuevo != EstadoCita.NO_ASISTIO) throw AccesoDenegado("Solo puedes marcar la cita como atendida o como no asistió.")
                if (dia.isAfter(hoy())) throw DatosNoProcesables("La asistencia se marca el día de la cita.")
            }
            Rol.PACIENTE -> {
                if (actual.pacienteId == null || actual.pacienteId != usuario.pacienteId) throw AccesoDenegado("Esa cita no es tuya.")
                if (nuevo != EstadoCita.CANCELADA) throw AccesoDenegado("Desde la app solo puedes cancelar tu cita.")
                if (dia.isBefore(hoy())) throw DatosNoProcesables("Esa cita ya pasó.")
            }
        }
        if (nuevo == EstadoCita.ATENDIDA || nuevo == EstadoCita.NO_ASISTIO) {
            if (dia.isAfter(hoy())) throw DatosNoProcesables("La asistencia se marca el día de la cita.")
        }
        val notaLimpia = nota?.trim()?.take(200)?.ifBlank { null }
        val textoNota = when {
            nuevo == EstadoCita.CANCELADA && usuario.rol == Rol.PACIENTE -> "Cancelada por el paciente" + (notaLimpia?.let { ": $it" } ?: "")
            notaLimpia != null -> notaLimpia
            else -> actual.nota
        }
        val nueva = guardarCita(actual.copy(estado = nuevo, nota = textoNota))
        if (nuevo == EstadoCita.CANCELADA && dia >= hoy()) {
            avisar { citaCancelada(nueva, porElPaciente = usuario.rol == Rol.PACIENTE, quienCancela = usuario.id) }
        }
        nueva
    }

    /** Horas de inicio libres de un fisioterapeuta en un día, cada 30 minutos. */
    fun horasLibres(fisioterapeutaId: Int, fecha: LocalDate, duracion: Int, excepto: Int? = null): List<String> {
        val ahora = LocalTime.now(reloj.withZone(ZONA))
        val ocupadas = citasQueOcupan(fisioterapeutaId, fecha, excepto)
        return generateSequence(APERTURA) { it.plusMinutes(30) }
            .takeWhile { !it.plusMinutes(duracion.toLong()).isAfter(CIERRE) && it.isAfter(APERTURA.minusMinutes(1)) }
            .filter { inicio -> ocupadas.none { traslapa(inicio, duracion, it) } }
            .filter { fecha.isAfter(hoy()) || (fecha == hoy() && it.isAfter(ahora)) }
            .map { it.format(FORMATO_HORA) }
            .toList()
    }

    /** Al renombrar a un fisioterapeuta, su nombre se actualiza en citas y rutinas. */
    fun renombrarFisioterapeuta(id: Int, nombre: String) {
        citas.values.filter { it.fisioterapeutaId == id }.forEach { guardarCita(it.copy(fisioterapeuta = nombre)) }
        val suyos = pacientesDe(id)
        rutinas.values.filter { it.pacienteId in suyos }.forEach { guardarRutina(it.copy(fisioterapeuta = nombre)) }
    }

    /** true si el fisioterapeuta tiene pacientes o citas por atender (no se le puede quitar el rol). */
    fun tieneCompromisos(fisioId: Int): Boolean {
        val hoy = hoy().toString()
        return pacientesDe(fisioId).isNotEmpty() ||
            citas.values.any { it.fisioterapeutaId == fisioId && it.fecha >= hoy && it.estado !in TERMINADAS }
    }

    // ======================================================================= AUXILIARES

    /** Un aviso que no se pudo guardar no debe deshacer la operación que lo originó. */
    private inline fun avisar(bloque: AvisosServicio.() -> Unit) {
        avisos?.let { runCatching { it.bloque() } }
    }

    private fun guardarCita(c: Cita): Cita {
        citas[c.id] = c
        colCitas.guardar(c)
        return c
    }

    private fun validarHorario(fechaTexto: String, horaTexto: String, duracion: Int): Pair<LocalDate, LocalTime> {
        val fecha = try {
            LocalDate.parse(fechaTexto.trim())
        } catch (_: DateTimeParseException) {
            throw DatosNoProcesables("La fecha debe tener el formato 2026-11-04.")
        }
        val hora = try {
            LocalTime.parse(horaTexto.trim(), FORMATO_HORA)
        } catch (_: DateTimeParseException) {
            throw DatosNoProcesables("La hora debe tener el formato 09:30.")
        }
        if (duracion !in DURACIONES) throw DatosNoProcesables("La duración debe ser de ${DURACIONES.sorted().joinToString(", ")} minutos.")
        if (fecha.isBefore(hoy())) throw DatosNoProcesables("No se pueden agendar citas en días que ya pasaron.")
        if (fecha.isAfter(hoy().plusDays(120))) throw DatosNoProcesables("Las citas se agendan con máximo 4 meses de anticipación.")
        if (fecha.dayOfWeek == java.time.DayOfWeek.SUNDAY) throw DatosNoProcesables("La clínica no abre en domingo.")
        if (hora.isBefore(APERTURA) || hora.plusMinutes(duracion.toLong()).isAfter(CIERRE)) {
            throw DatosNoProcesables("La clínica atiende de ${APERTURA.format(FORMATO_HORA)} a ${CIERRE.format(FORMATO_HORA)}.")
        }
        if (fecha == hoy() && !hora.isAfter(LocalTime.now(reloj.withZone(ZONA)))) throw DatosNoProcesables("Esa hora de hoy ya pasó.")
        return fecha to hora
    }

    private fun citasQueOcupan(fisioId: Int, fecha: LocalDate, excepto: Int?) = citas.values.filter {
        it.id != excepto && it.fisioterapeutaId == fisioId && it.fecha == fecha.toString() && it.estado != EstadoCita.CANCELADA
    }

    private fun traslapa(inicio: LocalTime, duracion: Int, otra: Cita): Boolean {
        val oInicio = LocalTime.parse(otra.hora, FORMATO_HORA)
        val oFin = oInicio.plusMinutes(otra.duracionMinutos.toLong())
        val fin = inicio.plusMinutes(duracion.toLong())
        return inicio.isBefore(oFin) && oInicio.isBefore(fin)
    }

    private fun verificarChoques(excepto: Int?, fisioId: Int, pacienteId: Int?, fecha: LocalDate, hora: LocalTime, duracion: Int) {
        citasQueOcupan(fisioId, fecha, excepto).firstOrNull { traslapa(hora, duracion, it) }?.let { otra ->
            val fin = LocalTime.parse(otra.hora, FORMATO_HORA).plusMinutes(otra.duracionMinutos.toLong()).format(FORMATO_HORA)
            throw Conflicto("${otra.fisioterapeuta} ya tiene una cita de ${otra.hora} a $fin ese día.")
        }
        if (pacienteId != null) {
            citas.values.firstOrNull {
                it.id != excepto && it.pacienteId == pacienteId && it.fecha == fecha.toString() &&
                    it.estado != EstadoCita.CANCELADA && traslapa(hora, duracion, it)
            }?.let { throw Conflicto("${it.paciente} ya tiene una cita a las ${it.hora} ese día.") }
        }
    }

    private fun fechaMx(iso: String): String = runCatching { LocalDate.parse(iso).format(FORMATO_MX) }.getOrDefault(iso)

    private fun EstadoCita.texto() = when (this) {
        EstadoCita.ATENDIDA -> "atendida"
        EstadoCita.CANCELADA -> "cancelada"
        EstadoCita.NO_ASISTIO -> "marcada como «no asistió»"
        EstadoCita.CONFIRMADA -> "confirmada"
        EstadoCita.PENDIENTE -> "pendiente"
    }
}
