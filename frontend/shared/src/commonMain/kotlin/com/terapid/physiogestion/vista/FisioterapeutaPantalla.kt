package com.terapid.physiogestion.vista

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.modelo.EstadoCita
import com.terapid.physiogestion.modelo.PruebaEscalerasRegistrada
import com.terapid.physiogestion.modelo.Semaforo
import com.terapid.physiogestion.modelo.SesionTerapia
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.modelo.fechaIsoLegible
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.formatearFechaHora
import com.terapid.physiogestion.viewmodel.AgendaViewModel
import com.terapid.physiogestion.viewmodel.ResumenAvisosViewModel
import com.terapid.physiogestion.vista.componentes.BotonEnlace
import com.terapid.physiogestion.vista.componentes.BotonPildora
import com.terapid.physiogestion.vista.componentes.BotonRedondo
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.Chip
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.Iconos
import com.terapid.physiogestion.vista.componentes.MensajeInfo
import com.terapid.physiogestion.vista.componentes.SelectorDia
import com.terapid.physiogestion.vista.componentes.TarjetaActivarNotificaciones
import com.terapid.physiogestion.vista.componentes.TarjetaAvisosSinLeer
import com.terapid.physiogestion.vista.componentes.TarjetaSuave
import com.terapid.physiogestion.vista.componentes.TituloSeccion
import com.terapid.physiogestion.vista.componentes.VigilarAvisos
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta
import com.terapid.physiogestion.vista.componentes.notificacionesOVacio
import com.terapid.physiogestion.vista.componentes.resumenOVacio
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * VISTA del rol FISIOTERAPEUTA (v1.5): el aviso de dolor alto hasta arriba, su día
 * como línea de tiempo con la marca de «Ahora» y lo que sus pacientes hicieron en casa.
 */
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
    val ahora = formatearFechaHora(ahoraEnMilisegundos())
    val horaActual = ahora.takeLast(5)

    Surface(Modifier.fillMaxSize(), color = ColoresPhysio.Fondo) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ahora.substringBefore(" "), color = ColoresPhysio.TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(saludo(horaActual) + ", " + primerNombre(usuario.nombre), style = MaterialTheme.typography.headlineMedium, color = ColoresPhysio.Texto)
                    }
                    BotonRedondo(Iconos.Salir, "Cerrar sesión", alCerrarSesion, tamano = 44.dp)
                }
                if (alCambiarContrasena != null) BotonEnlace("Cambiar contraseña", alCambiarContrasena)
            }
            if (avisos != null) {
                item { TarjetaAvisosSinLeer(resumenAvisos, alAbrirAvisos) }
                item { TarjetaActivarNotificaciones(notificaciones, "Te avisamos en cuanto un paciente termine su sesión con dolor alto, aunque tengas la app cerrada.", avisos::activarNotificaciones) }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cifra("${estado.citas.size}", if (estado.fecha == estado.hoy) "citas hoy" else "citas", ColoresPhysio.Texto, Modifier.weight(1f))
                    Cifra("${estado.cuantas(EstadoCita.ATENDIDA)}", "atendidas", ColoresPhysio.Exito, Modifier.weight(1f))
                    Cifra("${estado.seguimiento.count { it.semaforo == Semaforo.ROJO }}", "en rojo", ColoresPhysio.Alerta, Modifier.weight(1f))
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
                    item { TituloSeccion(if (estado.fecha == estado.hoy) "Tu día" else "Agenda · ${estado.etiquetaDia}") }
                    if (estado.citas.isEmpty()) {
                        item { Text("No tienes citas registradas este día.", color = ColoresPhysio.TextoSecundario) }
                    }
                    val siguiente = estado.siguiente
                    val indiceAhora = if (estado.fecha == estado.hoy) estado.citas.indexOfFirst { it.hora > horaActual }.let { if (it < 0) estado.citas.size else it } else -1
                    estado.citas.forEachIndexed { i, cita ->
                        if (i == indiceAhora && estado.citas.isNotEmpty()) item(key = "ahora") { MarcaAhora(horaActual) }
                        item(key = cita.id) {
                            FilaLineaDeTiempo(
                                cita = cita,
                                destacada = cita.id == siguiente?.id,
                                ultima = i == estado.citas.lastIndex,
                                puedeMarcar = estado.puedeMarcarAsistencia,
                                procesando = estado.procesando == cita.id,
                                alAtendida = { viewModel.marcarAsistencia(cita, llego = true) },
                                alNoAsistio = { viewModel.marcarAsistencia(cita, llego = false) },
                            )
                        }
                    }
                    if (indiceAhora == estado.citas.size && estado.citas.isNotEmpty()) item(key = "ahora") { MarcaAhora(horaActual) }

                    // Seguimiento remoto: lo que los pacientes registraron en casa (GET /sesiones).
                    item { TituloSeccion("Desde casa", alPulsar = alAbrirPacientes) }
                    when {
                        estado.errorSeguimiento != null -> item { Text(estado.errorSeguimiento ?: "", color = ColoresPhysio.TextoSecundario) }
                        estado.seguimiento.isEmpty() -> item { Text("Tus pacientes todavía no envían sesiones desde casa.", color = ColoresPhysio.TextoSecundario) }
                        else -> item {
                            LazyRow(contentPadding = PaddingValues(end = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(estado.seguimiento, key = { "sesion-${it.id}" }) { TarjetaSeguimiento(it, Modifier.animateItem()) }
                            }
                        }
                    }

                    if (estado.escaleras.isNotEmpty()) {
                        item { TituloSeccion("Pruebas de escaleras") }
                        items(estado.escaleras.take(6), key = { "escaleras-${it.id}" }) { TarjetaEscaleras(it) }
                    }
                }
            }
        }
    }
}

