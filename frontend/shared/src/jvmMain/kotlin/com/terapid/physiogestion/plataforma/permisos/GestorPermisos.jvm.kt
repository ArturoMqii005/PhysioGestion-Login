package com.terapid.physiogestion.plataforma.permisos

/** Implementación ESCRITORIO: el sistema operativo de una computadora no pide estos permisos. */
actual class GestorPermisos actual constructor() {
    actual fun estaConcedido(permiso: Permiso): Boolean = true

    actual suspend fun solicitar(permiso: Permiso): Boolean = true
}
