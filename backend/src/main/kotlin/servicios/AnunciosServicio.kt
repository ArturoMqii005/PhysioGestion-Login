package com.terapid.physiogestion.servicios

import com.terapid.physiogestion.datos.AlmacenDocumentos
import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.datos.Coleccion
import com.terapid.physiogestion.modelos.Anuncio
import com.terapid.physiogestion.modelos.AnuncioSolicitud
import com.terapid.physiogestion.modelos.Destinatarios
import com.terapid.physiogestion.modelos.ResultadoRevision
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.TipoAviso
import com.terapid.physiogestion.modelos.UsuarioPublico
import com.terapid.physiogestion.plugins.DatosNoProcesables
import com.terapid.physiogestion.plugins.DemasiadosIntentos
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * ANUNCIOS DE LA CLÍNICA (push por tema): recepción o administración escriben un aviso
 * general («Cerramos el lunes festivo») para pacientes, personal o todos. Llega como
 * push a los teléfonos suscritos y queda en la bandeja de avisos de cada persona.
 */
class AnunciosServicio(
    private val cuentas: AutenticacionServicio,
    private val avisos: AvisosServicio,
    private val push: PushServicio,
    private val reloj: Clock = Clock.systemUTC(),
    almacen: AlmacenDocumentos = AlmacenEnMemoria(),
    json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        /** Para que los anuncios no se vuelvan ruido: máximo 3 en 24 horas. */
        const val MAXIMO_POR_DIA = 3
        private const val MS_DIA = 86_400_000L
    }

    private val col = Coleccion(almacen, "anuncios", Anuncio.serializer(), json) { it.id.toString() }
    private val anuncios = ConcurrentHashMap<Int, Anuncio>().apply { col.cargar().forEach { put(it.id, it) } }
    private val siguiente = AtomicInteger((anuncios.keys.maxOrNull() ?: 0) + 1)

    fun recientes(limite: Int = 30): List<Anuncio> = anuncios.values.sortedByDescending { it.id }.take(limite)

    @Synchronized
    fun publicar(autor: UsuarioPublico, s: AnuncioSolicitud): Anuncio {
        val titulo = s.titulo.trim().replace(Regex("\\s+"), " ")
        val texto = s.texto.trim()
        if (titulo.length !in 3..60) throw DatosNoProcesables("El título debe tener entre 3 y 60 caracteres.")
        if (texto.length !in 5..300) throw DatosNoProcesables("El texto debe tener entre 5 y 300 caracteres.")

        val ahora = reloj.millis()
        val ultimos = anuncios.values.filter { it.creado > ahora - MS_DIA }
        if (ultimos.size >= MAXIMO_POR_DIA) {
            val libre = (ultimos.minOf { it.creado } + MS_DIA - ahora) / 1000
            throw DemasiadosIntentos("Ya se publicaron $MAXIMO_POR_DIA anuncios en las últimas 24 horas. Espera para publicar otro.", libre)
        }

        val personas = cuentas.usuarios().filter { u ->
            u.activo && u.id != autor.id && when (s.destinatarios) {
                Destinatarios.PACIENTES -> u.rol == Rol.PACIENTE
                Destinatarios.PERSONAL -> u.rol != Rol.PACIENTE
                Destinatarios.TODOS -> true
            }
        }
        val anuncio = Anuncio(siguiente.getAndIncrement(), titulo, texto, s.destinatarios, autor.nombre, ahora, personas.size)
        anuncios[anuncio.id] = anuncio
        col.guardar(anuncio)
        personas.forEach { avisos.anuncio(it.id, titulo, texto) }
        push.enviarAnuncio(s.destinatarios, anuncio.id, titulo, texto)
        return anuncio
    }
}

/**
 * REVISIÓN DIARIA (push programada): cada mañana GitHub Actions llama a
 * POST /tareas/revision-diaria y el servidor le avisa a cada fisioterapeuta qué
 * pacientes con rutina llevan 3 días o más sin registrar una sesión.
 */
class RevisionDiariaServicio(
    private val cuentas: AutenticacionServicio,
    private val clinica: ClinicaServicio,
    private val sesiones: SesionesServicio,
    private val avisos: AvisosServicio,
    private val reloj: Clock = Clock.systemUTC(),
) {
    companion object {
        const val DIAS_SIN_ACTIVIDAD = 3L
    }

    data class Inactivo(val pacienteId: Int, val nombre: String, val dias: Long?)

    /** Se puede llamar varias veces el mismo día: cada fisioterapeuta recibe un solo aviso por día. */
    @Synchronized
    fun ejecutar(): ResultadoRevision {
        val ahora = LocalDateTime.ofInstant(reloj.instant(), ClinicaServicio.ZONA)
        val hoy = ahora.toLocalDate()
        val inicioDelDia = hoy.atStartOfDay(ClinicaServicio.ZONA).toInstant().toEpochMilli()
        val conocidos = clinica.pacientesConocidos().filter { it.tieneCuenta }
        var fisios = 0
        var pacientes = 0
        cuentas.usuarios().filter { it.rol == Rol.FISIOTERAPEUTA && it.activo }.forEach { fisio ->
            if (avisos.tieneDesde(fisio.id, TipoAviso.PACIENTES_INACTIVOS, inicioDelDia)) return@forEach
            val suyos = clinica.pacientesDe(fisio.id) + sesiones.pacientesDe(fisio.nombre)
            val inactivos = conocidos.filter { it.pacienteId in suyos && sesiones.rutinaDelPaciente(it.pacienteId) != null }.mapNotNull { p ->
                val dias = sesiones.ultimaSesion(p.pacienteId)?.let { diasDesde(it, hoy) }
                if (dias == null || dias >= DIAS_SIN_ACTIVIDAD) Inactivo(p.pacienteId, p.nombre, dias) else null
            }.sortedWith(compareByDescending<Inactivo> { it.dias ?: Long.MAX_VALUE }.thenBy { it.nombre })
            if (inactivos.isEmpty()) return@forEach
            avisos.pacientesInactivos(fisio.id, inactivos)
            fisios++
            pacientes += inactivos.size
        }
        return ResultadoRevision(fisios, pacientes)
    }

    private fun diasDesde(fechaHora: String, hoy: LocalDate): Long? =
        runCatching { ChronoUnit.DAYS.between(LocalDateTime.parse(fechaHora).toLocalDate(), hoy) }.getOrNull()
}
