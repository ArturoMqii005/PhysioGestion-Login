package com.terapid.physiogestion.servicio

import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.Dificultad
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.RegistroSesion
import com.terapid.physiogestion.modelo.Rol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReglasLocalesTest {
    private fun cita(id: Int, hora: String, estado: EstadoCita = EstadoCita.CONFIRMADA, fecha: String = "2026-11-04") =
        Cita(id, hora, "Paciente $id", 2, "Ft. Carlos Ortega Luna", "Rodilla", estado, fecha, pacienteId = id)

    private fun sesion(dia: String, cumplimiento: Int, antes: Int, despues: Int) = RegistroSesion(
        rutinaId = 1, inicioMillis = 0, finMillis = 0, claveDia = dia, ejerciciosRealizados = listOf(1),
        ejerciciosAsignados = 1, cumplimiento = cumplimiento, dolorAntes = antes, dolorDespues = despues,
        dificultad = Dificultad.MEDIA, observacion = "", puntos = 10, alertaDolor = false,
    )

    @Test
    fun `agenda del dia cuenta solo las citas abiertas de hoy`() {
        val citas = listOf(
            cita(1, "12:00", EstadoCita.PENDIENTE), cita(2, "09:00"), cita(3, "17:00"),
            cita(4, "08:00", EstadoCita.CANCELADA), cita(5, "10:00", fecha = "2026-11-05"),
        )
        val fisio = ReglasLocales.agendaDelDia(citas, Rol.FISIOTERAPEUTA, "2026-11-04")!!
        assertEquals("Hoy tienes 3 citas", fisio.titulo)
        assertEquals("La primera es a las 09:00 con Paciente 2; la última, a las 17:00. 1 falta por confirmar.", fisio.texto)
        assertEquals("Hoy hay 3 citas en la clínica", ReglasLocales.agendaDelDia(citas, Rol.RECEPCIONISTA, "2026-11-04")!!.titulo)
        assertNull(ReglasLocales.agendaDelDia(citas, Rol.FISIOTERAPEUTA, "2026-11-06"))
    }

    @Test
    fun `la racha solo se avisa si esta en riesgo`() {
        assertNull(ReglasLocales.rachaEnRiesgo(0, false))
        assertNull(ReglasLocales.rachaEnRiesgo(5, true))
        assertEquals("¡No pierdas tu racha de 5 días!", ReglasLocales.rachaEnRiesgo(5, false)!!.titulo)
    }

    @Test
    fun `resumen semanal con dias, cumplimiento y dolor`() {
        val sesiones = listOf(
            sesion("2026-11-04", 100, 6, 4), sesion("2026-11-03", 80, 6, 4),
            sesion("2026-11-03", 90, 5, 3), sesion("2026-10-20", 10, 9, 9), // esta ya no cuenta
        )
        val r = ReglasLocales.resumenSemanal(sesiones, "2026-11-04")
        assertEquals("Tu semana: 2 días de rutina", r.titulo)
        assertEquals("Hiciste 90 % de tus ejercicios y, en promedio, tu dolor bajó de 5.7 a 3.7 al entrenar. ¡Sigue así!", r.texto)
        assertTrue(ReglasLocales.resumenSemanal(emptyList(), "2026-11-04").texto.startsWith("Esta semana no registraste"))
    }
}
