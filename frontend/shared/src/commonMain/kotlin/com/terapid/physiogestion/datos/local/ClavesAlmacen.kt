package com.terapid.physiogestion.datos.local

/**
 * Claves de [AlmacenPreferencias]. Al ser constantes, el compilador detecta
 * si se escribe con una clave y se lee con otra ligeramente distinta.
 */
object ClavesAlmacen {

    // ---- Sesión (el token NO va aquí: va cifrado en AlmacenSeguro) ----
    const val SESION_USUARIO = "sesion_usuario"
    /** Lo que falta antes de abrir la vista del rol: «AVISO», «CONTRASENA» (separados por coma). */
    const val SESION_PENDIENTES = "sesion_pendientes"

    // ---- Conexión ----
    /** Última dirección del servidor con la que se inició sesión correctamente. */
    const val URL_SERVIDOR = "url_servidor"

    // ---- Configuración ----
    const val VERIFICACION_CAMARA_ACTIVA = "verificacion_camara_activa"

    // ---- Control de sincronización ----
    const val FECHA_ULTIMA_SINCRONIZACION = "fecha_ultima_sincronizacion"

    // ---- Progreso del tratamiento (uno por paciente, por si el teléfono se comparte) ----
    fun rachaDias(pacienteId: Int) = "racha_dias_$pacienteId"
    fun ultimoDiaActivo(pacienteId: Int) = "ultimo_dia_activo_$pacienteId"

    // ---- Avisos y recordatorios (NOT-01..04) ----
    /** Id del aviso más reciente que este teléfono ya mostró a ese usuario (para no repetir notificaciones). */
    fun ultimoAvisoVisto(usuarioId: Int) = "ultimo_aviso_visto_$usuarioId"
    /** Minutos del día del recordatorio de la rutina (18:30 = 1110); si no existe, está apagado. */
    fun recordatorioRutina(pacienteId: Int) = "recordatorio_rutina_$pacienteId"
    /** Día en que ya se avisó de sesiones sin enviar (un aviso por día como máximo). */
    const val DIA_AVISO_PENDIENTES = "dia_aviso_pendientes"

    // ---- Contador de pasos: lectura del sensor al empezar el día ----
    const val PASOS_DIA_BASE = "pasos_dia_base"
    const val PASOS_VALOR_BASE = "pasos_valor_base"
}
