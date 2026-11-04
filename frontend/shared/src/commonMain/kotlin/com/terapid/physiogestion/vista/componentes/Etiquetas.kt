package com.terapid.physiogestion.vista.componentes

import androidx.compose.ui.graphics.Color
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.TipoAviso
import com.terapid.physiogestion.modelo.Semaforo
import com.terapid.physiogestion.servicio.movimiento.RangoArticular
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
        EstadoCita.NO_ASISTIO -> "No asistió"
    }

/** Para las tarjetas de conteo: «Pendientes», «No asistieron». */
val EstadoCita.plural: String
    get() = when (this) {
        EstadoCita.PENDIENTE -> "Pendientes"
        EstadoCita.CONFIRMADA -> "Confirmadas"
        EstadoCita.ATENDIDA -> "Atendidas"
        EstadoCita.CANCELADA -> "Canceladas"
        EstadoCita.NO_ASISTIO -> "No asistieron"
    }

val EstadoCita.color: Color
    get() = when (this) {
        EstadoCita.PENDIENTE -> ColoresPhysio.Pendiente
        EstadoCita.CONFIRMADA -> ColoresPhysio.AzulClaro
        EstadoCita.ATENDIDA -> ColoresPhysio.Exito
        EstadoCita.CANCELADA -> ColoresPhysio.Alerta
        EstadoCita.NO_ASISTIO -> ColoresPhysio.Violeta
    }

/** Semáforo con texto: el color nunca va solo (RC-25). */
val Semaforo.etiqueta: String
    get() = when (this) {
        Semaforo.VERDE -> "Verde · cumple"
        Semaforo.AMARILLO -> "Amarillo · cumplimiento bajo"
        Semaforo.ROJO -> "Rojo · dolor alto"
    }

val Semaforo.color: Color
    get() = when (this) {
        Semaforo.VERDE -> ColoresPhysio.Exito
        Semaforo.AMARILLO -> ColoresPhysio.Pendiente
        Semaforo.ROJO -> ColoresPhysio.Alerta
    }

val RangoArticular.color: Color
    get() = when (this) {
        RangoArticular.INSUFICIENTE -> ColoresPhysio.Alerta
        RangoArticular.ACEPTABLE -> ColoresPhysio.Pendiente
        RangoArticular.OBJETIVO_ALCANZADO -> ColoresPhysio.Exito
        RangoArticular.EXCEDIDO -> ColoresPhysio.Violeta
    }

/** Tipo de aviso con texto y color (RC-25). */
val TipoAviso.etiqueta: String
    get() = when (this) {
        TipoAviso.DOLOR_ALTO -> "Dolor alto"
        TipoAviso.CITA_NUEVA -> "Cita nueva"
        TipoAviso.CITA_REPROGRAMADA -> "Cita reprogramada"
        TipoAviso.CITA_CANCELADA -> "Cita cancelada"
        TipoAviso.RUTINA_ACTUALIZADA -> "Rutina"
        TipoAviso.PACIENTE_ASIGNADO -> "Paciente nuevo"
    }

val TipoAviso.color: Color
    get() = when (this) {
        TipoAviso.DOLOR_ALTO -> ColoresPhysio.Alerta
        TipoAviso.CITA_NUEVA -> ColoresPhysio.Turquesa
        TipoAviso.CITA_REPROGRAMADA -> ColoresPhysio.Pendiente
        TipoAviso.CITA_CANCELADA -> ColoresPhysio.TextoSecundario
        TipoAviso.RUTINA_ACTUALIZADA -> ColoresPhysio.AzulClaro
        TipoAviso.PACIENTE_ASIGNADO -> ColoresPhysio.Violeta
    }
