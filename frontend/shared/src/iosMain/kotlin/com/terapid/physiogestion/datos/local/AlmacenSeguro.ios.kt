package com.terapid.physiogestion.datos.local

import platform.Foundation.NSUserDefaults

/**
 * Implementación iOS del almacén cifrado.
 *
 * El equivalente del Android Keystore en iOS es el Llavero (Keychain), cifrado
 * por hardware y respaldado por el Secure Enclave; su interfaz es de C
 * (SecItemAdd, SecItemCopyMatching, SecItemDelete). Esta versión deja la
 * estructura preparada y por ahora usa NSUserDefaults, para que el proyecto
 * compile mientras se incorpora la interoperabilidad con el Llavero.
 */
actual class AlmacenSeguro actual constructor() {

    private val preferencias = NSUserDefaults.standardUserDefaults

    actual fun guardarToken(token: String) {
        preferencias.setObject(token, CLAVE_TOKEN)
    }

    actual fun leerToken(): String? = preferencias.stringForKey(CLAVE_TOKEN)

    actual fun borrarToken() {
        preferencias.removeObjectForKey(CLAVE_TOKEN)
    }

    private companion object {
        const val CLAVE_TOKEN = "physiogestion.token_acceso"
    }
}
