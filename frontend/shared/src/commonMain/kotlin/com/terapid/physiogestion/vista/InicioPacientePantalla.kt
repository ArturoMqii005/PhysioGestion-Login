package com.terapid.physiogestion.vista

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.Ejercicio
import com.terapid.physiogestion.modelo.OrigenDatos
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.servicio.ReglasAgenda
import com.terapid.physiogestion.viewmodel.EstadoPasos
import com.terapid.physiogestion.viewmodel.InicioPacienteUiState
import com.terapid.physiogestion.viewmodel.InicioPacienteViewModel
import com.terapid.physiogestion.viewmodel.MisCitasViewModel
import com.terapid.physiogestion.viewmodel.ResumenAvisosViewModel
import com.terapid.physiogestion.vista.componentes.ALTO_BARRA_PESTANAS
import com.terapid.physiogestion.vista.componentes.BotonEnlace
import com.terapid.physiogestion.vista.componentes.BotonPildora
import com.terapid.physiogestion.vista.componentes.BotonRedondo
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.Chip
import com.terapid.physiogestion.vista.componentes.DialogoCancelarCita
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.Iconos
import com.terapid.physiogestion.vista.componentes.Insignia
import com.terapid.physiogestion.vista.componentes.PortadaEscaleras
import com.terapid.physiogestion.vista.componentes.PuntosCarrusel
import com.terapid.physiogestion.vista.componentes.TarjetaActivarNotificaciones
import com.terapid.physiogestion.vista.componentes.TarjetaAvisosSinLeer
import com.terapid.physiogestion.vista.componentes.TarjetaEpisodio
import com.terapid.physiogestion.vista.componentes.TarjetaSuave
import com.terapid.physiogestion.vista.componentes.TituloSeccion
import com.terapid.physiogestion.vista.componentes.VigilarAvisos
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta
import com.terapid.physiogestion.vista.componentes.notificacionesOVacio
import com.terapid.physiogestion.vista.componentes.resumenOVacio
import com.terapid.physiogestion.vista.tema.ColoresPhysio
import com.terapid.physiogestion.vista.tema.Movimiento
import com.terapid.physiogestion.vista.tema.presionable
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Una página de la portada del inicio. */
private enum class PaginaPortada { RUTINA, CITA, ESCALERAS }

/**
 * VISTA principal del PACIENTE (v1.5): portada grande con su meta y un carrusel
 * que pasa solo entre la rutina de hoy, la próxima cita y la prueba de escaleras;
 * debajo, sus ejercicios como «episodios» y el resto de su día.
 */
