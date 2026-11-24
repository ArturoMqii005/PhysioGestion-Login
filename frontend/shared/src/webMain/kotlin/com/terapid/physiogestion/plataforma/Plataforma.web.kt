package com.terapid.physiogestion.plataforma

/**
 * Implementación WEB. Se considera de depuración cuando la página se abre desde la
 * propia computadora (localhost): así usa el backend local. Publicada en Railway,
 * trabaja contra el servidor de la nube.
 */
actual object Plataforma {
    actual val esDepuracion: Boolean
        get() = dominioDeLaPagina().let { it == "localhost" || it == "127.0.0.1" }

    actual val nombre: String
        get() = "Web · ${nombreNavegador(agenteDelNavegador())}"

    actual val versionApp: String
        get() = "1.7.0"
}

/** "Mozilla/5.0 ... Chrome/142.0 Safari/537.36" -> "Chrome". */
private fun nombreNavegador(agente: String): String = when {
    "Edg/" in agente -> "Edge"
    "OPR/" in agente -> "Opera"
    "Firefox/" in agente -> "Firefox"
    "Chrome/" in agente -> "Chrome"
    "Safari/" in agente -> "Safari"
    else -> "navegador"
}
