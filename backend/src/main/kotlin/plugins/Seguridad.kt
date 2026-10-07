package com.terapid.physiogestion.plugins

import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.UsuarioPublico
import com.terapid.physiogestion.servicios.AutenticacionServicio
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal

/** Nombre del proveedor de autenticación que usan las rutas protegidas. */
const val AUTH_SESION = "sesion"

/**
 * Authentication (Bearer): las rutas dentro de authenticate(AUTH_SESION)
 * exigen el encabezado "Authorization: Bearer <token>" que entrega el login.
 */
fun Application.configureSeguridad(autenticacion: AutenticacionServicio) {
    install(Authentication) {
        bearer(AUTH_SESION) {
            realm = "PhysioGestion"
            authenticate { credencial -> autenticacion.usuarioDeToken(credencial.token) }
        }
    }
}

/** Usuario dueño del token de la petición actual. */
fun ApplicationCall.usuarioActual(): UsuarioPublico =
    principal<UsuarioPublico>() ?: throw AccesoDenegado("Inicia sesión para continuar.")

/** Detiene la petición con 403 si el rol del usuario no está en la lista. */
fun ApplicationCall.exigirRol(vararg permitidos: Rol): UsuarioPublico {
    val usuario = usuarioActual()
    if (usuario.rol !in permitidos) throw AccesoDenegado()
    return usuario
}