@Composable
fun InicioPacientePantalla(
    usuario: Usuario,
    viewModel: InicioPacienteViewModel,
    alAbrirRutina: () -> Unit,
    alAbrirEscaleras: () -> Unit,
    alAbrirHistorial: () -> Unit,
    alAbrirPerfil: () -> Unit,
    alCerrarSesion: () -> Unit,
    misCitas: MisCitasViewModel? = null,
    avisos: ResumenAvisosViewModel? = null,
    alAbrirAvisos: () -> Unit = {},
    alMedirEjercicio: (Int) -> Unit = { alAbrirRutina() },
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val resumenAvisos by avisos.resumenOVacio.collectAsStateWithLifecycle()
    val notificaciones by avisos.notificacionesOVacio.collectAsStateWithLifecycle()
    val citas = misCitas?.estado?.collectAsStateWithLifecycle()?.value
    if (avisos != null) VigilarAvisos(avisos)
    // Cada vez que la pantalla aparece (también al regresar de la rutina) se actualiza el resumen.
    LaunchedEffect(Unit) { viewModel.cargar() }

    val proximaCita = citas?.proximas?.firstOrNull()
    val paginas = buildList {
        add(PaginaPortada.RUTINA)
        if (proximaCita != null) add(PaginaPortada.CITA)
        add(PaginaPortada.ESCALERAS)
    }

    Surface(Modifier.fillMaxSize(), color = ColoresPhysio.Fondo) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = ALTO_BARRA_PESTANAS + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "portada") {
                Portada(usuario, estado, paginas, proximaCita, citas?.hoy.orEmpty(), resumenAvisos.sinLeer, alAbrirAvisos, alAbrirPerfil, alAbrirRutina, alAbrirHistorial, alAbrirEscaleras)
            }

            item(key = "avisos") {
                Column(Modifier.padding(horizontal = 20.dp).animateContentSize(Movimiento.resorte()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (avisos != null) {
                        TarjetaAvisosSinLeer(resumenAvisos, alAbrirAvisos)
                        TarjetaActivarNotificaciones(
                            notificaciones,
                            "Te recordamos tus citas un día y una hora antes, y te avisamos cuando tu fisioterapeuta cambie tu rutina.",
                            avisos::activarNotificaciones,
                        )
                    }
                    AnimatedVisibility(estado.aviso != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        TarjetaSuave(borde = ColoresPhysio.Exito) {
                            Text(estado.aviso.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            BotonEnlace("Entendido", viewModel::cerrarAviso)
                        }
                    }
                }
            }

            item(key = "ejercicios") { EstanteEjercicios(estado, alAbrirRutina, alMedirEjercicio, viewModel::cargar) }

            if (misCitas != null && citas != null && !citas.cargando) {
                item(key = "citas") { SeccionCitas(misCitas) }
            }

            item(key = "numeros") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Numero("${estado.racha}", if (estado.racha == 1) "día seguido" else "días seguidos", ColoresPhysio.Lila, Modifier.weight(1f))
                    Numero("${estado.puntos}", "puntos", ColoresPhysio.Lavanda, Modifier.weight(1f))
                }
            }

            item(key = "pasos") { Box(Modifier.padding(horizontal = 20.dp)) { TarjetaPasos(estado.pasos, viewModel) } }

            if (estado.pendientesEnvio > 0) {
                item(key = "pendientes") {
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        TarjetaSuave(fondo = ColoresPhysio.FondoPendiente, borde = ColoresPhysio.FondoPendiente) {
                            Text(
                                if (estado.pendientesEnvio == 1) "1 sesión espera conexión" else "${estado.pendientesEnvio} sesiones esperan conexión",
                                color = ColoresPhysio.Pendiente,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Están guardadas en tu teléfono. Se enviarán a tu fisioterapeuta en cuanto haya conexión.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColoresPhysio.Texto,
                            )
                            BotonPildora("Enviar ahora", viewModel::sincronizar, icono = null)
                        }
                    }
                }
            }

            item(key = "accesos") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Acceso("Prueba de escaleras", "Barómetro y pasos", Iconos.Escalera, ColoresPhysio.Exito, alAbrirEscaleras, Modifier.weight(1f))
                    Acceso("Mi historial", "Sesiones anteriores", Iconos.Reloj, ColoresPhysio.AzulClaro, alAbrirHistorial, Modifier.weight(1f))
                }
            }
        }
    }
}

// ======================================================================= PORTADA

