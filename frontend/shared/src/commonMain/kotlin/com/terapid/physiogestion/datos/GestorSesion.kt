package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.PersistenciaSesion
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.Sesion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Guarda la sesión abierta (token + usuario). La navegación observa [sesion]:
 * al abrirse va a la vista del rol y al cerrarse regresa al inicio de sesión.
 *
 * Con [persistencia], la sesión también queda guardada en el teléfono (el
 * token cifrado), así la app abre directo en la vista del rol. Si el servidor
 * ya no reconoce el token, la primera petición responde 401 y se vuelve al login.
 */
class GestorSesion(private val persistencia: PersistenciaSesion? = null) {

    private val _sesion = MutableStateFlow(leerGuardada())
    val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()

    fun abrir(sesion: Sesion) {
        _sesion.value = sesion
        try {
            persistencia?.guardar(sesion)
        } catch (_: Exception) {
            // Si no se pudo guardar, la sesión sigue abierta mientras la app esté abierta.
        }
    }

    /** Mensaje para el login cuando la sesión se cerró sola (por ejemplo, el servidor ya no reconoce el token). */
    private var avisoDeCierre: String? = null

    fun cerrar(aviso: String? = null) {
        avisoDeCierre = aviso
        _sesion.value = null
        try {
            persistencia?.borrar()
        } catch (_: Exception) {
        }
    }

    /** Devuelve el aviso una sola vez (el login lo muestra y lo olvida). */
    fun tomarAviso(): String? = avisoDeCierre.also { avisoDeCierre = null }

    /** Token para el encabezado Authorization; si no hay sesión, se debe volver a iniciar. */
    fun token(): String = _sesion.value?.token ?: throw SesionVencida()

    /** Id del paciente de la sesión actual (solo el rol PACIENTE lo tiene). */
    fun pacienteId(): Int = _sesion.value?.usuario?.pacienteId ?: throw SesionVencida()

    private fun leerGuardada(): Sesion? = try {
        persistencia?.leer()
    } catch (_: Exception) {
        null
    }
}
