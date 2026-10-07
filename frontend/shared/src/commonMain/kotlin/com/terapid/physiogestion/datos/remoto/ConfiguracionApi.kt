package com.terapid.physiogestion.datos.remoto

import com.terapid.physiogestion.plataforma.urlServidorPorDefecto

/**
 * Dirección del backend Ktor (carpeta backend/ del repositorio).
 *
 * En la etapa local el servidor corre en la computadora, puerto 8080.
 * La versión va dentro de la ruta (/api/v1) para que la app instalada siga
 * funcionando cuando salga una v2 (RI-02).
 */
object ConfiguracionApi {
    const val VERSION = "v1"

    /** Se puede cambiar desde la pantalla de inicio de sesión (por ejemplo, para un celular físico). */
    var urlServidor: String = urlServidorPorDefecto()

    /** Arma la URL completa de un recurso: api("citas") -> http://10.0.2.2:8080/api/v1/citas */
    fun api(recurso: String): String = "${urlServidor.trim().trimEnd('/')}/api/$VERSION/$recurso"
}