@Composable
private fun Portada(
    usuario: Usuario,
    estado: InicioPacienteUiState,
    paginas: List<PaginaPortada>,
    cita: Cita?,
    hoy: String,
    avisosSinLeer: Int,
    alAbrirAvisos: () -> Unit,
    alAbrirPerfil: () -> Unit,
    alAbrirRutina: () -> Unit,
    alAbrirHistorial: () -> Unit,
    alAbrirEscaleras: () -> Unit,
) {
    val pager = rememberPagerState { paginas.size }
    val arrastrando by pager.interactionSource.collectIsDraggedAsState()
    // El carrusel pasa solo cada 6 s; si el paciente lo mueve con el dedo, espera a que lo suelte.
    LaunchedEffect(pager, arrastrando, paginas.size) {
        if (arrastrando || paginas.size < 2) return@LaunchedEffect
        while (true) {
            delay(6_000)
            pager.animateScrollToPage((pager.currentPage + 1) % paginas.size, animationSpec = tween(700, easing = Movimiento.CurvaIos))
        }
    }
    val pagina = paginas.getOrElse(pager.currentPage) { PaginaPortada.RUTINA }
    val total = estado.rutina?.ejercicios?.size ?: 0
    val avance = if (total == 0) 0.4f else 0.15f + 0.85f * (estado.medidosHoyIds.size.toFloat() / total)

    Box(Modifier.fillMaxWidth().height(586.dp)) {
        PortadaEscaleras(
            colorBase = colorDePagina(pagina),
            modifier = Modifier.fillMaxWidth().height(400.dp),
            avance = avance,
        )

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Inicio", style = MaterialTheme.typography.headlineLarge, color = Color.White, modifier = Modifier.weight(1f))
            BotonRedondo(
                Iconos.Campana,
                if (avisosSinLeer > 0) "Avisos, $avisosSinLeer sin leer" else "Avisos",
                alAbrirAvisos,
                tamano = 48.dp,
                fondo = ColoresPhysio.Fondo.copy(alpha = 0.55f),
                insignia = avisosSinLeer,
            )
            Box(
                Modifier.padding(start = 10.dp).size(48.dp).clip(CircleShape).background(ColoresPhysio.Lila)
                    .presionable(escala = 0.9f, etiqueta = "Mi perfil", alPulsar = alAbrirPerfil)
                    .semantics { contentDescription = "Mi perfil" },
                contentAlignment = Alignment.Center,
            ) {
                Text(iniciales(usuario.nombre), color = ColoresPhysio.Fondo, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(250.dp)) { i ->
                // Al deslizar, la página que sale se desvanece y se corre un poco (paralaje).
                val desplazamiento = (pager.currentPage - i) + pager.currentPageOffsetFraction
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp).graphicsLayer {
                        alpha = 1f - abs(desplazamiento).coerceAtMost(1f) * 0.7f
                        translationX = desplazamiento * size.width * 0.25f
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                ) {
                    when (paginas[i]) {
                        PaginaPortada.RUTINA -> PaginaRutina(estado, alAbrirRutina, alAbrirHistorial)
                        PaginaPortada.CITA -> cita?.let { PaginaCita(it, hoy, alAbrirRutina) }
                        PaginaPortada.ESCALERAS -> PaginaEscaleras(alAbrirEscaleras)
                    }
                }
            }
            PuntosCarrusel(pager, Modifier.padding(top = 18.dp))
        }
    }
}

private fun colorDePagina(p: PaginaPortada): Color = when (p) {
    PaginaPortada.RUTINA -> ColoresPhysio.Violeta
    PaginaPortada.CITA -> Color(0xFF3D6BE0)
    PaginaPortada.ESCALERAS -> Color(0xFF1F9D6B)
}

@Composable
private fun PaginaRutina(estado: InicioPacienteUiState, alAbrirRutina: () -> Unit, alAbrirHistorial: () -> Unit) {
    val rutina = estado.rutina
    when {
        rutina == null && estado.cargando -> Cargando("Cargando tu rutina…")
        rutina == null -> {
            TituloPortada("Sin rutina por ahora")
            Text(estado.error ?: "No pudimos cargar tu rutina.", color = ColoresPhysio.Pendiente, textAlign = TextAlign.Center)
            BotonPildora("Reintentar", alAbrirRutina, icono = null)
        }
        else -> {
            val hechos = estado.medidosHoyIds.size
            Chip(
                when {
                    estado.origen == OrigenDatos.ALMACENAMIENTO_LOCAL -> "Sin conexión · copia del teléfono"
                    hechos == 0 -> "Tu meta"
                    else -> "Hoy llevas $hechos de ${rutina.ejercicios.size}"
                },
                relleno = Color.White.copy(alpha = 0.1f),
            )
            TituloPortada(rutina.metaFuncional)
            Text(
                "${zonasDe(rutina.ejercicios)} · ${rutina.ejercicios.size} ejercicios · ${rutina.fisioterapeuta}",
                color = ColoresPhysio.TextoSecundario,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonPildora(if (hechos == 0) "Empezar hoy" else "Continuar", alAbrirRutina, Modifier.anchoPildora())
                BotonRedondo(Iconos.Reloj, "Ver mi historial", alAbrirHistorial)
            }
        }
    }
}

