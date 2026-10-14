package com.terapid.physiogestion.plataforma.permisos

/**
 * HABILITACIÓN: permisos del sistema operativo.
 *
 * El acelerómetro y el giroscopio no piden permiso porque no revelan datos
 * personales. La cámara sí, y el contador de pasos también desde Android 10,
 * porque la actividad física se considera un dato de salud.
 */
enum class Permiso {
    /** Verificación visual del ejercicio (RFN-27). */
    CAMARA,

    /** Contador de pasos (RFN-39). */
    ACTIVIDAD_FISICA,
}

/**
 * Se declara con expect porque el mecanismo cambia por completo: Android usa
 * un contrato de resultado de la Activity, iOS resuelve el permiso en la
 * primera llamada a cada marco y en escritorio no hay permisos que pedir.
 */
expect class GestorPermisos() {

    /** true si el permiso ya está concedido. */
    fun estaConcedido(permiso: Permiso): Boolean

    /** Muestra el diálogo del sistema y suspende hasta que el usuario responde. true si lo concedió. */
    suspend fun solicitar(permiso: Permiso): Boolean
}
