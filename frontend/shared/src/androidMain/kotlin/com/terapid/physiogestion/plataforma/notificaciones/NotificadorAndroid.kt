package com.terapid.physiogestion.plataforma.notificaciones

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.terapid.physiogestion.modelo.Aviso
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.servicio.RecordatorioCita
import com.terapid.physiogestion.servicio.ReglasAvisos
import com.terapid.physiogestion.plataforma.Notificador
import com.terapid.physiogestion.shared.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Notificaciones de Android SIN Firebase Cloud Messaging.
 *
 * Firebase empuja el aviso desde el servidor al teléfono. Aquí es al revés: el
 * teléfono pregunta. Con la app abierta pregunta cada 30 s (la vista del rol);
 * cerrada, WorkManager ejecuta [RevisarAvisosTrabajo] cada 15 min (el mínimo que
 * permite Android) cuando hay internet, y Android decide el momento exacto para
 * cuidar la batería. Los recordatorios de cita y de rutina no necesitan al
 * servidor: se programan en el teléfono con trabajos de una sola vez.
 *
 * Canales (el usuario puede silenciar cada uno en Ajustes):
 *  - «Avisos urgentes»: dolor alto de un paciente (importancia alta, sale encima de la pantalla).
 *  - «Avisos de la clínica»: citas, rutina y pacientes nuevos.
 *  - «Recordatorios»: citas, rutina diaria y sesiones sin enviar.
 */
class NotificadorAndroid(private val context: Context) : Notificador {

    companion object {
        const val CANAL_URGENTE = "avisos_urgentes"
        const val CANAL_AVISOS = "avisos_clinica"
        const val CANAL_RECORDATORIOS = "recordatorios"

        const val TRABAJO_REVISION = "revisar-avisos"
        const val TRABAJO_RUTINA = "recordatorio-rutina"
        const val ETIQUETA_CITAS = "recordatorio-cita"

        const val DATO_ID = "id"
        const val DATO_TITULO = "titulo"
        const val DATO_TEXTO = "texto"
        const val DATO_MINUTOS = "minutos"

        /** Ids de notificación: fijos para los que se reemplazan, y por rango para avisos y citas. */
        const val ID_PENDIENTES = 2
        const val ID_RUTINA = 3
        private const val BASE_AVISOS = 1_000
        private const val BASE_CITAS = 1_000_000

        /** Máximo de avisos sueltos de una vez; si llegan más, uno de resumen. */
        private const val MAXIMO_SUELTOS = 4
        private const val COLOR = 0xFF14B8A6.toInt()
    }

    private val sistema = NotificationManagerCompat.from(context)
    private val trabajos get() = WorkManager.getInstance(context)

    override val disponible = true