@Composable
private fun PaginaCita(cita: Cita, hoy: String, alVer: () -> Unit) {
    Chip("Tu próxima cita", relleno = Color.White.copy(alpha = 0.1f))
    TituloPortada("${ReglasAgenda.etiquetaDia(cita.fecha, hoy)} · ${cita.hora}")
    Text("${cita.motivo} · ${cita.fisioterapeuta}", color = ColoresPhysio.TextoSecundario, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2)
    Text("Te avisamos un día antes y una hora antes.", color = ColoresPhysio.TextoTenue, fontSize = 13.sp, textAlign = TextAlign.Center)
    BotonPildora("Preparar mi rutina", alVer, Modifier.anchoPildora(), icono = Iconos.Rutina)
}

@Composable
private fun PaginaEscaleras(alAbrir: () -> Unit) {
    Chip("Prueba de la semana", relleno = Color.White.copy(alpha = 0.1f))
    TituloPortada("¿Cuánto subes hoy?")
    Text(
        "Mide con el barómetro cuántos pisos subes y a qué ritmo, para ver tu avance cada semana.",
        color = ColoresPhysio.TextoSecundario,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        maxLines = 3,
    )
    BotonPildora("Hacer la prueba", alAbrir, Modifier.anchoPildora(), icono = Iconos.Escalera)
}

