package com.terapid.physiogestion.plataforma

/**
 * VARIABLES DE ENTORNO de PhysioGestion.
 *
 * Reúne en un solo lugar los valores que cambian según dónde se ejecuta la
 * app: dirección del servidor, tiempos de espera y bandera de registro.
 * Ninguna otra clase escribe direcciones fijas.
 *
 *  - Lo que NO es secreto (URLs públicas, tiempos, banderas) se declara aquí,
 *    en código común, porque debe ser igual en Android, iOS y escritorio.
 *  - Lo que depende de cada computadora (la IP del backend para un celular
 *    físico) va en frontend/local.properties, que no se sube a GitHub, y
 *    Gradle lo entrega a la app como BuildConfig.SERVIDOR_URL.
 */
enum class TipoEntorno {
    DESARROLLO, // emulador o celular contra el backend local (puerto 8080)
    PRUEBAS,    // servidor de pruebas de la clínica
    PRODUCCION, // servidor definitivo
}

object Entorno {

    /** Una compilación de depuración trabaja contra el backend local. */
    val actual: TipoEntorno
        get() = if (Plataforma.esDepuracion) TipoEntorno.DESARROLLO else TipoEntorno.PRODUCCION

    /**
     * Dirección del backend según el ambiente. En desarrollo cada plataforma
     * aporta la suya (10.0.2.2 en el emulador de Android, localhost en
     * escritorio e iOS, o la de local.properties si se configuró).
     * Las de pruebas y producción se definirán al desplegar en Oracle Cloud.
     */
    val urlServidor: String
        get() = when (actual) {
            TipoEntorno.DESARROLLO -> urlServidorPorDefecto()
            TipoEntorno.PRUEBAS -> "https://pruebas.physiogestion.mx"
            TipoEntorno.PRODUCCION -> "https://api.physiogestion.mx"
        }

    /** Tiempo máximo para conectar. En desarrollo es corto para detectar rápido un backend apagado. */
    val tiempoConexionMs: Long
        get() = if (actual == TipoEntorno.DESARROLLO) 8_000 else 15_000

    /** Tiempo máximo de una petición completa. */
    val tiempoPeticionMs: Long
        get() = if (actual == TipoEntorno.DESARROLLO) 15_000 else 30_000

    /** El registro de peticiones (Logcat / consola) solo se activa fuera de producción. */
    val registroActivo: Boolean
        get() = actual != TipoEntorno.PRODUCCION

    /** Días que una rutina guardada en el teléfono se considera vigente sin volver a descargarla. */
    const val VIGENCIA_RUTINA_DIAS = 7
}
