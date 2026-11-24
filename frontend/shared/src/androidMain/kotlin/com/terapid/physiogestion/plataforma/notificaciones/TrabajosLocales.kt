package com.terapid.physiogestion.plataforma.notificaciones

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.terapid.physiogestion.datos.sensores.SensorPresion
import com.terapid.physiogestion.dependencias.Dependencias
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.servicio.ReglasLocales
import com.terapid.physiogestion.servicio.TextoAviso
import kotlinx.coroutines.CancellationException
import java.util.Calendar
import java.util.concurrent.TimeUnit

/*
 * NOTIFICACIONES LOCALES programadas en el teléfono (el texto vive en ReglasLocales,
 * código común con pruebas). Cada trabajo se ejecuta una vez y se vuelve a programar
 * para la siguiente fecha; Android los conserva si el teléfono se reinicia.
 */

/** Qué notificación local es, cuándo sale y en qué canal. */
enum class Local(val trabajo: String, val hora: Int, val minuto: Int, val diaSemana: Int?, val canal: String, val id: Int) {
    /** Personal: resumen de las citas de hoy. */
    AGENDA("local-agenda", 7, 30, null, NotificadorAndroid.CANAL_AGENDA, NotificadorAndroid.ID_AGENDA),

    /** Paciente: su racha se pierde si hoy no entrena. */
    RACHA("local-racha", 20, 0, null, NotificadorAndroid.CANAL_PROGRESO, NotificadorAndroid.ID_RACHA),

    /** Paciente: resumen de la semana, los domingos. */
    RESUMEN("local-resumen", 19, 0, Calendar.SUNDAY, NotificadorAndroid.CANAL_PROGRESO, NotificadorAndroid.ID_RESUMEN),

    /** Paciente: prueba de escaleras, los lunes (solo con barómetro). */
    ESCALERAS("local-escaleras", 10, 0, Calendar.MONDAY, NotificadorAndroid.CANAL_PROGRESO, NotificadorAndroid.ID_ESCALERAS),
}

object ProgramadorLocales {
    private const val TRABAJO_COLA = "local-cola"
    private const val DATO_LOCAL = "local"

    fun programar(context: Context, rol: Rol) {
        val trabajos = WorkManager.getInstance(context)
        val deRol = if (rol == Rol.PACIENTE) {
            buildList {
                add(Local.RACHA)
                add(Local.RESUMEN)
                if (SensorPresion().estaDisponible) add(Local.ESCALERAS)
            }
        } else {
            listOf(Local.AGENDA)
        }
        Local.entries.filter { it !in deRol }.forEach { trabajos.cancelUniqueWork(it.trabajo) }
        deRol.forEach { programarSiguiente(context, it, ExistingWorkPolicy.KEEP) }

        if (rol == Rol.PACIENTE) {
            // Cada 2 horas, CON o SIN internet: sesiones guardadas que no han llegado al servidor.
            val cola = PeriodicWorkRequestBuilder<RevisarColaTrabajo>(2, TimeUnit.HOURS).build()
            trabajos.enqueueUniquePeriodicWork(TRABAJO_COLA, ExistingPeriodicWorkPolicy.KEEP, cola)
        } else {
            trabajos.cancelUniqueWork(TRABAJO_COLA)
        }
    }

    fun cancelar(context: Context) {
        val trabajos = WorkManager.getInstance(context)
        Local.entries.forEach { trabajos.cancelUniqueWork(it.trabajo) }
        trabajos.cancelUniqueWork(TRABAJO_COLA)
    }

