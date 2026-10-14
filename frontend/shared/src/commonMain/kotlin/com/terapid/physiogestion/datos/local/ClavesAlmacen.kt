package com.terapid.physiogestion.datos.local

/**
 * Claves de [AlmacenPreferencias]. Al ser constantes, el compilador detecta
 * si se escribe con una clave y se lee con otra ligeramente distinta.
 */
object ClavesAlmacen {

    // ---- Sesión (el token NO va aquí: va cifrado en AlmacenSeguro) ----
    const val SESION_USUARIO = "sesion_usuario"

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

    // ---- Contador de pasos: lectura del sensor al empezar el día ----
    const val PASOS_DIA_BASE = "pasos_dia_base"
    const val PASOS_VALOR_BASE = "pasos_valor_base"
}
