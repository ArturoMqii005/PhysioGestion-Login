package com.terapid.physiogestion.datos.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.terapid.physiogestion.plataforma.ContextoAndroid
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Implementación ANDROID del almacén cifrado.
 *
 * Se usa directamente el Android Keystore: una llave AES-256 que genera y
 * guarda el sistema (la app puede usarla, pero nunca leerla). El token se
 * cifra con AES/GCM y en SharedPreferences solo queda el texto cifrado.
 * (EncryptedSharedPreferences, que hacía esto mismo, quedó obsoleta en la
 * biblioteca androidx.security, por eso no se usa.)
 */
actual class AlmacenSeguro actual constructor() {

    private val preferencias: SharedPreferences by lazy {
        ContextoAndroid.aplicacion.getSharedPreferences("physiogestion_seguro", Context.MODE_PRIVATE)
    }

    actual fun guardarToken(token: String) {
        val cifrador = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, llave()) }
        val cifrado = cifrador.doFinal(token.encodeToByteArray())
        // Se guarda el vector de inicio (12 bytes) junto con el texto cifrado.
        val paquete = Base64.encodeToString(cifrador.iv + cifrado, Base64.NO_WRAP)
        preferencias.edit().putString(CLAVE_TOKEN, paquete).apply()
    }

    actual fun leerToken(): String? {
        val paquete = preferencias.getString(CLAVE_TOKEN, null) ?: return null
        return try {
            val bytes = Base64.decode(paquete, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, LARGO_IV)
            val cifrado = bytes.copyOfRange(LARGO_IV, bytes.size)
            val cifrador = Cipher.getInstance(TRANSFORMACION).apply {
                init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, iv))
            }
            cifrador.doFinal(cifrado).decodeToString()
        } catch (_: Exception) {
            // La llave ya no existe (p. ej. se restauró un respaldo en otro teléfono): se pide login otra vez.
            borrarToken()
            null
        }
    }

    actual fun borrarToken() {
        preferencias.edit().remove(CLAVE_TOKEN).apply()
    }

    /** Llave del Keystore; se crea la primera vez. */
    private fun llave(): SecretKey {
        val almacen = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (almacen.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "physiogestion_token"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val LARGO_IV = 12
        const val CLAVE_TOKEN = "token_acceso"
    }
}
