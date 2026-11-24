package com.terapid.physiogestion.datos.local

import com.terapid.physiogestion.plataforma.borrarDeSesion
import com.terapid.physiogestion.plataforma.guardarEnSesion
import com.terapid.physiogestion.plataforma.leerDeSesion

/**
 * Implementación WEB: el token va en sessionStorage, que el navegador borra al
 * cerrar la pestaña. En una computadora de la clínica que usan varias personas,
 * cerrar la pestaña cierra la sesión.
 */
actual class AlmacenSeguro actual constructor() {

    actual fun guardarToken(token: String) = guardarEnSesion(CLAVE_TOKEN, token)

    actual fun leerToken(): String? = leerDeSesion(CLAVE_TOKEN)

    actual fun borrarToken() = borrarDeSesion(CLAVE_TOKEN)

    private companion object {
        const val CLAVE_TOKEN = "physiogestion.token_acceso"
    }
}
