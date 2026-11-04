package com.terapid.physiogestion.vista

import com.terapid.physiogestion.viewmodel.ResumenAvisosViewModel
import com.terapid.physiogestion.vista.componentes.TarjetaActivarNotificaciones
import com.terapid.physiogestion.vista.componentes.TarjetaAvisosSinLeer
import com.terapid.physiogestion.vista.componentes.VigilarAvisos
import com.terapid.physiogestion.vista.componentes.notificacionesOVacio
import com.terapid.physiogestion.vista.componentes.resumenOVacio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import com.terapid.physiogestion.vista.componentes.AccionCita
import com.terapid.physiogestion.vista.componentes.MensajeInfo
import com.terapid.physiogestion.vista.componentes.SelectorDia
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.PruebaEscalerasRegistrada
import com.terapid.physiogestion.modelo.SesionTerapia
import com.terapid.physiogestion.modelo.fechaIsoLegible
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.formatearFechaHora
import com.terapid.physiogestion.viewmodel.AgendaViewModel
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.EncabezadoRol
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.TarjetaCita
import com.terapid.physiogestion.vista.componentes.TarjetaDato
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** VISTA del rol FISIOTERAPEUTA: sus citas del día, con la siguiente resaltada. */
@Composable
fun FisioterapeutaPantalla(
    usuario: Usuario,
    viewModel: AgendaViewModel,
    alCerrarSesion: () -> Unit,
    alCambiarContrasena: (() -> Unit)? = null,
    alAbrirPacientes: () -> Unit = {},
    avisos: ResumenAvisosViewModel? = null,
    alAbrirAvisos: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val resumenAvisos by avisos.resumenOVacio.collectAsStateWithLifecycle()
    val notificaciones by avisos.notificacionesOVacio.collectAsStateWithLifecycle()
    if (avisos != null) VigilarAvisos(avisos)
    val hoy = formatearFechaHora(ahoraEnMilisegundos()).substringBefore(" ")
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { EncabezadoRol("Mi agenda", usuario, alCerrarSesion, alCambiarContrasena, resumenAvisos.sinLeer, alAbrirAvisos.takeIf { avisos != null }) }
            if (avisos != null) {
                item { TarjetaAvisosSinLeer(resumenAvisos, alAbrirAvisos) }
                item { TarjetaActivarNotificaciones(notificaciones, "Te avisamos en cuanto un paciente termine su sesión con dolor alto, aunque tengas la app cerrada.", avisos::activarNotificaciones) }
            }
            item {
                OutlinedButton(onClick = alAbrirPacientes, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Mis pacientes · fichas y rutinas")
                }
            }
            item {
                SelectorDia(estado.etiquetaDia, { viewModel.cambiarDia(-1) }, { viewModel.cambiarDia(1) }, if (estado.fecha != estado.hoy) viewModel::irAHoy else null)
            }
            estado.aviso?.let { item { MensajeInfo(it) } }

            when {
                estado.cargando -> item { Cargando("Cargando tu agenda…") }
                estado.error != null -> item { ErrorConReintento(estado.error ?: "", viewModel::cargar) }
                else -> {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TarjetaDato(estado.citas.size.toString(), if (estado.fecha == estado.hoy) "Citas hoy · $hoy" else "Citas · ${estado.etiquetaDia}", ColoresPhysio.Turquesa, Modifier.weight(1f))
                            TarjetaDato(
                                estado.cuantas(EstadoCita.ATENDIDA).toString(),
                                "Atendidas",
                                ColoresPhysio.Exito,
                                Modifier.weight(1f),
                            )
                        }
                    }
                    if (estado.citas.isEmpty()) {
                        item { Text("No tienes citas registradas este día.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    val siguiente = estado.siguiente
                    items(estado.citas, key = { it.id }) { cita ->
                        val acciones: (@Composable RowScope.() -> Unit)? = if (estado.puedeMarcarAsistencia) {
                            {
                                AccionCita("Atendida", { viewModel.marcarAsistencia(cita, llego = true) }, estado.procesando != cita.id)
                                AccionCita("No asistió", { viewModel.marcarAsistencia(cita, llego = false) }, estado.procesando != cita.id)
                            }
                        } else {
                            null
                        }
                        TarjetaCita(cita, mostrarFisioterapeuta = false, resaltada = cita.id == siguiente?.id, acciones = acciones)
                    }

                    // Seguimiento remoto: lo que los pacientes registraron en casa (GET /sesiones).
                    item {
                        Text(
                            "Seguimiento remoto de mis pacientes",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    when {
                        estado.errorSeguimiento != null -> item {
                            Text(estado.errorSeguimiento ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        estado.seguimiento.isEmpty() -> item {
                            Text("Tus pacientes todavía no envían sesiones desde casa.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> items(estado.seguimiento, key = { "sesion-${it.id}" }) { TarjetaSeguimiento(it) }
                    }

                    // Prueba de escaleras (barómetro): avance hacia la meta funcional.
                    if (estado.escaleras.isNotEmpty()) {
                        item {
                            Text(
                                "Pruebas de escaleras",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(estado.escaleras.take(6), key = { "escaleras-${it.id}" }) { TarjetaEscaleras(it) }
                    }
                }
            }
        }
    }
}

/** Una prueba de escaleras: cuánto subió el paciente y a qué ritmo. */
@Composable
private fun TarjetaEscaleras(prueba: PruebaEscalerasRegistrada) {
    TarjetaPhysio(borde = ColoresPhysio.AzulClaro) {
        Text(prueba.paciente.ifBlank { "Paciente ${prueba.pacienteId}" }, style = MaterialTheme.typography.titleSmall)
        val decimas = (prueba.metrosPorMinuto * 10).toInt()
        Text(
            "${fechaIsoLegible(prueba.fecha)} · ${prueba.pisos} ${if (prueba.pisos == 1) "piso" else "pisos"} " +
                "(${prueba.alturaSubidaCm / 100}.${(prueba.alturaSubidaCm % 100) / 10} m) en ${prueba.duracionSegundos} s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Etiqueta("Ritmo: ${decimas / 10}.${decimas % 10} m/min", ColoresPhysio.AzulClaro)
    }
}

/** Una sesión que el paciente hizo en casa, con su semáforo (RFN-47). */
@Composable
private fun TarjetaSeguimiento(sesion: SesionTerapia) {
    TarjetaPhysio(borde = sesion.semaforo.color) {
        Text(sesion.paciente.ifBlank { "Paciente ${sesion.pacienteId}" }, style = MaterialTheme.typography.titleSmall)
        Text(
            "${fechaIsoLegible(sesion.fechaHoraInicio)} · ${sesion.porcentajeCumplimiento} % cumplido · dolor ${sesion.dolorAntes} → ${sesion.dolorDespues}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Etiqueta(sesion.semaforo.etiqueta, sesion.semaforo.color)
            val conCamara = sesion.ejercicios.count { it.verificadoConCamara }
            val conSensores = sesion.ejercicios.count { it.verificado } - conCamara
            if (conCamara > 0) {
                Etiqueta(if (conCamara == 1) "1 verificado con cámara" else "$conCamara verificados con cámara", ColoresPhysio.Exito)
            }
            if (conSensores > 0) {
                Etiqueta(if (conSensores == 1) "1 medido con sensores" else "$conSensores medidos con sensores", ColoresPhysio.AzulClaro)
            }
        }
        sesion.observacion?.let {
            Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
