package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.ClinicaApi
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.Usuario

/** Datos del personal de la clínica (usuarios y agenda) usando el token de la sesión. */
class ClinicaRepositorio(
    private val api: ClinicaApi,
    private val gestor: GestorSesion,
) {
    suspend fun usuarios(): List<Usuario> = conSesion { api.usuarios(it) }

    suspend fun citas(): List<Cita> = conSesion { api.citas(it) }

    /** Si el servidor dice que el token ya no vale, cierra la sesión (la app vuelve al login). */
    private suspend fun <T> conSesion(llamada: suspend (String) -> T): T =
        try {
            llamada(gestor.token())
        } catch (e: SesionVencida) {
            gestor.cerrar()
            throw e
        }
}
