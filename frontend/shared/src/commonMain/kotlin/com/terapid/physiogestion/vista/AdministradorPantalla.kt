package com.terapid.physiogestion.vista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.datos.remoto.ConfiguracionApi
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.viewmodel.AdministradorViewModel
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.EncabezadoRol
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.TarjetaDato
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta

/** VISTA del rol ADMINISTRADOR: resumen y lista de usuarios del sistema. */
@Composable
fun AdministradorPantalla(
    usuario: Usuario,
    viewModel: AdministradorViewModel,
    alCerrarSesion: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { EncabezadoRol("Panel de administración", usuario, alCerrarSesion) }

            when {
                estado.cargando -> item { Cargando("Cargando usuarios…") }
                estado.error != null -> item { ErrorConReintento(estado.error ?: "", viewModel::cargar) }
                else -> {
                    item { Text("Usuarios por perfil", style = MaterialTheme.typography.titleMedium) }
                    // Tarjetas de resumen, dos por fila.
                    items(Rol.entries.chunked(2)) { par ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            par.forEach { rol ->
                                TarjetaDato(
                                    valor = (estado.porRol[rol] ?: 0).toString(),
                                    descripcion = rol.etiqueta,
                                    color = rol.color,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            "Usuarios del sistema (${estado.usuarios.size})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(estado.usuarios, key = { it.id }) { FilaUsuario(it) }
                }
            }

            item {
                Text(
                    "Conectado a ${ConfiguracionApi.urlServidor} · credenciales de usuarios.json",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FilaUsuario(u: Usuario) {
    TarjetaPhysio {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(u.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("@${u.usuario}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Etiqueta(u.rol.etiqueta, u.rol.color)
        }
    }
}
