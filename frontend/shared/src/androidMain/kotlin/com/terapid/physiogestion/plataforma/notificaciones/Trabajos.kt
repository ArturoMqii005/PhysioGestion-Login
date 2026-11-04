package com.terapid.physiogestion.plataforma.notificaciones

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.dependencias.Dependencias
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.servicio.ReglasAvisos
import kotlinx.coroutines.CancellationException

/*
 * TRABAJOS EN SEGUNDO PLANO (WorkManager). Android los ejecuta aunque la app
 * esté cerrada y los conserva si el teléfono se reinicia. Cada uno abre la
 * app «por dentro» (PhysioGestionApp prepara el Context y el Notificador) y
 * usa los mismos repositorios que las pantallas.
 */

/** El notificador de Android que registró PhysioGestionApp (o null en pruebas). */
private val notificadorAndroid: NotificadorAndroid? get() = Dependencias.notificador as? NotificadorAndroid

/**
 * Cada 15 min con internet: avisos nuevos (NOT-03), sesiones sin enviar (NOT-04)
 * y recordatorios de las próximas citas (NOT-02). La lógica está en
 * RevisorSegundoPlano (código común, con pruebas).
 */
class RevisarAvisosTrabajo(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        return try {
            val resultado = Dependencias.revisor.revisar()
            if (!resultado.sesionAbierta) Dependencias.notificador.detenerTodo()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: SesionVencida) {
            // El servidor ya no reconoce el token: se deja de revisar hasta el próximo inicio de sesión.
            Dependencias.notificador.detenerTodo()
            Result.success()
        } catch (_: Exception) {
            // Sin conexión o servidor dormido: la siguiente vuelta (15 min) lo intenta otra vez.
            Result.success()
        }
    }
}

/** Recordatorio de una cita, programado para un día antes o una hora antes (NOT-02). */
class RecordatorioTrabajo(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val titulo = inputData.getString(NotificadorAndroid.DATO_TITULO) ?: return Result.success()
        val texto = inputData.getString(NotificadorAndroid.DATO_TEXTO).orEmpty()
        val id = inputData.getInt(NotificadorAndroid.DATO_ID, NotificadorAndroid.ID_RUTINA + 1)
        // Si mientras tanto se cerró la sesión, ya no se recuerda nada.
        if (Dependencias.autenticacion.sesion.value?.usuario?.rol != Rol.PACIENTE) return Result.success()
        notificadorAndroid?.mostrar(id, NotificadorAndroid.CANAL_RECORDATORIOS, titulo, texto)
        return Result.success()
    }
}

/**
 * Recordatorio diario de la rutina (NOT-01). Si hoy ya registró su sesión no
 * molesta al paciente. Al terminar se programa para el día siguiente.
 */
class RecordatorioRutinaTrabajo(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val minutos = inputData.getInt(NotificadorAndroid.DATO_MINUTOS, -1)
        val usuario = Dependencias.autenticacion.sesion.value?.usuario ?: return Result.success()
        val pacienteId = usuario.pacienteId ?: return Result.success()
        val preferencias = Dependencias.preferencias
        // Si el paciente cambió la hora o lo apagó, este trabajo ya no vale.
        if (usuario.rol != Rol.PACIENTE || preferencias.recordatorioRutina(pacienteId) != minutos) return Result.success()
        val notificador = notificadorAndroid ?: return Result.success()
        if (!preferencias.yaEntrenoHoy(pacienteId)) {
            val (titulo, texto) = ReglasAvisos.TEXTO_RUTINA
            notificador.mostrar(NotificadorAndroid.ID_RUTINA, NotificadorAndroid.CANAL_RECORDATORIOS, titulo, texto)
        }
        notificador.programarRutina(minutos, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
