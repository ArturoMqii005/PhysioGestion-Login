package com.terapid.physiogestion.servicio

/**
 * Mismas reglas que valida el servidor (AutenticacionServicio), revisadas en la
 * app para avisar al momento, sin esperar la respuesta. El servidor las vuelve a
 * validar: la app nunca es la única barrera.
 */
object ReglasCuenta {
    private val patronUsuario = Regex("^[a-z0-9._]{4,30}$")
    private val patronCorreo = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
    private val patronInvitacion = Regex("^[A-Z0-9]{4}-?[A-Z0-9]{4}$")

    /** Problema de la contraseña, o null si cumple. */
    fun problemaContrasena(texto: String, usuario: String? = null): String? = when {
        texto.length < 8 -> "Usa al menos 8 caracteres."
        texto.length > 64 -> "Usa máximo 64 caracteres."
        texto.none { it.isLetter() } || texto.none { it.isDigit() } -> "Combina letras y números."
        texto.trim() != texto -> "No empieces ni termines con espacios."
        usuario != null && usuario.length >= 4 && texto.contains(usuario, ignoreCase = true) -> "No incluyas tu usuario en la contraseña."
        else -> null
    }

    /** Qué tan segura se ve la contraseña, de 0 a 4, para la barra de la pantalla. */
    fun fuerza(texto: String): Int {
        if (texto.isEmpty()) return 0
        var puntos = 0
        if (texto.length >= 8) puntos++
        if (texto.length >= 12) puntos++
        if (texto.any { it.isLetter() } && texto.any { it.isDigit() }) puntos++
        if (texto.any { it.isUpperCase() } && texto.any { it.isLowerCase() } || texto.any { !it.isLetterOrDigit() }) puntos++
        return puntos.coerceAtMost(4)
    }

    fun problemaUsuario(usuario: String): String? =
        if (patronUsuario.matches(usuario)) null else "De 4 a 30 caracteres: minúsculas, números, punto o guion bajo."

    fun problemaCorreo(correo: String): String? =
        if (correo.length <= 120 && patronCorreo.matches(correo)) null else "Escribe un correo válido, por ejemplo nombre@gmail.com."

    fun problemaInvitacion(codigo: String): String? =
        if (patronInvitacion.matches(codigo.trim().uppercase())) null else "El código tiene 8 caracteres, por ejemplo K7QM-4R2X."

    /** Solo dígitos, máximo 6 (el código que llega por correo). */
    fun limpiarCodigo(texto: String): String = texto.filter { it.isDigit() }.take(6)

    /** «k7qm4r2x» → «K7QM-4R2X» mientras el paciente escribe. */
    fun formatearInvitacion(texto: String): String {
        val limpio = texto.uppercase().filter { it.isLetterOrDigit() }.take(8)
        return if (limpio.length > 4) limpio.take(4) + "-" + limpio.drop(4) else limpio
    }

    /** Usuario sugerido a partir del correo: «Maria.Lopez@gmail.com» → «maria.lopez». */
    fun usuarioSugerido(correo: String): String =
        correo.substringBefore('@').lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(30)
}
