package com.terapid.physiogestion.vista

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.EstadoCita
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
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val hoy = formatearFechaHora(ahoraEnMilisegundos()).substringBefore(" ")
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { EncabezadoRol("Mi agenda de hoy", usuario, alCerrarSesion) }

            when {
                estado.cargando -> item { Cargando("Cargando tu agenda…") }
                estado.error != null -> item { ErrorConReintento(estado.error ?: "", viewModel::cargar) }
                else -> {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TarjetaDato(estado.citas.size.toString(), "Citas hoy · $hoy", ColoresPhysio.Turquesa, Modifier.weight(1f))
                            TarjetaDato(
                                estado.cuantas(EstadoCita.ATENDIDA).toString(),
                                "Atendidas",
                                ColoresPhysio.Exito,
                                Modifier.weight(1f),
                            )
                        }
                    }
                    if (estado.citas.isEmpty()) {
                        item { Text("No tienes citas registradas para hoy.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    val siguiente = estado.siguiente
                    items(estado.citas, key = { it.id }) { cita ->
                        TarjetaCita(cita, mostrarFisioterapeuta = false, resaltada = cita.id == siguiente?.id)
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
                }
            }
        }
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
            val medidos = sesion.ejercicios.count { it.verificado }
            if (medidos > 0) {
                Etiqueta(if (medidos == 1) "1 medido con sensores" else "$medidos medidos con sensores", ColoresPhysio.AzulClaro)
            }
        }
        sesion.observacion?.let {
            Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
