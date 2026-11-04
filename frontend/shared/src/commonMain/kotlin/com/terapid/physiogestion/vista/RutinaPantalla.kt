package com.terapid.physiogestion.vista

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.plataforma.Entorno
import com.terapid.physiogestion.plataforma.Plataforma
import com.terapid.physiogestion.plataforma.formatearFechaHora
import com.terapid.physiogestion.viewmodel.EstadoEnvio
import com.terapid.physiogestion.viewmodel.RutinaUiState
import com.terapid.physiogestion.viewmodel.RutinaViewModel
import com.terapid.physiogestion.vista.componentes.BotonPildora
import com.terapid.physiogestion.vista.componentes.BotonRedondo
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.Chip
import com.terapid.physiogestion.vista.componentes.DetalleTecnico
import com.terapid.physiogestion.vista.componentes.Iconos
import com.terapid.physiogestion.vista.componentes.PortadaEscaleras
import com.terapid.physiogestion.vista.componentes.SelectorDificultad
import com.terapid.physiogestion.vista.componentes.SelectorDolor
import com.terapid.physiogestion.vista.componentes.TarjetaEjercicio
import com.terapid.physiogestion.vista.componentes.TarjetaEpisodio
import com.terapid.physiogestion.vista.componentes.TarjetaResultado
import com.terapid.physiogestion.vista.componentes.TarjetaSuave
import com.terapid.physiogestion.vista.componentes.TituloSeccion
import com.terapid.physiogestion.vista.tema.ColoresPhysio
import com.terapid.physiogestion.vista.tema.Movimiento
import kotlinx.coroutines.launch

/**
 * VISTA «Mi rutina» del PACIENTE (v1.5), presentada como la página de una serie:
 * portada con su meta, botón para continuar, los ejercicios como episodios y, al
 * final, cómo se sintió para guardar la sesión. Solo dibuja el estado del ViewModel.
 */
@Composable
fun RutinaPantalla(
    usuario: Usuario,
    viewModel: RutinaViewModel,
    alRegresar: () -> Unit,
    alMedirEjercicio: (Int) -> Unit,
    alVerHistorial: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = ColoresPhysio.Fondo) {
        when {
            estado.cargando && estado.rutina == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Cargando("Descargando tu rutina…") }
            estado.rutina == null -> PantallaError(estado.mensajeError, viewModel::cargarRutina, alRegresar)
            else -> ContenidoRutina(usuario, estado, viewModel, alRegresar, alMedirEjercicio, alVerHistorial)
        }
    }
}