    /** KEEP al iniciar sesión (no mueve lo ya programado); APPEND_OR_REPLACE desde el propio trabajo. */
    fun programarSiguiente(context: Context, local: Local, politica: ExistingWorkPolicy) {
        val trabajo = OneTimeWorkRequestBuilder<NotificacionLocalTrabajo>()
            .setInitialDelay(esperaHasta(local.hora, local.minuto, local.diaSemana), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(DATO_LOCAL to local.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(local.trabajo, politica, trabajo)
    }

    fun localDe(parametros: WorkerParameters): Local? =
        parametros.inputData.getString(DATO_LOCAL)?.let { nombre -> Local.entries.firstOrNull { it.name == nombre } }

    /** Milisegundos hasta la próxima vez que el reloj marque esa hora (y ese día de la semana, si se indica). */
    private fun esperaHasta(hora: Int, minuto: Int, diaSemana: Int?): Long {
        val ahora = Calendar.getInstance()
        val objetivo = (ahora.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hora)
            set(Calendar.MINUTE, minuto)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (timeInMillis <= ahora.timeInMillis + 60_000L || (diaSemana != null && get(Calendar.DAY_OF_WEEK) != diaSemana)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return objetivo.timeInMillis - ahora.timeInMillis
    }
}

/** Muestra la notificación local que le toca y se programa para la siguiente vez. */
class NotificacionLocalTrabajo(contexto: Context, private val parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val local = ProgramadorLocales.localDe(parametros) ?: return Result.success()
        val rol = Dependencias.autenticacion.sesion.value?.usuario?.rol ?: return Result.success() // sesión cerrada: se detiene
        val texto: TextoAviso? = try {
            when (local) {
                Local.AGENDA -> Dependencias.locales.agendaDelDia()
                Local.RACHA -> Dependencias.locales.rachaEnRiesgo()
                Local.RESUMEN -> Dependencias.locales.resumenSemanal()
                Local.ESCALERAS -> Dependencias.locales.escaleras()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // la agenda necesita internet; si no hay, mañana sale la siguiente
        }
        texto?.let { (Dependencias.notificador as? NotificadorAndroid)?.mostrar(local.id, local.canal, it.titulo, it.texto) }
        val sigueValido = (local == Local.AGENDA) == (rol != Rol.PACIENTE)
        if (sigueValido) ProgramadorLocales.programarSiguiente(applicationContext, local, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

/** Cada 2 horas, sin necesitar internet: sesiones que siguen guardadas solo en el teléfono. */
class RevisarColaTrabajo(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val texto = runCatching { Dependencias.locales.pendientes() }.getOrNull() ?: return Result.success()
        (Dependencias.notificador as? NotificadorAndroid)
            ?.mostrar(NotificadorAndroid.ID_PENDIENTES, NotificadorAndroid.CANAL_RECORDATORIOS, texto.titulo, texto.texto)
        return Result.success()
    }
}

/**
 * Fin del descanso entre series con AlarmManager: a diferencia de WorkManager, suena a
 * tiempo aunque el teléfono esté en reposo (setAndAllowWhileIdle no pide permiso de alarma exacta).
 */
object AlarmaDescanso {
    private const val EXTRA_SIGUIENTE = "siguiente"
    private const val CODIGO = 77

    private fun intencion(context: Context, siguiente: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context, CODIGO,
            Intent(context, FinDescansoReceptor::class.java).apply { siguiente?.let { putExtra(EXTRA_SIGUIENTE, it) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun programar(context: Context, segundos: Int, siguiente: String) {
        if (segundos <= 0) return
        val alarmas = context.getSystemService(AlarmManager::class.java) ?: return
        alarmas.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + segundos * 1000L,
            intencion(context, siguiente),
        )
    }

    fun cancelar(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(intencion(context, null))
    }

    fun siguienteDe(intent: Intent): String = intent.getStringExtra(EXTRA_SIGUIENTE) ?: "tu siguiente serie"
}

class FinDescansoReceptor : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val texto = ReglasLocales.finDescanso(AlarmaDescanso.siguienteDe(intent))
        (Dependencias.notificador as? NotificadorAndroid)
            ?.mostrar(NotificadorAndroid.ID_DESCANSO, NotificadorAndroid.CANAL_EJERCICIO, texto.titulo, texto.texto, urgente = true)
    }
}
