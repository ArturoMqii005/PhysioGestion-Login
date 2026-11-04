package com.terapid.physiogestion.vista

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.Ejercicio
import com.terapid.physiogestion.modelo.PacienteResumen
import com.terapid.physiogestion.modelo.fechaIsoLegible
import com.terapid.physiogestion.servicio.CampoDosis
import com.terapid.physiogestion.servicio.ItemRutina
import com.terapid.physiogestion.servicio.ReglasAgenda
import com.terapid.physiogestion.viewmodel.AgendarCitaViewModel
import com.terapid.physiogestion.viewmodel.EditarRutinaViewModel
import com.terapid.physiogestion.viewmodel.FichaPacienteViewModel
import com.terapid.physiogestion.viewmodel.PacientesViewModel
import com.terapid.physiogestion.vista.componentes.BotonEnlace
import com.terapid.physiogestion.vista.componentes.BotonPrincipal
import com.terapid.physiogestion.vista.componentes.CampoTexto
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.MensajeError
import com.terapid.physiogestion.vista.componentes.MensajeInfo
import com.terapid.physiogestion.vista.componentes.PantallaFormulario
import com.terapid.physiogestion.vista.componentes.TarjetaCita
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/*
 * VISTAS de la operación clínica (fase 2): agendar o reprogramar una cita, lista y
 * ficha de pacientes, y edición de la rutina. Solo dibujan el estado de su ViewModel.
 */

