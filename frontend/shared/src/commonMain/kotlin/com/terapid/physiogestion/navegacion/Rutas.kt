package com.terapid.physiogestion.navegacion

import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Sesion
import kotlinx.serialization.Serializable

/*
 * Rutas de la navegación (rutas tipadas de Navigation Compose).
 * Son @Serializable porque la librería convierte cada ruta en texto para
 * guardarla en el historial de pantallas. Las que llevan datos son
 * data class: el argumento viaja dentro de la ruta y el compilador revisa su tipo.
 */

@Serializable
data object RutaLogin

@Serializable
data object RutaAdministrador

@Serializable
data object RutaFisioterapeuta

@Serializable
data object RutaRecepcion

/** Panel principal del paciente: desde aquí se ramifica su navegación. */
@Serializable
data object RutaPaciente

/** Rutina del día (RFN-20). */
@Serializable
data object RutaRutina

/** Medición de un ejercicio con los sensores del teléfono (RFN-26). */
@Serializable
data class RutaEjercicio(val idEjercicio: Int)

/** Prueba de escaleras con el barómetro: avance hacia la meta funcional. */
@Serializable
data object RutaEscaleras

/** Historial de sesiones (RFN-31). */
@Serializable
data object RutaHistorial

/** Detalle de una sesión, donde se puede corregir o eliminar. */
@Serializable
data class RutaDetalleSesion(val idSesion: Int)

/** Perfil: datos de la cuenta, almacenamiento y permisos. */
@Serializable
data object RutaPerfil

/** Enrutamiento por rol: decide qué vista abre cada perfil al iniciar sesión. */
fun rutaInicialPara(rol: Rol): Any = when (rol) {
    Rol.ADMINISTRADOR -> RutaAdministrador
    Rol.FISIOTERAPEUTA -> RutaFisioterapeuta
    Rol.RECEPCIONISTA -> RutaRecepcion
    Rol.PACIENTE -> RutaPaciente
}

// ---------------- Cuentas (antes de iniciar sesión o antes de abrir la vista del rol) ----------------

/** Registro del paciente con el código de invitación (USR-01). */
@Serializable
data object RutaRegistro

/** Confirmar el correo con el código de 6 dígitos (USR-20). */
@Serializable
data class RutaVerificarCorreo(val correo: String)

/** «¿Olvidaste tu contraseña?»: pedir el código y restablecer (USR-10, USR-11). */
@Serializable
data object RutaRecuperarContrasena

/** Aviso de privacidad que el paciente acepta antes de usar la app (USR-21). */
@Serializable
data object RutaAvisoPrivacidad

/** Cambio de contraseña: desde el perfil, u obligatorio si la cuenta trae una temporal (USR-02, USR-12). */
@Serializable
data class RutaCambiarContrasena(val obligatorio: Boolean)

/**
 * A dónde lleva una sesión recién abierta: primero lo que falta (aviso de
 * privacidad o contraseña temporal) y después la vista de su rol.
 */
fun rutaPara(sesion: Sesion): Any = when {
    sesion.requiereConsentimiento -> RutaAvisoPrivacidad
    sesion.debeCambiarContrasena -> RutaCambiarContrasena(obligatorio = true)
    else -> rutaInicialPara(sesion.usuario.rol)
}
