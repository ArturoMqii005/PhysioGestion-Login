package com.terapid.physiogestion.datos.local

/**
 * ALMACENAMIENTO CIFRADO para el único dato que no puede quedar en texto
 * plano: el token, que autoriza las peticiones al servidor. Filtrarlo
 * equivale a entregar la cuenta del paciente.
 *
 * Android cifra con una llave AES guardada en el Android Keystore (la llave
 * nunca sale del almacén del sistema). iOS usará el Llavero (Keychain).
 */
expect class AlmacenSeguro() {
    fun guardarToken(token: String)
    fun leerToken(): String?
    fun borrarToken()
}
