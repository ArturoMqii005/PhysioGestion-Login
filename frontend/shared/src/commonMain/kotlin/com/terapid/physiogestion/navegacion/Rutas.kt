package com.terapid.physiogestion.navegacion

import com.terapid.physiogestion.modelo.Rol
import kotlinx.serialization.Serializable

/*
 * Rutas de la navegación (rutas tipadas de Navigation Compose).
 * Son @Serializable porque la librería convierte cada ruta en texto para
 * guardarla en el historial de pantallas.
 */

@Serializable
data object RutaLogin

@Serializable
data object RutaAdministrador

@Serializable
data object RutaFisioterapeuta

@Serializable
data object RutaRecepcion

@Serializable
data object RutaPaciente

/** Enrutamiento por rol: decide qué vista abre cada perfil al iniciar sesión. */
fun rutaInicialPara(rol: Rol): Any = when (rol) {
    Rol.ADMINISTRADOR -> RutaAdministrador
    Rol.FISIOTERAPEUTA -> RutaFisioterapeuta
    Rol.RECEPCIONISTA -> RutaRecepcion
    Rol.PACIENTE -> RutaPaciente
}
