package com.terapid.physiogestion.plataforma

import com.terapid.physiogestion.modelo.Aviso
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.servicio.RecordatorioCita

/**
 * NOTIFICACIONES DEL SISTEMA (NOT-01..04), sin Firebase.
 *
 * El código común decide QUÉ notificar; cada plataforma decide CÓMO. En
 * Android lo implementa NotificadorAndroid (canales de notificación y
 * WorkManager) y lo registra la clase Application al arrancar. En escritorio e
 * iOS se usa [SinNotificaciones]: los avisos se ven igual dentro de la app.
 */
interface Notificador {
    /** false donde la app no muestra notificaciones del sistema. */
    val disponible: Boolean

    /** true si el usuario permite las notificaciones de la app. */
    fun permitidas(): Boolean

    /** Muestra los avisos nuevos del servidor (dolor alto, citas, rutina…). */
    fun mostrarAvisos(avisos: List<Aviso>)

    /** NOT-04: sesiones guardadas en el teléfono que todavía no llegan al servidor. */
    fun mostrarPendientes(cantidad: Int)

    /** NOT-02: reemplaza los recordatorios de cita programados por estos. */
    fun programarRecordatoriosDeCitas(recordatorios: List<RecordatorioCita>)

    /** NOT-01: recordatorio diario de la rutina a [minutosDelDia] (18:30 = 1110); null lo apaga. */
    fun programarRecordatorioRutina(minutosDelDia: Int?)

    /** Al abrir sesión: revisa avisos en segundo plano aunque la app esté cerrada. */
    fun iniciarRevision(rol: Rol)

    /** Al cerrar sesión: cancela la revisión, los recordatorios y las notificaciones visibles. */
    fun detenerTodo()
}

/** Plataformas sin notificaciones del sistema (por ahora escritorio e iOS). */
object SinNotificaciones : Notificador {
    override val disponible = false
    override fun permitidas() = false
    override fun mostrarAvisos(avisos: List<Aviso>) = Unit
    override fun mostrarPendientes(cantidad: Int) = Unit
    override fun programarRecordatoriosDeCitas(recordatorios: List<RecordatorioCita>) = Unit
    override fun programarRecordatorioRutina(minutosDelDia: Int?) = Unit
    override fun iniciarRevision(rol: Rol) = Unit
    override fun detenerTodo() = Unit
}