    /** Se llama una vez al arrancar la app. En Android 7 (sin canales) no hace nada. */
    fun crearCanales() {
        listOf(
            NotificationChannelCompat.Builder(CANAL_URGENTE, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Avisos urgentes")
                .setDescription("Un paciente terminó su sesión con dolor alto.")
                .build(),
            NotificationChannelCompat.Builder(CANAL_AVISOS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Avisos de la clínica")
                .setDescription("Citas nuevas, reprogramadas o canceladas, cambios de rutina y pacientes asignados.")
                .build(),
            NotificationChannelCompat.Builder(CANAL_RECORDATORIOS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Recordatorios")
                .setDescription("Tus citas, tu rutina del día y las sesiones que faltan por enviar.")
                .build(),
        ).forEach(sistema::createNotificationChannel)
    }

    override fun permitidas(): Boolean = sistema.areNotificationsEnabled()

    // ======================================================================= AVISOS DEL SERVIDOR

    override fun mostrarAvisos(avisos: List<Aviso>) {
        if (avisos.isEmpty()) return
        val sueltos = if (avisos.size > MAXIMO_SUELTOS) avisos.takeLast(MAXIMO_SUELTOS - 1) else avisos
        sueltos.forEach { a ->
            mostrar(BASE_AVISOS + a.id, if (a.urgente) CANAL_URGENTE else CANAL_AVISOS, a.titulo, a.mensaje, urgente = a.urgente)
        }
        if (avisos.size > MAXIMO_SUELTOS) {
            val resto = avisos.size - sueltos.size
            mostrar(BASE_AVISOS, CANAL_AVISOS, "Tienes $resto avisos más", "Ábrelos en PhysioGestion > Avisos.")
        }
    }

    override fun mostrarPendientes(cantidad: Int) {
        val (titulo, texto) = ReglasAvisos.textoPendientes(cantidad)
        mostrar(ID_PENDIENTES, CANAL_RECORDATORIOS, titulo, texto)
    }

    /** Muestra una notificación que abre la app al tocarla. Sin permiso no hace nada. */
    @SuppressLint("MissingPermission") // se revisa con areNotificationsEnabled y se atrapa SecurityException
    fun mostrar(id: Int, canal: String, titulo: String, texto: String, urgente: Boolean = false) {
        if (!permitidas()) return
        val abrir = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            // Si la app ya estaba abierta, la trae al frente en lugar de abrir otra copia.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        val alTocar = abrir?.let { PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val notificacion = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(COLOR)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(if (urgente) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (urgente) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(alTocar)
            .build()
        try {
            sistema.notify(id, notificacion)
        } catch (_: SecurityException) {
            // El usuario quitó el permiso justo ahora: no hay nada más que hacer.
        }
    }

    // ======================================================================= RECORDATORIOS EN EL TELÉFONO

    /**
     * NOT-02. Borra los recordatorios de cita anteriores y programa los nuevos;
     * así una cita cancelada o reprogramada no deja un recordatorio viejo.
     */
    override fun programarRecordatoriosDeCitas(recordatorios: List<RecordatorioCita>) {
        trabajos.cancelAllWorkByTag(ETIQUETA_CITAS)
        val ahora = System.currentTimeMillis()
        recordatorios.forEach { r ->
            val inicio = momentoLocal(r.fecha, r.hora) ?: return@forEach
            val espera = inicio - r.minutosAntes * 60_000L - ahora
            if (espera <= 0) return@forEach // ese momento ya pasó (p. ej., la cita es en menos de un día)
            // Un id por cita y momento (un día antes y una hora antes).
            val id = BASE_CITAS + r.citaId * 2 + if (r.minutosAntes >= 24 * 60) 0 else 1
            val trabajo = OneTimeWorkRequestBuilder<RecordatorioTrabajo>()
                .setInitialDelay(espera, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(DATO_ID to id, DATO_TITULO to r.titulo, DATO_TEXTO to r.texto))
                .addTag(ETIQUETA_CITAS)
                .build()
            trabajos.enqueueUniqueWork(r.clave, ExistingWorkPolicy.REPLACE, trabajo)
        }
    }

    /** NOT-01. Programa (o apaga) el recordatorio diario; el trabajo se vuelve a programar solo cada día. */
    override fun programarRecordatorioRutina(minutosDelDia: Int?) = programarRutina(minutosDelDia, ExistingWorkPolicy.REPLACE)

    /**
     * [politica] REPLACE al cambiar la hora desde el perfil. El propio trabajo usa
     * APPEND_OR_REPLACE para programar el de mañana sin cancelarse a sí mismo.
     */
    fun programarRutina(minutosDelDia: Int?, politica: ExistingWorkPolicy) {
        if (minutosDelDia == null) {
            trabajos.cancelUniqueWork(TRABAJO_RUTINA)
            sistema.cancel(ID_RUTINA)
            return
        }
        val trabajo = OneTimeWorkRequestBuilder<RecordatorioRutinaTrabajo>()
            .setInitialDelay(esperaHasta(minutosDelDia), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(DATO_MINUTOS to minutosDelDia))
            .build()
        trabajos.enqueueUniqueWork(TRABAJO_RUTINA, politica, trabajo)
    }

    // ======================================================================= SESIÓN

    /** KEEP: si ya estaba programada (la app se abrió otra vez), no se reinicia el conteo de 15 min. */
    override fun iniciarRevision(rol: Rol) {
        val trabajo = PeriodicWorkRequestBuilder<RevisarAvisosTrabajo>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        trabajos.enqueueUniquePeriodicWork(TRABAJO_REVISION, ExistingPeriodicWorkPolicy.KEEP, trabajo)
    }

    override fun detenerTodo() {
        trabajos.cancelUniqueWork(TRABAJO_REVISION)
        trabajos.cancelUniqueWork(TRABAJO_RUTINA)
        trabajos.cancelAllWorkByTag(ETIQUETA_CITAS)
        sistema.cancelAll()
    }

    // ======================================================================= HORAS

    /** «2026-11-05» + «10:00» en la hora del teléfono -> milisegundos; null si el texto no es válido. */
    private fun momentoLocal(fecha: String, hora: String): Long? =
        runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { isLenient = false }.parse("$fecha $hora")?.time }.getOrNull()

    /** Milisegundos que faltan para la próxima vez que el reloj marque [minutosDelDia] (hoy o mañana). */
    private fun esperaHasta(minutosDelDia: Int): Long {
        val ahora = Calendar.getInstance()
        val objetivo = (ahora.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, minutosDelDia / 60)
            set(Calendar.MINUTE, minutosDelDia % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Si ya pasó (o falta menos de un minuto), mañana a la misma hora.
            if (timeInMillis <= ahora.timeInMillis + 60_000L) add(Calendar.DAY_OF_YEAR, 1)
        }
        return objetivo.timeInMillis - ahora.timeInMillis
    }
}
