package com.terapid.physiogestion.servicio

import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.datos.GestorSesion
import com.terapid.physiogestion.datos.PreferenciasRepositorio
import com.terapid.physiogestion.datos.SesionesRepositorio
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.modelo.Rol
import kotlin.math.roundToInt

/** Título y texto de una notificación. */
data class TextoAviso(val titulo: String, val texto: String)

/**
 * NOTIFICACIONES LOCALES: las decide el teléfono con sus propios datos, sin
 * esperar al servidor. Aquí solo está el texto y cuándo vale la pena mostrarlas
 * (código común, con pruebas); Android las programa con WorkManager y AlarmManager.
 *
 *  - Agenda del día (personal, 7:30).
 *  - Racha en riesgo (paciente, 20:00, solo si hoy no ha entrenado).
 *  - Resumen semanal (paciente, domingo 19:00, con las sesiones guardadas en el teléfono).
 *  - Prueba de escaleras (paciente, lunes 10:00, solo si el teléfono tiene barómetro).
 *  - Fin del descanso entre series (paciente, si salió de la app mientras descansa).
 */
object ReglasLocales {

    fun agendaDelDia(citas: List<Cita>, rol: Rol, hoy: String): TextoAviso? {
        val deHoy = citas.filter { it.abierta && it.fecha == hoy }.sortedBy { it.hora }
        if (deHoy.isEmpty()) return null
        val n = deHoy.size
        val primera = deHoy.first()
        val porConfirmar = deHoy.count { it.estado == EstadoCita.PENDIENTE }
        val titulo = when {
            rol == Rol.FISIOTERAPEUTA && n == 1 -> "Hoy tienes 1 cita"
            rol == Rol.FISIOTERAPEUTA -> "Hoy tienes $n citas"
            n == 1 -> "Hoy hay 1 cita en la clínica"
            else -> "Hoy hay $n citas en la clínica"
        }
        val texto = buildString {
            append("La primera es a las ${primera.hora} con ${primera.paciente}")
            if (n > 1) append("; la última, a las ${deHoy.last().hora}")
            append(".")
            when (porConfirmar) {
                0 -> Unit
                1 -> append(" 1 falta por confirmar.")
                else -> append(" $porConfirmar faltan por confirmar.")
            }
        }
        return TextoAviso(titulo, texto)
    }

    fun rachaEnRiesgo(racha: Int, yaEntrenoHoy: Boolean): TextoAviso? = when {
        racha <= 0 || yaEntrenoHoy -> null
        racha == 1 -> TextoAviso("Xochi te espera para tu rutina", "Ayer empezaste tu racha. Haz tu rutina de hoy para llevarla a 2 días.")
        else -> TextoAviso("¡No pierdas tu racha de $racha días!", "Todavía estás a tiempo: tu rutina de hoy toma unos minutos y Xochi te acompaña.")
    }

    /** Con las sesiones de los últimos 7 días (de [hoy] hacia atrás). */
    fun resumenSemanal(sesiones: List<RegistroSesion>, hoy: String): TextoAviso {
        val desde = ReglasAgenda.sumarDias(hoy, -6)
        val semana = sesiones.filter { it.claveDia in desde..hoy }
        if (semana.isEmpty()) {
            return TextoAviso("Tu semana en PhysioGestion", "Esta semana no registraste sesiones. Mañana es buen día para retomar tu rutina; Xochi te acompaña.")
        }
        val dias = semana.map { it.claveDia }.distinct().size
        val cumplimiento = semana.map { it.cumplimiento }.average().roundToInt()
        val antes = semana.map { it.dolorAntes }.average()
        val despues = semana.map { it.dolorDespues }.average()
        val dolor = when {
            despues <= antes - 0.5 -> " y, en promedio, tu dolor bajó de ${antes.uno()} a ${despues.uno()} al entrenar"
            despues >= antes + 0.5 -> ". Tu dolor subió al entrenar (${antes.uno()} → ${despues.uno()}); coméntalo con tu fisioterapeuta"
            else -> ""
        }
        val cierre = if (dias >= 5) " ¡Semana de campeón!" else " ¡Sigue así!"
        return TextoAviso(
            "Tu semana: $dias ${if (dias == 1) "día" else "días"} de rutina",
            "Hiciste $cumplimiento % de tus ejercicios$dolor.$cierre".replace("..", "."),
        )
    }

    val ESCALERAS = TextoAviso(
        "Prueba de escaleras de la semana",
        "Sube un piso con el teléfono en la mano para medir cómo vas. Toma menos de dos minutos.",
    )

    fun finDescanso(siguiente: String) = TextoAviso("¡Se acabó el descanso!", "Sigue con $siguiente. Xochi te espera en la app.")

    /** 4.333 -> «4.3»; 4.0 -> «4». */
    private fun Double.uno(): String {
        val r = (this * 10).roundToInt()
        return if (r % 10 == 0) "${r / 10}" else "${r / 10}.${r % 10}"
    }
}

/** Junta los datos que necesita cada notificación local (lo usan los trabajos de Android). */
class AvisosLocales(
    private val gestor: GestorSesion,
    private val clinica: ClinicaRepositorio,
    private val sesiones: SesionesRepositorio,
    private val preferencias: PreferenciasRepositorio,
    private val hoy: () -> String,
) {
    private val usuario get() = gestor.sesion.value?.usuario
    private val pacienteId get() = usuario?.takeIf { it.rol == Rol.PACIENTE }?.pacienteId

    /** Necesita internet (las citas están en el servidor). */
    suspend fun agendaDelDia(): TextoAviso? {
        val u = usuario?.takeIf { it.rol != Rol.PACIENTE } ?: return null
        val fecha = hoy()
        return ReglasLocales.agendaDelDia(clinica.citas(fecha), u.rol, fecha)
    }

    fun rachaEnRiesgo(): TextoAviso? {
        val id = pacienteId ?: return null
        return ReglasLocales.rachaEnRiesgo(preferencias.racha(id), preferencias.yaEntrenoHoy(id))
    }

    /** Sin internet: usa las sesiones guardadas en el teléfono. */
    suspend fun resumenSemanal(): TextoAviso? {
        pacienteId ?: return null
        return ReglasLocales.resumenSemanal(sesiones.historialLocal(100), hoy())
    }

    /** Sesiones guardadas que no han llegado al servidor (una vez al día como máximo). */
    suspend fun pendientes(): TextoAviso? {
        pacienteId ?: return null
        val n = sesiones.pendientesDeEnvio()
        if (n == 0 || !preferencias.tocaAvisarPendientes()) return null
        val (titulo, texto) = ReglasAvisos.textoPendientes(n)
        return TextoAviso(titulo, texto)
    }

    fun escaleras(): TextoAviso? = pacienteId?.let { ReglasLocales.ESCALERAS }
}