@Composable
private fun TituloPortada(texto: String) {
    Text(
        texto,
        style = if (texto.length <= 22) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** El botón principal ocupa poco más de la mitad del ancho, como en la portada del diseño. */
/** Ancho de los botones de la portada: al menos 220 dp, y crece si el texto lo necesita. */
private fun Modifier.anchoPildora() = widthIn(min = 220.dp)

// ======================================================================= EJERCICIOS

@Composable
private fun EstanteEjercicios(estado: InicioPacienteUiState, alAbrirRutina: () -> Unit, alMedir: (Int) -> Unit, alReintentar: () -> Unit) {
    val rutina = estado.rutina
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TituloSeccion(
            if (estado.medidosHoyIds.isEmpty()) "Tus ejercicios de hoy" else "Continúa donde te quedaste",
            Modifier.padding(horizontal = 20.dp),
            alPulsar = alAbrirRutina,
        )
        if (rutina == null) {
            Box(Modifier.padding(horizontal = 20.dp)) {
                if (estado.cargando) Cargando("Cargando tus ejercicios…") else BotonEnlace("Reintentar", alReintentar)
            }
            return
        }
        // Primero los que faltan; los ya medidos hoy van al final con su palomita.
        val orden = rutina.ejercicios.withIndex().sortedBy { it.value.id in estado.medidosHoyIds }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(orden, key = { _, e -> e.value.id }) { _, (i, e) ->
                TarjetaEpisodio(
                    numero = i + 1,
                    zona = e.zonaCorporal,
                    titulo = e.nombre,
                    detalle = dosis(e),
                    metodo = metodo(e),
                    alPulsar = { if (e.medibleConSensores) alMedir(e.id) else alAbrirRutina() },
                    ancho = 262.dp,
                    alto = 146.dp,
                    completado = e.id in estado.medidosHoyIds,
                    tituloDentro = true,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

fun dosis(e: Ejercicio): String =
    "${e.series} × ${e.repeticiones}" + (if (e.descansoSegundos > 0) " · ${e.descansoSegundos} s de descanso" else "")

fun metodo(e: Ejercicio): String = when {
    e.admiteVerificacionPorCamara -> "Sensores y cámara"
    e.medibleConSensores -> "Con sensores"
    else -> "A mano"
}

private fun zonasDe(ejercicios: List<Ejercicio>): String {
    val zonas = ejercicios.map { it.zonaCorporal }.distinct()
    return when {
        zonas.isEmpty() -> "Tu rutina"
        zonas.size <= 2 -> zonas.joinToString(" y ")
        else -> "${zonas.take(2).joinToString(", ")} y más"
    }
}

private fun iniciales(nombre: String): String =
    nombre.split(' ').filter { it.isNotBlank() && it.first().isLetter() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

// ======================================================================= CITAS

/** Próximas citas del paciente (CIT-08), con la opción de cancelarlas. */
@Composable
private fun SeccionCitas(viewModel: MisCitasViewModel) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TituloSeccion("Tus citas")
        if (e.proximas.isEmpty()) {
            Text("No tienes citas agendadas. Para agendar, llama a la clínica.", style = MaterialTheme.typography.bodySmall, color = ColoresPhysio.TextoSecundario)
        }
        e.proximas.forEach { c ->
            TarjetaSuave {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(
                        Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(ColoresPhysio.SuperficieAlta),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(DIAS_CORTOS[ReglasAgenda.diaSemana(c.fecha)], color = ColoresPhysio.Lila, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(c.fecha.takeLast(2), color = ColoresPhysio.Texto, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.motivo, color = ColoresPhysio.Texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("${ReglasAgenda.etiquetaDia(c.fecha, e.hoy)} · ${c.horario} · ${c.fisioterapeuta}", color = ColoresPhysio.TextoSecundario, fontSize = 13.sp)
                        Etiqueta(c.estado.etiqueta, c.estado.color)
                    }
                }
                BotonEnlace("Cancelar esta cita", { viewModel.pedirCancelar(c) }, habilitado = !e.procesando)
            }
        }
        e.aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ColoresPhysio.Lila) }
    }
    e.cancelando?.let { c -> DialogoCancelarCita(c, null, null, viewModel::confirmarCancelacion, viewModel::cerrarCancelacion) }
}

private val DIAS_CORTOS = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")

// ======================================================================= DATOS DEL DÍA

@Composable
private fun Numero(valor: String, texto: String, color: Color, modifier: Modifier) {
    TarjetaSuave(modifier) {
        Text(valor, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(texto, style = MaterialTheme.typography.bodySmall, color = ColoresPhysio.TextoSecundario)
    }
}

@Composable
private fun Acceso(titulo: String, texto: String, icono: androidx.compose.ui.graphics.vector.ImageVector, color: Color, alPulsar: () -> Unit, modifier: Modifier) {
    TarjetaSuave(modifier, alPulsar = alPulsar) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            com.terapid.physiogestion.vista.componentes.Icono(icono, null, color = color)
        }
        Text(titulo, color = ColoresPhysio.Texto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(texto, color = ColoresPhysio.TextoSecundario, fontSize = 13.sp)
    }
}

/** Contador de pasos (RFN-39). El sensor solo trabaja mientras esta tarjeta está en pantalla. */
@Composable
private fun TarjetaPasos(estadoPasos: EstadoPasos, viewModel: InicioPacienteViewModel) {
    TarjetaSuave {
        Text("Actividad de hoy", color = ColoresPhysio.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        when (estadoPasos) {
            EstadoPasos.NO_DISPONIBLE -> Text(
                "Este dispositivo no tiene contador de pasos.",
                style = MaterialTheme.typography.bodyMedium,
                color = ColoresPhysio.TextoSecundario,
            )
            EstadoPasos.FALTA_PERMISO, EstadoPasos.PERMISO_DENEGADO -> {
                Text(
                    if (estadoPasos == EstadoPasos.FALTA_PERMISO) {
                        "Para contar tus pasos, Android pide permiso de actividad física (es un dato de salud)."
                    } else {
                        "No diste permiso de actividad física. Puedes intentarlo otra vez o activarlo en Ajustes."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColoresPhysio.TextoSecundario,
                )
                BotonEnlace("Activar contador de pasos", viewModel::activarPasos)
            }
            EstadoPasos.CONTANDO -> {
                val flujo = remember(viewModel) { viewModel.pasosDeHoy() }
                val pasos by flujo.collectAsStateWithLifecycle(initialValue = null)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pasos?.toString() ?: "…", style = MaterialTheme.typography.headlineMedium, color = ColoresPhysio.AzulClaro)
                    Text("pasos", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    "Los cuenta el propio teléfono con bajo consumo de batería.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColoresPhysio.TextoSecundario,
                )
            }
        }
    }
}
