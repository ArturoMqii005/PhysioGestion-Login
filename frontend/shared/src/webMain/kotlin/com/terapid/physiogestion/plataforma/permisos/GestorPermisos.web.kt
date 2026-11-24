package com.terapid.physiogestion.plataforma.permisos

/** Implementación WEB: la versión del navegador no usa cámara ni sensores, así que no pide permisos. */
actual class GestorPermisos actual constructor() {
    actual fun estaConcedido(permiso: Permiso): Boolean = true

    actual suspend fun solicitar(permiso: Permiso): Boolean = true
}
