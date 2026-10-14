package com.terapid.physiogestion.plataforma

/**
 * Implementación ESCRITORIO. La app de escritorio se usa para desarrollo, así
 * que es de depuración salvo que se ejecute con -Dphysiogestion.produccion=true.
 */
actual object Plataforma {
    actual val esDepuracion: Boolean
        get() = System.getProperty("physiogestion.produccion") != "true"

    actual val nombre: String
        get() = "Escritorio · ${System.getProperty("os.name")} · Java ${System.getProperty("java.version")}"

    actual val versionApp: String
        get() = "1.1.0"
}
