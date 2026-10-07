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
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.viewmodel.AgendaViewModel
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.EncabezadoRol
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.TarjetaCita
import com.terapid.physiogestion.vista.componentes.TarjetaDato
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta

/** VISTA del rol RECEPCIONISTA: agenda de toda la clínica, agrupada por fisioterapeuta. */
@Composable
fun RecepcionPantalla(
    usuario: Usuario,
    viewModel: AgendaViewModel,
    alCerrarSesion: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { EncabezadoRol("Agenda de la clínica", usuario, alCerrarSesion) }

            when {
                estado.cargando -> item { Cargando("Cargando agenda…") }
                estado.error != null -> item { ErrorConReintento(estado.error ?: "", viewModel::cargar) }
                else -> {
                    // Cuántas citas hay en cada estado, dos tarjetas por fila.
                    items(EstadoCita.entries.chunked(2)) { par ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            par.forEach { e ->
                                TarjetaDato(estado.cuantas(e).toString(), e.etiqueta + "s", e.color, Modifier.weight(1f))
                            }
                        }
                    }
                    estado.porFisioterapeuta.forEach { (fisioterapeuta, citas) ->
                        item {
                            Text(
                                "$fisioterapeuta · ${citas.size} citas",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(citas, key = { it.id }) { TarjetaCita(it, mostrarFisioterapeuta = false) }
                    }
                }
            }
        }
    }
}
