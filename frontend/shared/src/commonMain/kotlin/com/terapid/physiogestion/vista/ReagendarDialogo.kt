package com.terapid.physiogestion.vista

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.datos.ClinicaRepositorio
import com.terapid.physiogestion.modelo.Cita
import com.terapid.physiogestion.servicio.ReglasAgenda
import com.terapid.physiogestion.viewmodel.ModoReagenda
import com.terapid.physiogestion.viewmodel.ReagendaControlador
import com.terapid.physiogestion.viewmodel.ReagendaUiState
import com.terapid.physiogestion.vista.componentes.BotonPildora
import com.terapid.physiogestion.vista.componentes.ExpresionXochi
import com.terapid.physiogestion.vista.componentes.XochiDice
import com.terapid.physiogestion.vista.componentes.tocable
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * Diálogo para reagendar una cita. Lo usan el paciente (en «Tus citas») y el
 * fisioterapeuta («No puedo atender» en su agenda). [alCambiar] recarga la lista.
 */
@Composable
fun DialogoReagendar(
    clinica: ClinicaRepositorio,
    cita: Cita,
    esFisioterapeuta: Boolean,
    hoy: String,
    alCerrar: () -> Unit,
    alCambiar: () -> Unit,
) {
    val alcance = rememberCoroutineScope()
    val controlador = remember(cita.id) { ReagendaControlador(clinica, cita, esFisioterapeuta, hoy, alcance) }
    val e by controlador.estado.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = { if (!e.enviando) alCerrar() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).widthIn(max = 460.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = ColoresPhysio.Superficie,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val listo = e.listo
                if (listo != null) {
                    XochiDice(listo, expresion = ExpresionXochi.CELEBRANDO, tamano = 84.dp)
                    BotonPildora("Entendido", alCerrar, Modifier.fillMaxWidth(), icono = null, violeta = true)
                } else {
                    ContenidoReagendar(e, controlador, hoy, alCerrar, alCambiar)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContenidoReagendar(e: ReagendaUiState, c: ReagendaControlador, hoy: String, alCerrar: () -> Unit, alCambiar: () -> Unit) {
    val titulo = if (e.esFisioterapeuta) "No puedo atender" else "Cambiar mi cita"
    Text(titulo, style = MaterialTheme.typography.headlineSmall, color = ColoresPhysio.Texto)
    Text(
        "${if (e.esFisioterapeuta) e.cita.paciente else e.cita.motivo} · ${ReglasAgenda.etiquetaDia(e.cita.fecha, hoy)} a las ${e.cita.hora}",
        color = ColoresPhysio.TextoSecundario, fontSize = 14.sp,
    )

    if (e.esFisioterapeuta) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pestana("Mover de horario", e.modo == ModoReagenda.MOVER, Modifier.weight(1f)) { c.cambiarModo(ModoReagenda.MOVER) }
            Pestana("Pasarla a otro fisio", e.modo == ModoReagenda.PASAR, Modifier.weight(1f)) { c.cambiarModo(ModoReagenda.PASAR) }
        }
    } else {
        XochiDice(
            when (e.modo) {
                ModoReagenda.SOLICITAR -> "Faltan menos de 24 horas o ya la cambiaste 2 veces. Pídele el cambio a recepción y te avisamos."
                else -> "Elige un día y una hora libre con tu fisioterapeuta. Te quedan ${e.cambiosRestantes} ${if (e.cambiosRestantes == 1) "cambio" else "cambios"}."
            },
            expresion = if (e.modo == ModoReagenda.SOLICITAR) ExpresionXochi.PENSANDO else ExpresionXochi.FELIZ,
            tamano = 72.dp,
        )
    }

    if (e.modo != ModoReagenda.PASAR) {
        Text(if (e.modo == ModoReagenda.SOLICITAR) "¿Qué día te acomoda? (opcional)" else "Día", fontWeight = FontWeight.SemiBold, color = ColoresPhysio.Texto)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            e.dias.forEach { d ->
                val elegido = d == e.fecha
                Column(
                    Modifier.size(58.dp, 64.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (elegido) ColoresPhysio.Violeta else ColoresPhysio.SuperficieAlta)
                        .tocable("Día $d", alPulsar = { c.elegirFecha(d) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(DIAS[ReglasAgenda.diaSemana(d)], color = if (elegido) Color.White else ColoresPhysio.Lila, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(d.takeLast(2), color = if (elegido) Color.White else ColoresPhysio.Texto, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Text("Hora", fontWeight = FontWeight.SemiBold, color = ColoresPhysio.Texto)
        when {
            e.cargandoHoras -> CircularProgressIndicator(Modifier.size(24.dp), color = ColoresPhysio.Lila)
            e.horas.isEmpty() -> Text("No hay horas libres ese día. Prueba con otro.", color = ColoresPhysio.Pendiente, fontSize = 14.sp)
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                e.horas.forEach { h ->
                    val elegida = h == e.hora
                    Text(
                        h,
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (elegida) ColoresPhysio.Violeta else Color.Transparent)
                            .border(1.dp, if (elegida) ColoresPhysio.Violeta else ColoresPhysio.BordeFuerte, RoundedCornerShape(50))
                            .tocable("Hora $h", alPulsar = { c.elegirHora(h) })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        color = if (elegida) Color.White else ColoresPhysio.Texto,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    } else {
        Text("¿Quién puede atenderla a la misma hora?", fontWeight = FontWeight.SemiBold, color = ColoresPhysio.Texto)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            e.fisios.forEach { f ->
                val elegido = f.id == e.fisio
                Text(
                    f.nombre,
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (elegido) ColoresPhysio.Violeta else ColoresPhysio.SuperficieAlta)
                        .tocable(f.nombre, alPulsar = { c.elegirFisio(f.id) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (elegido) Color.White else ColoresPhysio.Texto,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (e.esFisioterapeuta || e.modo == ModoReagenda.SOLICITAR) {
        OutlinedTextField(
            value = e.mensaje,
            onValueChange = c::escribir,
            label = { Text(if (e.esFisioterapeuta) "Motivo (le llega al paciente)" else "¿Por qué necesitas cambiarla?") },
            placeholder = { Text(if (e.esFisioterapeuta) "Por ejemplo: me enfermé" else "Por ejemplo: me surgió trabajo") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            maxLines = 3,
        )
    }
    e.error?.let { Text(it, color = ColoresPhysio.Pendiente, fontSize = 14.sp) }
    BotonPildora(
        when (e.modo) {
            ModoReagenda.MOVER -> if (e.esFisioterapeuta) "Mover y avisar" else "Cambiar mi cita"
            ModoReagenda.PASAR -> "Pasar y avisar"
            ModoReagenda.SOLICITAR -> "Enviar solicitud"
        },
        { c.enviar(alCambiar) },
        Modifier.fillMaxWidth(),
        icono = null,
        violeta = true,
        habilitado = e.puedeEnviar,
    )
    TextButton(onClick = alCerrar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !e.enviando) { Text("Cancelar") }
}

@Composable
private fun Pestana(texto: String, elegida: Boolean, modifier: Modifier, alElegir: () -> Unit) {
    Text(
        texto,
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (elegida) ColoresPhysio.TinteVioleta else ColoresPhysio.SuperficieAlta)
            .border(1.dp, if (elegida) ColoresPhysio.Lila else Color.Transparent, RoundedCornerShape(14.dp))
            .tocable(texto, alElegir)
            .padding(vertical = 12.dp),
        color = if (elegida) ColoresPhysio.Lila else ColoresPhysio.TextoSecundario,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private val DIAS = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")
