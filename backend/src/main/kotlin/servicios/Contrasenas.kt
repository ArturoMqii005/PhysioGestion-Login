package com.terapid.physiogestion.servicios

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * CONTRASEÑAS (USR-13). El servidor guarda solo el hash BCrypt: aunque alguien
 * copiara la base de datos, no podría leer las contraseñas. BCrypt incluye una
 * sal aleatoria y es lento a propósito (costo 10 ≈ 1 024 rondas), lo que vuelve
 * muy caro probar contraseñas por fuerza bruta.
 */
object Contrasenas {
    private const val COSTO = 10
    private val aleatorio = SecureRandom()

    /** Hash para comparar cuando el usuario no existe: así la respuesta tarda lo mismo y no revela cuentas. */
    private val hashFicticio: String by lazy { hash("contrasena-que-nadie-usa-0") }

    fun hash(texto: String): String = BCrypt.withDefaults().hashToString(COSTO, texto.toCharArray())

    fun coincide(texto: String, hash: String?): Boolean =
        BCrypt.verifyer().verify(texto.toCharArray(), hash ?: hashFicticio).verified && hash != null

    /** Regla mínima de contraseña. Devuelve el problema, o null si la contraseña cumple. */
    fun problema(texto: String, usuario: String? = null): String? = when {
        texto.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
        texto.length > 64 -> "La contraseña puede tener máximo 64 caracteres."
        texto.none { it.isLetter() } || texto.none { it.isDigit() } -> "La contraseña debe combinar letras y números."
        texto.isBlank() || texto.trim() != texto -> "La contraseña no puede empezar ni terminar con espacios."
        usuario != null && usuario.length >= 4 && texto.contains(usuario, ignoreCase = true) ->
            "La contraseña no debe contener tu usuario."
        else -> null
    }

    /** Contraseña temporal de 12 caracteres para las cuentas que da de alta el administrador. */
    fun temporal(): String {
        val letras = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ"
        val numeros = "23456789"
        val todos = letras + numeros
        val base = (1..10).map { todos[aleatorio.nextInt(todos.length)] } +
            letras[aleatorio.nextInt(letras.length)] + numeros[aleatorio.nextInt(numeros.length)]
        return base.shuffled(java.util.Random(aleatorio.nextLong())).joinToString("")
    }

    /** Token de sesión: 32 bytes aleatorios en Base64 URL (más difícil de adivinar que un UUID). */
    fun token(): String {
        val bytes = ByteArray(32).also(aleatorio::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Código numérico de 6 dígitos para la verificación del correo y la recuperación. */
    fun codigo(): String = (aleatorio.nextInt(900_000) + 100_000).toString()

    /** Código de invitación legible, sin letras que se confundan (0/O, 1/I/L): «K7QM-4R2X». */
    fun codigoInvitacion(): String {
        val alfabeto = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        val c = (1..8).map { alfabeto[aleatorio.nextInt(alfabeto.length)] }.joinToString("")
        return c.take(4) + "-" + c.drop(4)
    }

    /** SHA-256 en hexadecimal: los códigos y tokens se guardan así, nunca tal cual. */
    fun sha256(texto: String): String =
        MessageDigest.getInstance("SHA-256").digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Comparación en tiempo constante, para no filtrar información por lo que tarda. */
    fun iguales(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
