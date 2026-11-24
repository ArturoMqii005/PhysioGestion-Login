package com.terapid.physiogestion.vista

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.SolicitudReagenda
import com.terapid.physiogestion.modelo.fechaIsoLegible
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.claveDelDia
import com.terapid.physiogestion.servicio.ReglasAgenda
import com.terapid.physiogestion.viewmodel.AprobacionUiState
import com.terapid.physiogestion.viewmodel.SolicitudesViewModel
import com.terapid.physiogestion.vista.componentes.BotonEnlace
import com.terapid.physiogestion.vista.componentes.BotonPildora
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.ChipOpcion
import com.terapid.physiogestion.vista.componentes.EncabezadoSeccion
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.ExpresionXochi
import com.terapid.physiogestion.vista.componentes.MensajeInfo
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.XochiDice
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * VISTA de recepción y administración: solicitudes de cambio de cita que mandan
 * los pacientes desde la app cuando faltan menos de 24 horas o ya la movieron
 * dos veces. Cada una se aprueba (eligiendo día, hora libre y fisioterapeuta)
 * o se rechaza con un motivo; al paciente le llega un aviso en los dos casos.
 */
@Composable
fun SolicitudesPantalla(viewModel: SolicitudesViewModel, alAbrirFicha: (Int) -> Unit) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    val hoy = claveDelDia(ahoraEnMilisegundos())
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EncabezadoSeccion(
                    "Solicitudes de cambio",
                    "Los pacientes piden mover su cita cuando faltan menos de 24 horas o ya la cambiaron dos veces.",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipOpcion("Pendientes", e.soloPendientes, { viewModel.filtrar(true) })
                    ChipOpcion("Todas", !e.soloPendientes, { viewModel.filtrar(false) })
                }
            }
            e.aviso?.let { item { MensajeInfo(it) } }
            when {
                e.cargando -> item { Cargando("Cargando solicitudes…") }
                e.error != null -> item { ErrorConReintento(e.error ?: "", viewModel::cargar) }
                e.solicitudes.isEmpty() -> item {
                    XochiDice(
                        if (e.soloPendientes) "No hay solicitudes pendientes. Cuando un paciente pida cambiar su cita aparecerá aquí."
                        else "Todavía no hay solicitudes. Cuando un paciente pida cambiar su cita aparecerá aquí.",
                        expresion = ExpresionXochi.FELIZ,
                        tamano = 72.dp,
                    )
                }
                else -> items(e.solicitudes, key = { it.id }) { s ->
                    TarjetaSolicitud(
                        s, hoy,
                        alAbrirFicha = { alAbrirFicha(s.pacienteId) },
                        alAprobar = { viewModel.abrirAprobar(s) },
                        alRechazar = { viewModel.abrirRechazar(s) },
                    )
                }
            }
        }
    }

    e.aprobando?.let { a ->
        DialogoSeccion(alCerrar = viewModel::cerrarDialogo) {
            ContenidoAprobar(a, hoy, e.enviando, e.errorDialogo, e.puedeAprobar, viewModel)
        }
    }
    e.rechazando?.let { s ->
        DialogoSeccion(alCerrar = viewModel::cerrarDialogo) {
            Text("Rechazar solicitud", style = MaterialTheme.typography.headlineSmall, color = ColoresPhysio.Texto)
            Text(
                "La cita de ${s.paciente} se queda igual. Explícale por qué; le llega como aviso en la app.",
                color = ColoresPhysio.TextoSecundario, fontSize = 14.sp,
            )
            OutlinedTextField(
                value = e.motivoRechazo,
                onValueChange = viewModel::escribirMotivo,
                label = { Text("Motivo") },
                placeholder = { Text("Por ejemplo: no hay horas libres esa semana; te llamamos para ofrecerte otra.") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                maxLines = 4,
                enabled = !e.enviando,
            )
            e.errorDialogo?.let { Text(it, color = ColoresPhysio.Pendiente, fontSize = 14.sp) }
            BotonPildora("Rechazar", viewModel::rechazar, Modifier.fillMaxWidth(), icono = null, violeta = true, habilitado = e.puedeRechazar)
            TextButton(onClick = viewModel::cerrarDialogo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !e.enviando) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun TarjetaSolicitud(
    s: SolicitudReagenda,
    hoy: String,
    alAbrirFicha: () -> Unit,
    alAprobar: () -> Unit,
    alRechazar: () -> Unit,
) {
    TarjetaPhysio(borde = if (s.pendiente) ColoresPhysio.Pendiente else MaterialTheme.colorScheme.outline) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.paciente, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${s.fisioterapeuta} · pedida ${fechaIsoLegible(s.creada)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Etiqueta(etiquetaEstado(s.estado), colorEstado(s.estado))
        }
        Text(
            "Cita actual: ${ReglasAgenda.etiquetaDia(s.fechaActual, hoy)} a las ${s.horaActual}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val propuesta = if (s.fechaPropuesta != null && s.horaPropuesta != null) {
            "${ReglasAgenda.etiquetaDia(s.fechaPropuesta, hoy)} a las ${s.horaPropuesta}"
        } else {
            "sin horario específico"
        }
        Text("Propone: $propuesta", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (s.mensaje.isNotBlank()) {
            Text("«${s.mensaje}»", style = MaterialTheme.typography.bodyMedium, color = ColoresPhysio.Lila)
        }
        s.respuesta?.let {
            Text(
                "Respuesta de ${s.atendidaPor ?: "recepción"}: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (s.pacienteId > 0) BotonEnlace("Ver ficha del paciente", alAbrirFicha)
        if (s.pendiente) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPildora("Aprobar y mover", alAprobar, Modifier.weight(1f), icono = null, violeta = true, compacta = true)
                OutlinedButton(onClick = alRechazar, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("Rechazar", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContenidoAprobar(
    a: AprobacionUiState,
    hoy: String,
    enviando: Boolean,
    error: String?,
    puedeAprobar: Boolean,
    viewModel: SolicitudesViewModel,
) {
    Text("Aprobar y mover la cita", style = MaterialTheme.typography.headlineSmall, color = ColoresPhysio.Texto)
    Text("${a.solicitud.paciente}: «${a.solicitud.mensaje}»", color = ColoresPhysio.TextoSecundario, fontSize = 14.sp)

    Text("Día", fontWeight = FontWeight.SemiBold, color = ColoresPhysio.Texto)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        a.dias.forEach { d -> ChipOpcion(ReglasAgenda.etiquetaDia(d, hoy), d == a.fecha, { viewModel.elegirFecha(d) }) }
    }

    if (a.fisioterapeutas.size > 1) {
        Text("Fisioterapeuta", fontWeight = FontWeight.SemiBold, color = ColoresPhysio.Texto)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            a.fisioterapeutas.forEach { f -> ChipOpcion(f.nombre, f.id == a.fisioterapeutaId, { viewModel.elegirFisioterapeuta(f.id) }) }
        }
    }

    Text("Hora", fontWeight = FontWeight.SemiBold, color = ColoresPhysio.Texto)
    when {
        a.cargandoHoras -> CircularProgressIndicator(Modifier.size(24.dp), color = ColoresPhysio.Lila)
        a.horas.isEmpty() -> Text(
            "No hay horas libres ese día con este fisioterapeuta. Prueba otro día u otra persona.",
            color = ColoresPhysio.Pendiente, fontSize = 14.sp,
        )
        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            a.horas.forEach { h -> ChipOpcion(h, h == a.hora, { viewModel.elegirHora(h) }) }
        }
    }
    if (a.propuestaOcupada) {
        Text("Las ${a.solicitud.horaPropuesta} ya no están libres; elige otra hora.", color = ColoresPhysio.Pendiente, fontSize = 14.sp)
    }

    OutlinedTextField(
        value = a.nota,
        onValueChange = viewModel::escribirNota,
        label = { Text("Mensaje para el paciente (opcional)") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        maxLines = 3,
        enabled = !enviando,
    )
    error?.let { Text(it, color = ColoresPhysio.Pendiente, fontSize = 14.sp) }
    BotonPildora("Aprobar", viewModel::aprobar, Modifier.fillMaxWidth(), icono = null, violeta = true, habilitado = puedeAprobar)
    TextButton(onClick = viewModel::cerrarDialogo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !enviando) {
        Text("Cancelar")
    }
}

/** Ventana flotante con el estilo de la app (la usan Solicitudes y Anuncios). */
@Composable
internal fun DialogoSeccion(alCerrar: () -> Unit, contenido: @Composable () -> Unit) {
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = ColoresPhysio.Superficie,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                contenido()
            }
        }
    }
}

private fun etiquetaEstado(estado: String): String = when (estado) {
    "PENDIENTE" -> "Pendiente"
    "APROBADA" -> "Aprobada"
    "RECHAZADA" -> "Rechazada"
    else -> estado
}

private fun colorEstado(estado: String) = when (estado) {
    "PENDIENTE" -> ColoresPhysio.Pendiente
    "APROBADA" -> ColoresPhysio.Exito
    else -> ColoresPhysio.TextoSecundario
}
