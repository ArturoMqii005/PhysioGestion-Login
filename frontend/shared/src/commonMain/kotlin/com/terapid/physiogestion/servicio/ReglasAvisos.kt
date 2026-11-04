package com.terapid.physiogestion.servicio

import com.terapid.physiogestion.modelo.Aviso
import com.terapid.physiogestion.modelo.BandejaAvisos
import com.terapid.physiogestion.modelo.Cita

/**
 * Recordatorio de una cita para programarlo en el teléfono (NOT-02).
 * La plataforma convierte [fecha] + [hora] − [minutosAntes] a su reloj local.
 */
data class RecordatorioCita(
    /** Único por cita y momento («cita-7-60»): al reprogramar se reemplaza, no se duplica. */
    val clave: String,
    val citaId: Int,
    val fecha: String,
    val hora: String,
    val minutosAntes: Int,
    val titulo: String,
    val texto: String,
)

/** Reglas de los avisos y recordatorios; sin dependencias de plataforma para poder probarlas. */
object ReglasAvisos {
    /** Un día y una hora antes de cada cita. */
    val ANTICIPACIONES = listOf(24 * 60, 60)

    /** El recordatorio de la rutina se puede poner entre las 06:00 y las 22:00, cada 30 min. */
    const val PRIMERA_HORA = 6 * 60
    const val ULTIMA_HORA = 22 * 60
    const val PASO_MINUTOS = 30
    const val HORA_SUGERIDA = 18 * 60

    /**
     * Avisos que todavía no se notificaron: sin leer y con id mayor a [ultimoVisto].
     * Si [ultimoVisto] es null (primera revisión en este teléfono) no se notifica
     * nada: lo anterior ya aparece en la bandeja y no conviene llenar la pantalla.
     */
    fun paraNotificar(bandeja: BandejaAvisos, ultimoVisto: Int?): List<Aviso> {
        if (ultimoVisto == null) return emptyList()
        return bandeja.avisos.filter { !it.leido && it.id > ultimoVisto }.sortedBy { it.id }
    }

    /** Recordatorios de las citas abiertas de hoy en adelante, un día y una hora antes. */
    fun recordatoriosDeCitas(citas: List<Cita>, hoy: String): List<RecordatorioCita> =
        citas.filter { it.abierta && it.fecha.isNotBlank() && it.fecha >= hoy }
            .sortedWith(compareBy({ it.fecha }, { it.hora }))
            .flatMap { cita ->
                ANTICIPACIONES.map { antes ->
                    val (titulo, texto) = textoCita(cita, antes)
                    RecordatorioCita("cita-${cita.id}-$antes", cita.id, cita.fecha, cita.hora, antes, titulo, texto)
                }
            }

    fun textoCita(cita: Cita, minutosAntes: Int): Pair<String, String> {
        val con = "a las ${cita.hora} con ${cita.fisioterapeuta}"
        return if (minutosAntes >= 24 * 60) {
            "Mañana tienes cita" to "Mañana $con · ${cita.motivo}. Si no puedes ir, cancélala desde la app."
        } else {
            "Tu cita es en una hora" to "Hoy $con. Llega unos minutos antes."
        }
    }

    fun textoPendientes(cantidad: Int): Pair<String, String> = if (cantidad == 1) {
        "Tienes 1 sesión sin enviar" to "Está guardada en tu teléfono. Abre la app con internet para que llegue a tu fisioterapeuta."
    } else {
        "Tienes $cantidad sesiones sin enviar" to "Están guardadas en tu teléfono. Abre la app con internet para que lleguen a tu fisioterapeuta."
    }

    val TEXTO_RUTINA = "Es hora de tu rutina" to "Hoy todavía no registras tu sesión. Te toma unos minutos y tu fisioterapeuta la verá."

    /** 1110 → «18:30». */
    fun horaLegible(minutosDelDia: Int): String =
        "${(minutosDelDia / 60).toString().padStart(2, '0')}:${(minutosDelDia % 60).toString().padStart(2, '0')}"

    /** Mueve la hora del recordatorio media hora, sin salir de 06:00–22:00. */
    fun ajustarHora(minutosDelDia: Int, pasos: Int): Int =
        (minutosDelDia + pasos * PASO_MINUTOS).coerceIn(PRIMERA_HORA, ULTIMA_HORA)
}