@Composable
private fun PantallaError(mensaje: String?, alReintentar: () -> Unit, alRegresar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sin rutina disponible", style = MaterialTheme.typography.headlineSmall, color = ColoresPhysio.Texto)
        Text(mensaje ?: "Ocurrió un problema al cargar tu rutina.", color = ColoresPhysio.TextoSecundario, textAlign = TextAlign.Center)
        BotonPildora("Reintentar", alReintentar, icono = null)
        BotonPildora("Regresar", alRegresar, icono = Iconos.Atras, violeta = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContenidoRutina(
    usuario: Usuario,
    estado: RutinaUiState,
    viewModel: RutinaViewModel,
    alRegresar: () -> Unit,
    alMedirEjercicio: (Int) -> Unit,
    alVerHistorial: () -> Unit,
) {
    val rutina = estado.rutina ?: return
    val lista = rememberLazyListState()
    val alcance = rememberCoroutineScope()
    val siguiente = rutina.ejercicios.withIndex().firstOrNull { it.value.id !in estado.completados }
    val indiceFormulario = 4 + rutina.ejercicios.size

    LazyColumn(state = lista, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        // ---------------- Portada ----------------
        item(key = "portada") {
            Box(Modifier.fillMaxWidth()) {
                PortadaEscaleras(
                    ColoresPhysio.Violeta,
                    Modifier.fillMaxWidth().height(300.dp),
                    avance = 0.15f + 0.85f * estado.avance,
                )
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    BotonRedondo(Iconos.Atras, "Volver al inicio", alRegresar, tamano = 44.dp, fondo = ColoresPhysio.Fondo.copy(alpha = 0.55f))
                    Box(Modifier.weight(1f))
                    BotonRedondo(Iconos.Reloj, "Ver mi historial", alVerHistorial, tamano = 44.dp, fondo = ColoresPhysio.Fondo.copy(alpha = 0.55f))
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 218.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("TU RUTINA", color = ColoresPhysio.Lila, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(
                        rutina.metaFuncional,
                        style = if (rutina.metaFuncional.length <= 24) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text("Asignada por ${rutina.fisioterapeuta} · desde ${rutina.vigenteDesde}", color = ColoresPhysio.TextoSecundario, fontSize = 15.sp, textAlign = TextAlign.Center)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("${estado.completados.size} de ${estado.totalEjercicios} hechos")
                        Chip("Límite de dolor ${rutina.umbralDolor}/10")
                        Chip("Racha ${estado.racha} d · ${estado.puntosTotales} pts")
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val e = siguiente?.value
                        BotonPildora(
                            when {
                                e == null -> "Registrar cómo me sentí"
                                estado.completados.isEmpty() -> "Empezar · ejercicio ${siguiente.index + 1}"
                                else -> "Continuar · ejercicio ${siguiente.index + 1}"
                            },
                            {
                                if (e != null && e.medibleConSensores) alMedirEjercicio(e.id)
                                else alcance.launch { lista.animateScrollToItem(if (e == null) indiceFormulario else 4 + (siguiente?.index ?: 0)) }
                            },
                            Modifier.weight(1f),
                            icono = if (e == null) Iconos.Check else Iconos.Reproducir,
                        )
                    }
                    Origen(estado)
                }
            }
        }

        // ---------------- Ejercicios como episodios ----------------
        item(key = "episodios") {
            Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TituloSeccion("Ejercicios de hoy", Modifier.padding(horizontal = 20.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(rutina.ejercicios, key = { _, e -> e.id }) { i, e ->
                        TarjetaEpisodio(
                            numero = i + 1,
                            zona = e.zonaCorporal,
                            titulo = "${i + 1}. ${e.nombre}",
                            detalle = dosis(e),
                            metodo = metodo(e),
                            alPulsar = {
                                if (e.medibleConSensores) alMedirEjercicio(e.id)
                                else alcance.launch { lista.animateScrollToItem(4 + i) }
                            },
                            completado = e.id in estado.completados,
                            avance = if (siguiente?.value?.id == e.id && estado.mediciones[e.id] != null) 0.5f else null,
                        )
                    }
                }
            }
        }

        // ---------------- Avance ----------------
        item(key = "avance") {
            val avance by animateFloatAsState(estado.avance, Movimiento.resorteProgreso(), label = "avanceRutina")
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Detalle e indicaciones", style = MaterialTheme.typography.titleLarge, color = ColoresPhysio.Texto, modifier = Modifier.weight(1f))
                    Text("${estado.completados.size} de ${estado.totalEjercicios}", color = ColoresPhysio.Lila, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(ColoresPhysio.SuperficieAlta)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(avance).clip(RoundedCornerShape(50)).background(ColoresPhysio.Violeta))
                }
            }
        }

        item(key = "separador") { Box(Modifier.height(0.dp)) }

        itemsIndexed(rutina.ejercicios, key = { _, e -> "detalle-${e.id}" }) { i, ejercicio ->
            Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).animateItem()) {
                TarjetaEjercicio(
                    numero = i + 1,
                    ejercicio = ejercicio,
                    realizado = ejercicio.id in estado.completados,
                    alCambiar = { viewModel.alternarEjercicio(ejercicio.id) },
                    medicion = estado.mediciones[ejercicio.id],
                    alMedir = { alMedirEjercicio(ejercicio.id) },
                )
            }
        }

        // ---------------- Cómo se sintió y guardar ----------------
        item(key = "formulario") {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                TarjetaSuave {
                    Text("¿Cómo te sientes?", style = MaterialTheme.typography.titleLarge, color = ColoresPhysio.Texto)
                    SelectorDolor("Dolor antes de la sesión", estado.dolorAntes, viewModel::cambiarDolorAntes)
                    SelectorDolor("Dolor después de la sesión", estado.dolorDespues, viewModel::cambiarDolorDespues)
                    SelectorDificultad(estado.dificultad, viewModel::cambiarDificultad)
                    OutlinedTextField(
                        value = estado.observacion,
                        onValueChange = viewModel::cambiarObservacion,
                        label = { Text("Observación para tu fisioterapeuta (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
        }

        item(key = "guardar") {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(estado.aviso != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Text(estado.aviso.orEmpty(), color = ColoresPhysio.Pendiente, style = MaterialTheme.typography.bodyMedium)
                }
                if (estado.guardando) {
                    Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(26.dp), color = ColoresPhysio.Lila, strokeWidth = 3.dp)
                    }
                } else {
                    BotonPildora("Guardar sesión", viewModel::guardarSesion, Modifier.fillMaxWidth(), icono = Iconos.Check, violeta = true)
                }
                AnimatedVisibility(estado.resultado != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    estado.resultado?.let { resultado ->
                        val enviada = when (estado.envio) {
                            EstadoEnvio.ENVIADA -> true
                            EstadoEnvio.PENDIENTE -> false
                            else -> null
                        }
                        TarjetaResultado(resultado, enviada, viewModel::cerrarResultado)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 8.dp)) {
                    DetalleTecnico(estado.detalleTecnico)
                    Text(
                        "PhysioGestion v${Plataforma.versionApp} · ${Plataforma.nombre} · ${usuario.nombre}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColoresPhysio.TextoTenue,
                    )
                }
            }
        }
    }
}

/** De dónde salió la rutina: del servidor o de la copia del teléfono (sin conexión). */
@Composable
private fun Origen(estado: RutinaUiState) {
    val fecha = estado.actualizadaEn?.let(::formatearFechaHora) ?: ""
    if (estado.origen == OrigenDatos.SERVIDOR) {
        Text("Actualizada desde el servidor · $fecha", color = ColoresPhysio.TextoTenue, fontSize = 12.sp, textAlign = TextAlign.Center)
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Chip("Sin conexión · rutina guardada el $fecha", color = ColoresPhysio.Pendiente, relleno = ColoresPhysio.FondoPendiente)
            Text(
                "Puedes hacer tus ejercicios; tus sesiones se guardan en el teléfono y se enviarán después.",
                color = ColoresPhysio.TextoSecundario,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            if (estado.copiaVencida) {
                Chip("Esta copia tiene más de ${Entorno.VIGENCIA_RUTINA_DIAS} días: conéctate para actualizarla", color = ColoresPhysio.Alerta, relleno = ColoresPhysio.FondoAlerta)
            }
        }
    }
}