private fun saludo(hora: String): String = when (hora.take(2).toIntOrNull() ?: 12) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

/** «Ft. Carlos Ortega Luna» → «Carlos». */
private fun primerNombre(nombre: String): String =
    nombre.split(' ').firstOrNull { it.isNotBlank() && !it.endsWith(".") } ?: nombre

@Composable
private fun Cifra(valor: String, texto: String, color: Color, modifier: Modifier) {
    TarjetaSuave(modifier) {
        Text(valor, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(texto, color = ColoresPhysio.TextoSecundario, fontSize = 12.sp)
    }
}

/** Línea violeta con la hora actual entre las citas de hoy. */
@Composable
private fun MarcaAhora(hora: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Ahora $hora",
            Modifier.clip(RoundedCornerShape(50)).background(ColoresPhysio.Violeta).padding(horizontal = 9.dp, vertical = 3.dp),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(Modifier.weight(1f).height(2.dp).background(ColoresPhysio.Violeta))
    }
}

/** Una cita en la línea de tiempo: hora, riel con punto y tarjeta (la siguiente, con sus botones). */
@Composable
private fun FilaLineaDeTiempo(
    cita: Cita,
    destacada: Boolean,
    ultima: Boolean,
    puedeMarcar: Boolean,
    procesando: Boolean,
    alAtendida: () -> Unit,
    alNoAsistio: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            cita.hora,
            Modifier.width(46.dp).padding(top = 14.dp),
            color = if (destacada) ColoresPhysio.Lila else ColoresPhysio.TextoSecundario,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(Modifier.width(12.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.padding(top = 18.dp).size(12.dp).clip(CircleShape).background(
                    when {
                        cita.estado == EstadoCita.ATENDIDA -> ColoresPhysio.Exito
                        destacada -> ColoresPhysio.Violeta
                        else -> ColoresPhysio.BordeFuerte
                    },
                ),
            )
            if (!ultima) Box(Modifier.weight(1f).width(2.dp).background(ColoresPhysio.Borde))
        }
        Box(Modifier.weight(1f).padding(bottom = 8.dp)) {
            TarjetaSuave(borde = if (destacada) ColoresPhysio.Lila else ColoresPhysio.Borde) {
                // El nombre y el motivo usan todo el ancho; el estado va debajo para no cortarlos.
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(cita.paciente, color = ColoresPhysio.Texto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${cita.motivo} · ${cita.duracionMinutos} min", color = ColoresPhysio.TextoSecundario, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (destacada && cita.abierta) Chip("Siguiente", color = ColoresPhysio.Lila, relleno = ColoresPhysio.TinteVioleta) else Etiqueta(cita.estado.etiqueta, cita.estado.color)
                cita.nota?.let { Text(it, color = ColoresPhysio.TextoTenue, fontSize = 12.sp) }
                if (puedeMarcar && cita.abierta && destacada) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonPildora("Atendida", alAtendida, Modifier.weight(1f), icono = null, violeta = true, habilitado = !procesando, compacta = true)
                        BotonPildora("No asistió", alNoAsistio, Modifier.weight(1f), icono = null, habilitado = !procesando, compacta = true)
                    }
                } else if (puedeMarcar && cita.abierta) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BotonEnlace("Atendida", alAtendida, habilitado = !procesando)
                        BotonEnlace("No asistió", alNoAsistio, habilitado = !procesando)
                    }
                }
            }
        }
    }
}

/** Una prueba de escaleras: cuánto subió el paciente y a qué ritmo. */
@Composable
private fun TarjetaEscaleras(prueba: PruebaEscalerasRegistrada) {
    TarjetaSuave {
        Text(prueba.paciente.ifBlank { "Paciente ${prueba.pacienteId}" }, color = ColoresPhysio.Texto, fontWeight = FontWeight.SemiBold)
        val decimas = (prueba.metrosPorMinuto * 10).toInt()
        Text(
            "${fechaIsoLegible(prueba.fecha)} · ${prueba.pisos} ${if (prueba.pisos == 1) "piso" else "pisos"} " +
                "(${prueba.alturaSubidaCm / 100}.${(prueba.alturaSubidaCm % 100) / 10} m) en ${prueba.duracionSegundos} s",
            color = ColoresPhysio.TextoSecundario,
            fontSize = 13.sp,
        )
        Etiqueta("Ritmo: ${decimas / 10}.${decimas % 10} m/min", ColoresPhysio.AzulClaro)
    }
}

/** Una sesión que el paciente hizo en casa, con su semáforo (RFN-47). */
@Composable
private fun TarjetaSeguimiento(sesion: SesionTerapia, modifier: Modifier = Modifier) {
    TarjetaSuave(modifier.width(250.dp), borde = if (sesion.semaforo == Semaforo.ROJO) ColoresPhysio.AlertaRelleno else ColoresPhysio.Borde) {
        Text(sesion.paciente.ifBlank { "Paciente ${sesion.pacienteId}" }, color = ColoresPhysio.Texto, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${fechaIsoLegible(sesion.fechaHoraInicio)} · ${sesion.porcentajeCumplimiento} % · dolor ${sesion.dolorAntes} → ${sesion.dolorDespues}",
            color = ColoresPhysio.TextoSecundario,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Etiqueta(sesion.semaforo.etiqueta, sesion.semaforo.color)
            val conCamara = sesion.ejercicios.count { it.verificadoConCamara }
            if (conCamara > 0) Etiqueta("$conCamara con cámara", ColoresPhysio.Exito)
        }
        sesion.observacion?.let {
            Text("“$it”", color = ColoresPhysio.TextoSecundario, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
