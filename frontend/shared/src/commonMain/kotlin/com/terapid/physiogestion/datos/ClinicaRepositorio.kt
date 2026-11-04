package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.remoto.ClinicaApi
import com.terapid.physiogestion.datos.remoto.SesionVencida
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.CitaSolicitud
import com.terapid.physiogestion.modelo.Ejercicio
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.FichaPaciente
import com.terapid.physiogestion.modelo.FisioterapeutaResumen
import com.terapid.physiogestion.modelo.PacienteResumen
import com.terapid.physiogestion.modelo.ReprogramarCitaSolicitud
import com.terapid.physiogestion.modelo.Rutina
import com.terapid.physiogestion.modelo.RutinaSolicitud
import com.terapid.physiogestion.modelo.PruebaEscalerasRegistrada
import com.terapid.physiogestion.modelo.SesionTerapia
import com.terapid.physiogestion.modelo.Usuario

/** Datos del personal de la clínica (usuarios y agenda) usando el token de la sesión. */
class ClinicaRepositorio(
    private val api: ClinicaApi,
    private val gestor: GestorSesion,
) {
    suspend fun usuarios(): List<Usuario> = conSesion { api.usuarios(it) }

    suspend fun citas(fecha: String? = null): List<Cita> = conSesion { api.citas(it, fecha) }

    suspend fun horasLibres(fisioterapeutaId: Int, fecha: String, duracion: Int, excepto: Int? = null): List<String> =
        conSesion { api.horasLibres(it, fisioterapeutaId, fecha, duracion, excepto) }

    suspend fun agendar(s: CitaSolicitud): Cita = conSesion { api.agendar(it, s) }

    suspend fun reprogramar(id: Int, s: ReprogramarCitaSolicitud): Cita = conSesion { api.reprogramar(it, id, s) }

    suspend fun cambiarEstadoCita(id: Int, estado: EstadoCita, nota: String? = null): Cita =
        conSesion { api.cambiarEstadoCita(it, id, estado, nota) }

    suspend fun fisioterapeutas(): List<FisioterapeutaResumen> = conSesion { api.fisioterapeutas(it) }

    suspend fun pacientes(): List<PacienteResumen> = conSesion { api.pacientes(it) }

    suspend fun ficha(pacienteId: Int): FichaPaciente = conSesion { api.ficha(it, pacienteId) }

    suspend fun asignar(pacienteId: Int, fisioterapeutaId: Int): PacienteResumen = conSesion { api.asignar(it, pacienteId, fisioterapeutaId) }

    suspend fun catalogo(): List<Ejercicio> = conSesion { api.catalogo(it) }

    suspend fun guardarRutina(pacienteId: Int, s: RutinaSolicitud): Rutina = conSesion { api.guardarRutina(it, pacienteId, s) }

    /** Seguimiento remoto: sesiones que los pacientes del fisioterapeuta registraron en casa. */
    suspend fun sesionesDeMisPacientes(): List<SesionTerapia> = conSesion { api.sesionesDeMisPacientes(it) }

    /** Pruebas de escaleras de los pacientes del fisioterapeuta (la más reciente primero). */
    suspend fun pruebasEscalerasDeMisPacientes(): List<PruebaEscalerasRegistrada> = conSesion { api.pruebasEscaleras(it) }

    /** Si el servidor dice que el token ya no vale, cierra la sesión (la app vuelve al login). */
    private suspend fun <T> conSesion(llamada: suspend (String) -> T): T =
        try {
            llamada(gestor.token())
        } catch (e: SesionVencida) {
            gestor.cerrar(e.message)
            throw e
        }
}
