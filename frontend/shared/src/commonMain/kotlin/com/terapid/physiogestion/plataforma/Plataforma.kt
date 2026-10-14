package com.terapid.physiogestion.plataforma

/**
 * Datos que solo el sistema operativo puede responder. Cada plataforma los
 * obtiene de una fuente distinta: BuildConfig en Android, Info.plist en iOS
 * y las propiedades de Java en escritorio.
 */
expect object Plataforma {
    /** true en una compilación de depuración (la que instala Android Studio con Run). */
    val esDepuracion: Boolean

    /** Nombre legible del sistema, por ejemplo "Android 17 (API 37)". */
    val nombre: String

    /** Versión instalada de la app, por ejemplo "1.1". */
    val versionApp: String
}
