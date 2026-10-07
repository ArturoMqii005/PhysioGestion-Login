package com.terapid.physiogestion.vista.componentes

import androidx.compose.ui.graphics.Color
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** Nombre de cada rol tal como se muestra en pantalla. */
val Rol.etiqueta: String
    get() = when (this) {
        Rol.ADMINISTRADOR -> "Administrador"
        Rol.FISIOTERAPEUTA -> "Fisioterapeuta"
        Rol.RECEPCIONISTA -> "Recepción"
        Rol.PACIENTE -> "Paciente"
    }

val Rol.color: Color
    get() = when (this) {
        Rol.ADMINISTRADOR -> ColoresPhysio.Violeta
        Rol.FISIOTERAPEUTA -> ColoresPhysio.Turquesa
        Rol.RECEPCIONISTA -> ColoresPhysio.Pendiente
        Rol.PACIENTE -> ColoresPhysio.AzulClaro
    }

/** Estado de la cita con texto y color: nunca depende solo del color (RC-25). */
val EstadoCita.etiqueta: String
    get() = when (this) {
        EstadoCita.PENDIENTE -> "Pendiente"
        EstadoCita.CONFIRMADA -> "Confirmada"
        EstadoCita.ATENDIDA -> "Atendida"
        EstadoCita.CANCELADA -> "Cancelada"
    }

val EstadoCita.color: Color
    get() = when (this) {
        EstadoCita.PENDIENTE -> ColoresPhysio.Pendiente
        EstadoCita.CONFIRMADA -> ColoresPhysio.AzulClaro
        EstadoCita.ATENDIDA -> ColoresPhysio.Exito
        EstadoCita.CANCELADA -> ColoresPhysio.Alerta
    }