// =========================================================================== AGENDAR O REPROGRAMAR (CIT-05, CIT-06)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgendarCitaPantalla(viewModel: AgendarCitaViewModel, alRegresar: () -> Unit, alGuardar: (Cita) -> Unit) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(e.guardada) { e.guardada?.let(alGuardar) }
    val reprogramando = e.reprogramando
    PantallaFormulario(
        titulo = if (reprogramando != null) "Reprogramar cita" else "Agendar cita",
        subtitulo = reprogramando?.let { "Ahora: ${ReglasAgenda.etiquetaDia(it.fecha, e.hoy)} a las ${it.hora} con ${it.fisioterapeuta}." }
            ?: "Las horas ocupadas del fisioterapeuta no aparecen.",
        alRegresar = alRegresar,
    ) {
        if (e.cargando) {
            Cargando("Cargando fisioterapeutas y pacientes…")
            return@PantallaFormulario
        }
        TarjetaPhysio {
            Text("Paciente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (reprogramando != null) {
                Text(e.nombrePaciente, style = MaterialTheme.typography.bodyLarge)
            } else {
                CampoTexto(
                    e.nombrePaciente, viewModel::cambiarNombre, "Nombre del paciente",
                    ayuda = if (e.pacienteId != null) "Paciente con expediente en la app" else "Escribe para buscar, o el nombre completo si es nuevo",
                    habilitado = !e.guardando, mayusculas = KeyboardCapitalization.Words,
                )
                e.sugerencias.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { viewModel.elegirPaciente(p) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.nombre, modifier = Modifier.weight(1f))
                        p.fisioterapeuta?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }

        TarjetaPhysio {
            Text("Fisioterapeuta", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                e.fisioterapeutas.forEach { f ->
                    FilterChip(selected = e.fisioterapeutaId == f.id, onClick = { viewModel.elegirFisioterapeuta(f.id) }, label = { Text(f.nombre) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            Text("Día", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                e.dias.forEach { d ->
                    FilterChip(selected = e.fecha == d, onClick = { viewModel.elegirFecha(d) }, label = { Text(ReglasAgenda.etiquetaDia(d, e.hoy)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            Text("Duración", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReglasAgenda.DURACIONES.forEach { d ->
                    FilterChip(selected = e.duracion == d, onClick = { viewModel.elegirDuracion(d) }, label = { Text("$d min") }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            Text("Hora", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            when {
                e.cargandoHoras -> Cargando("Buscando horas libres…")
                e.horasLibres.isEmpty() -> Text("No hay horas libres ese día. Prueba otro día u otro fisioterapeuta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    e.horasLibres.forEach { h ->
                        FilterChip(selected = e.hora == h, onClick = { viewModel.elegirHora(h) }, label = { Text(h) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
        }

        TarjetaPhysio {
            CampoTexto(
                e.motivo, viewModel::cambiarMotivo, "Motivo",
                ayuda = "Por ejemplo: rehabilitación de rodilla, valoración inicial", habilitado = !e.guardando,
                mayusculas = KeyboardCapitalization.Sentences, accion = ImeAction.Done, alTerminar = viewModel::guardar,
            )
        }
        MensajeError(e.error)
        BotonPrincipal(if (reprogramando != null) "Guardar nueva fecha" else "Agendar cita", e.guardando, viewModel::guardar)
    }
}

// =========================================================================== PACIENTES (PAC-02)

@Composable
fun PacientesPantalla(viewModel: PacientesViewModel, hoy: String, alRegresar: () -> Unit, alAbrir: (Int) -> Unit) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    PantallaFormulario("Pacientes", "${e.pacientes.size} en total. Toca uno para ver su ficha.", alRegresar) {
        CampoTexto(e.filtro, viewModel::cambiarFiltro, "Buscar por nombre", mayusculas = KeyboardCapitalization.Words, accion = ImeAction.Done)
        when {
            e.cargando -> Cargando("Cargando pacientes…")
            e.error != null -> ErrorConReintento(e.error ?: "", viewModel::cargar)
            e.visibles.isEmpty() -> Text("No hay pacientes que coincidan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> e.visibles.forEach { FilaPaciente(it, hoy) { alAbrir(it.pacienteId) } }
        }
    }
}

@Composable
private fun FilaPaciente(p: PacienteResumen, hoy: String, alPulsar: () -> Unit) {
    TarjetaPhysio(modifier = Modifier.clickable(role = Role.Button, onClick = alPulsar)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(p.fisioterapeuta ?: "Sin fisioterapeuta asignado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            p.semaforo?.let { Etiqueta(it.etiqueta, it.color) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!p.tieneCuenta) Etiqueta("Sin cuenta", ColoresPhysio.Pendiente)
            if (!p.tieneRutina) Etiqueta("Sin rutina", ColoresPhysio.Alerta)
            p.cumplimientoPromedio?.let { Etiqueta("Cumplimiento $it %", ColoresPhysio.AzulClaro) }
        }
        p.proximaCita?.let { Text("Próxima cita: ${ReglasAgenda.citaLegible(it, hoy)}", style = MaterialTheme.typography.bodySmall) }
    }
}

// =========================================================================== FICHA (PAC-03, PAC-04)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FichaPacientePantalla(
    viewModel: FichaPacienteViewModel,
    hoy: String,
    puedeEditarRutina: Boolean,
    alRegresar: () -> Unit,
    alEditarRutina: () -> Unit,
) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    val ficha = e.ficha
    PantallaFormulario(ficha?.resumen?.nombre ?: "Ficha del paciente", ficha?.resumen?.correo, alRegresar) {
        when {
            e.cargando -> Cargando("Cargando ficha…")
            ficha == null -> ErrorConReintento(e.error ?: "No se pudo cargar la ficha.", viewModel::cargar)
            else -> {
                val r = ficha.resumen
                TarjetaPhysio {
                    Text("Fisioterapeuta", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(r.fisioterapeuta ?: "Sin asignar", color = if (r.fisioterapeuta == null) ColoresPhysio.Pendiente else MaterialTheme.colorScheme.onSurface)
                    if (viewModel.puedeAsignar) {
                        Text("Cambiar a:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            e.fisioterapeutas.forEach { f ->
                                FilterChip(
                                    selected = r.fisioterapeutaId == f.id,
                                    onClick = { viewModel.asignar(f.id) },
                                    enabled = !e.asignando,
                                    label = { Text(f.nombre) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Etiqueta(if (r.tieneCuenta) "Usa la app" else "Sin cuenta en la app", if (r.tieneCuenta) ColoresPhysio.Exito else ColoresPhysio.Pendiente)
                        r.cumplimientoPromedio?.let { Etiqueta("Cumplimiento $it %", ColoresPhysio.AzulClaro) }
                    }
                }
                MensajeInfo(e.aviso)

                if (ficha.citas.isNotEmpty()) {
                    Text("Próximas citas", style = MaterialTheme.typography.titleMedium)
                    ficha.citas.forEach { TarjetaCita(it, mostrarFisioterapeuta = true) }
                }

                if (!viewModel.puedeAsignar || ficha.rutina != null) {
                    Text("Rutina", style = MaterialTheme.typography.titleMedium)
                    val rutina = ficha.rutina
                    TarjetaPhysio {
                        if (rutina == null) {
                            Text("Todavía no tiene rutina asignada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(rutina.metaFuncional, fontWeight = FontWeight.SemiBold)
                            Text("Umbral de dolor: ${rutina.umbralDolor} · vigente desde ${rutina.vigenteDesde}", style = MaterialTheme.typography.bodySmall)
                            rutina.ejercicios.forEach { ej ->
                                Text("• ${ej.nombre}: ${ej.series} × ${ej.repeticiones}" + (ej.anguloObjetivo?.let { " · $it°" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (puedeEditarRutina) {
                            OutlinedButton(onClick = alEditarRutina, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (rutina == null) "Asignar rutina" else "Modificar rutina")
                            }
                        }
                    }
                }

                if (ficha.sesionesRecientes.isNotEmpty()) {
                    Text("Últimas sesiones en casa", style = MaterialTheme.typography.titleMedium)
                    ficha.sesionesRecientes.forEach { s ->
                        TarjetaPhysio {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(fechaIsoLegible(s.fechaHoraInicio), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Etiqueta(s.semaforo.etiqueta, s.semaforo.color)
                            }
                            Text("Cumplimiento ${s.porcentajeCumplimiento} % · dolor ${s.dolorAntes} → ${s.dolorDespues}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (ficha.pruebasEscaleras.isNotEmpty()) {
                    Text("Pruebas de escaleras", style = MaterialTheme.typography.titleMedium)
                    ficha.pruebasEscaleras.forEach { p ->
                        Text(
                            "${fechaIsoLegible(p.fecha)} · ${p.alturaSubidaCm / 100.0} m · ${p.pisos} pisos · ${p.duracionSegundos} s",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================== EDITAR RUTINA (RUT-08)

@Composable
fun EditarRutinaPantalla(viewModel: EditarRutinaViewModel, alRegresar: () -> Unit, alGuardar: () -> Unit) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(e.guardada) { if (e.guardada) alGuardar() }
    PantallaFormulario(
        titulo = if (e.esNueva) "Asignar rutina" else "Modificar rutina",
        subtitulo = e.paciente.ifEmpty { null }?.let { "$it verá los cambios la próxima vez que abra su rutina." },
        alRegresar = alRegresar,
    ) {
        when {
            e.cargando -> {
                Cargando("Cargando catálogo de ejercicios…")
                return@PantallaFormulario
            }
            e.catalogo.isEmpty() -> {
                ErrorConReintento(e.error ?: "No se pudo cargar el catálogo.", viewModel::cargar)
                return@PantallaFormulario
            }
        }
        val b = e.borrador
        TarjetaPhysio {
            CampoTexto(
                b.metaFuncional, viewModel::cambiarMeta, "Meta funcional",
                ayuda = "Lo que el paciente quiere recuperar, en sus palabras", mayusculas = KeyboardCapitalization.Sentences,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Umbral de dolor", fontWeight = FontWeight.SemiBold)
                    Text("Desde este nivel la app sugiere suspender.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Contador(b.umbralDolor.toString(), { viewModel.cambiarUmbral(-1) }, { viewModel.cambiarUmbral(1) })
            }
        }

        Text("Ejercicios (${b.items.size} de 10)", style = MaterialTheme.typography.titleMedium)
        b.items.forEachIndexed { i, item -> TarjetaItemRutina(item, primero = i == 0, viewModel) }
        OutlinedButton(onClick = viewModel::abrirCatalogo, enabled = b.items.size < 10, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("+ Agregar ejercicio del catálogo")
        }
        MensajeError(e.error)
        BotonPrincipal("Guardar rutina", e.guardando, viewModel::guardar)
    }

    if (e.mostrandoCatalogo) DialogoCatalogo(e.disponibles, viewModel::agregar, viewModel::cerrarCatalogo)
}

@Composable
private fun TarjetaItemRutina(item: ItemRutina, primero: Boolean, viewModel: EditarRutinaViewModel) {
    val ej = item.ejercicio
    TarjetaPhysio {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ej.nombre, fontWeight = FontWeight.SemiBold)
                Text(ej.zonaCorporal + listOfNotNull(" · sensores".takeIf { ej.medibleConSensores }, " · cámara".takeIf { ej.admiteVerificacionPorCamara }).joinToString(""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!primero) BotonEnlace("Subir", { viewModel.subir(ej.id) })
            BotonEnlace("Quitar", { viewModel.quitar(ej.id) })
        }
        FilaDosis("Series", item.series.toString(), { viewModel.ajustar(ej.id, CampoDosis.SERIES, -1) }, { viewModel.ajustar(ej.id, CampoDosis.SERIES, 1) })
        FilaDosis("Repeticiones", item.repeticiones.toString(), { viewModel.ajustar(ej.id, CampoDosis.REPETICIONES, -1) }, { viewModel.ajustar(ej.id, CampoDosis.REPETICIONES, 1) })
        FilaDosis("Descanso", "${item.descansoSegundos} s", { viewModel.ajustar(ej.id, CampoDosis.DESCANSO, -1) }, { viewModel.ajustar(ej.id, CampoDosis.DESCANSO, 1) })
        if (ej.medibleConSensores || item.anguloObjetivo != null) {
            FilaDosis("Ángulo objetivo", item.anguloObjetivo?.let { "$it°" } ?: "—", { viewModel.ajustar(ej.id, CampoDosis.ANGULO, -1) }, { viewModel.ajustar(ej.id, CampoDosis.ANGULO, 1) })
        }
    }
}

@Composable
private fun FilaDosis(etiqueta: String, valor: String, alMenos: () -> Unit, alMas: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Contador(valor, alMenos, alMas)
    }
}

@Composable
private fun Contador(valor: String, alMenos: () -> Unit, alMas: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = alMenos, modifier = Modifier.heightIn(min = 48.dp)) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(valor, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        TextButton(onClick = alMas, modifier = Modifier.heightIn(min = 48.dp)) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun DialogoCatalogo(disponibles: List<Ejercicio>, alElegir: (Ejercicio) -> Unit, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Catálogo de ejercicios") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (disponibles.isEmpty()) Text("Ya agregaste todos los ejercicios del catálogo.")
                disponibles.groupBy { it.zonaCorporal }.forEach { (zona, lista) ->
                    Text(zona, color = ColoresPhysio.Turquesa, fontWeight = FontWeight.SemiBold)
                    lista.forEach { ej ->
                        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { alElegir(ej) }) {
                            Text(ej.nombre, fontWeight = FontWeight.Medium)
                            Text("${ej.series} × ${ej.repeticiones}" + (ej.anguloObjetivo?.let { " · $it°" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}
