package com.terapid.physiogestion.vista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.datos.remoto.ConfiguracionApi
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.viewmodel.AdministradorViewModel
import com.terapid.physiogestion.vista.componentes.BotonEnlace
import com.terapid.physiogestion.vista.componentes.BotonPrincipal
import com.terapid.physiogestion.vista.componentes.CampoTexto
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.EncabezadoRol
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.MensajeError
import com.terapid.physiogestion.vista.componentes.MensajeInfo
import com.terapid.physiogestion.vista.componentes.TarjetaDato
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** VISTA del rol ADMINISTRADOR: resumen, alta del personal y lista de usuarios con su estado. */
@Composable
fun AdministradorPantalla(
    usuario: Usuario,
    viewModel: AdministradorViewModel,
    alCerrarSesion: () -> Unit,
    alCambiarContrasena: (() -> Unit)? = null,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { EncabezadoRol("Panel de administración", usuario, alCerrarSesion, alCambiarContrasena) }

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
                    if (viewModel.puedeAdministrar) {
                        item { FormularioAlta(viewModel) }
                    }
                    estado.aviso?.let { item { MensajeInfo(it) } }
                    item {
                        Text(
                            "Usuarios del sistema (${estado.usuarios.size})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(estado.usuarios, key = { it.id }) { u ->
                        FilaUsuario(
                            u,
                            esYo = u.id == usuario.id,
                            cambiando = estado.cambiandoId == u.id,
                            alAlternar = if (viewModel.puedeAdministrar) ({ viewModel.alternarActivo(u) }) else null,
                            alEditar = if (viewModel.puedeAdministrar) ({ viewModel.abrirEdicion(u) }) else null,
                        )
                    }
                }
            }

            item {
                Text(
                    "Conectado a ${ConfiguracionApi.urlServidor}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    DialogoEditarUsuario(viewModel)
}

/** Edición de nombre, correo y rol de una cuenta (ADM-03). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoEditarUsuario(viewModel: AdministradorViewModel) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    val u = e.editando ?: return
    AlertDialog(
        onDismissRequest = viewModel::cerrarEdicion,
        title = { Text("Editar a ${u.nombre}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto(e.edNombre, viewModel::cambiarEdNombre, "Nombre completo", habilitado = !e.guardandoEdicion, mayusculas = KeyboardCapitalization.Words)
                CampoTexto(e.edCorreo, viewModel::cambiarEdCorreo, "Correo", habilitado = !e.guardandoEdicion, teclado = KeyboardType.Email)
                if (u.rol != Rol.PACIENTE) {
                    Text("Rol", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Rol.FISIOTERAPEUTA, Rol.RECEPCIONISTA, Rol.ADMINISTRADOR).forEach { rol ->
                            FilterChip(selected = e.edRol == rol, onClick = { viewModel.cambiarEdRol(rol) }, label = { Text(rol.etiqueta, maxLines = 1) })
                        }
                    }
                }
                MensajeError(e.errorEdicion)
            }
        },
        confirmButton = { TextButton(onClick = viewModel::guardarEdicion, enabled = !e.guardandoEdicion) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = viewModel::cerrarEdicion) { Text("Cancelar") } },
    )
}

/** Alta de fisioterapeutas, recepcionistas y administradores con contraseña temporal (USR-02). */
@Composable
private fun FormularioAlta(viewModel: AdministradorViewModel) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val a = estado.alta
    TarjetaPhysio {
        if (!estado.mostrandoAlta) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Agregar personal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Los pacientes se registran solos con el código que les da recepción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = viewModel::abrirAlta, modifier = Modifier.heightIn(min = 48.dp)) { Text("Agregar") }
            }
            return@TarjetaPhysio
        }
        val creada = a.creada
        if (creada != null) {
            Text("Cuenta creada para ${creada.usuario.nombre}", fontWeight = FontWeight.SemiBold)
            Text("Usuario: ${creada.usuario.usuario}")
            Text("Contraseña temporal:", style = MaterialTheme.typography.bodySmall)
            Text(creada.contrasenaTemporal, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = ColoresPhysio.Turquesa)
            Text(
                "Entrégala en persona. Solo se muestra esta vez; al entrar, la app le pedirá crear su propia contraseña.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BotonEnlace("Listo", viewModel::cerrarAlta)
            return@TarjetaPhysio
        }
        Text("Agregar personal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Rol.FISIOTERAPEUTA, Rol.RECEPCIONISTA, Rol.ADMINISTRADOR).forEach { rol ->
                FilterChip(
                    selected = a.rol == rol,
                    onClick = { viewModel.cambiarRol(rol) },
                    label = { Text(rol.etiqueta) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        CampoTexto(a.nombre, viewModel::cambiarNombre, "Nombre completo", habilitado = !a.creando, mayusculas = KeyboardCapitalization.Words)
        CampoTexto(a.usuario, viewModel::cambiarUsuario, "Usuario", habilitado = !a.creando, teclado = KeyboardType.Ascii)
        CampoTexto(
            a.correo, viewModel::cambiarCorreo, "Correo", habilitado = !a.creando, teclado = KeyboardType.Email,
            accion = ImeAction.Done, alTerminar = viewModel::crearUsuario,
        )
        MensajeError(a.error)
        BotonPrincipal("Crear cuenta", a.creando, viewModel::crearUsuario)
        BotonEnlace("Cancelar", viewModel::cerrarAlta, habilitado = !a.creando)
    }
}

@Composable
private fun FilaUsuario(u: Usuario, esYo: Boolean, cambiando: Boolean, alAlternar: (() -> Unit)?, alEditar: (() -> Unit)?) {
    TarjetaPhysio(borde = if (u.activo) MaterialTheme.colorScheme.outline else ColoresPhysio.Alerta) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(u.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("@${u.usuario}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                u.correo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Etiqueta(u.rol.etiqueta, u.rol.color)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!u.activo) Etiqueta("Desactivada", ColoresPhysio.Alerta)
            if (!u.correoVerificado) Etiqueta("Correo sin confirmar", ColoresPhysio.Pendiente)
            if (esYo) Etiqueta("Tú", ColoresPhysio.AzulClaro)
            Column(Modifier.weight(1f)) {}
            if (alEditar != null) BotonEnlace("Editar", alEditar, habilitado = !cambiando)
            if (alAlternar != null && !esYo) {
                BotonEnlace(if (u.activo) "Desactivar" else "Activar", alAlternar, habilitado = !cambiando)
            }
        }
    }
}
