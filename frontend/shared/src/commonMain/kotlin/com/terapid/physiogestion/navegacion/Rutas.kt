package com.terapid.physiogestion.navegacion

import com.terapid.physiogestion.modelo.Rol
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
