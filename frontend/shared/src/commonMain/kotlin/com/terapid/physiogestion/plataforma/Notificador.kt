package com.terapid.physiogestion.plataforma

import com.terapid.physiogestion.modelo.Aviso
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.servicio.RecordatorioCita

/**
 * NOTIFICACIONES DEL SISTEMA: push (Firebase Cloud Messaging) y locales.
 *
 * El código común decide QUÉ notificar; cada plataforma decide CÓMO. En
 * Android lo implementa NotificadorAndroid (canales, Firebase, WorkManager y
 * AlarmManager) y lo registra la clase Application al arrancar. En escritorio e
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

    /**
     * Al abrir sesión: revisa avisos en segundo plano aunque la app esté cerrada y
     * programa los avisos locales del rol (agenda del día, racha, resumen semanal…).
     */
    fun iniciarRevision(rol: Rol)

    /** Token de Firebase Cloud Messaging de este teléfono; null si no hay Firebase. */
    suspend fun tokenPush(): String? = null

    /** El paciente salió de la app mientras descansa entre series: avisa cuando termine. */
    fun programarFinDescanso(segundos: Int, siguiente: String) = Unit

    /** Volvió a la app o terminó el descanso. */
    fun cancelarFinDescanso() = Unit

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
