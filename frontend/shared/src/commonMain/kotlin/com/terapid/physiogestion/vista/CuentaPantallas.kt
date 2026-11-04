package com.terapid.physiogestion.vista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.modelo.AvisoPrivacidad
import com.terapid.physiogestion.viewmodel.AvisoPrivacidadViewModel
import com.terapid.physiogestion.viewmodel.CambiarContrasenaViewModel
import com.terapid.physiogestion.viewmodel.CampoRegistro
import com.terapid.physiogestion.viewmodel.PasoRecuperacion
import com.terapid.physiogestion.viewmodel.RecuperarContrasenaViewModel
import com.terapid.physiogestion.viewmodel.RegistroViewModel
import com.terapid.physiogestion.viewmodel.VerificarCorreoViewModel
import com.terapid.physiogestion.vista.componentes.BarraFuerza
import com.terapid.physiogestion.vista.componentes.BotonEnlace
import com.terapid.physiogestion.vista.componentes.BotonPrincipal
import com.terapid.physiogestion.vista.componentes.CampoContrasena
import com.terapid.physiogestion.vista.componentes.CampoTexto
import com.terapid.physiogestion.vista.componentes.Cargando
import com.terapid.physiogestion.vista.componentes.ErrorConReintento
import com.terapid.physiogestion.vista.componentes.MensajeError
import com.terapid.physiogestion.vista.componentes.MensajeInfo
import com.terapid.physiogestion.vista.componentes.PantallaFormulario
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/*
 * VISTAS de las cuentas: registro, confirmación del correo, recuperación y
 * restablecimiento de la contraseña, cambio de contraseña y aviso de
 * privacidad. Solo dibujan el estado de su ViewModel.
 */

private const val AYUDA_CONTRASENA = "Mínimo 8 caracteres, con letras y números."

// =========================================================================== REGISTRO (USR-01)

@Composable
fun RegistroPantalla(
    viewModel: RegistroViewModel,
    alRegresar: () -> Unit,
    alRegistrado: (correo: String) -> Unit,
) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(e.correoRegistrado) { e.correoRegistrado?.let(alRegistrado) }

    PantallaFormulario(
        titulo = "Crear mi cuenta",
        subtitulo = "Usa el código de invitación que te dieron en la recepción de la clínica.",
        alRegresar = alRegresar,
    ) {
        TarjetaPhysio {
            CampoTexto(
                e.codigo, viewModel::cambiarCodigo, "Código de invitación",
                error = e.errores[CampoRegistro.CODIGO], ayuda = "8 caracteres, por ejemplo K7QM-4R2X",
                habilitado = !e.cargando, teclado = KeyboardType.Ascii,
                mayusculas = KeyboardCapitalization.Characters,
            )
            CampoTexto(
                e.correo, viewModel::cambiarCorreo, "Correo electrónico",
                error = e.errores[CampoRegistro.CORREO], ayuda = "Ahí te llegará el código para confirmar tu cuenta.",
                habilitado = !e.cargando, teclado = KeyboardType.Email,
            )
            CampoTexto(
                e.usuario, viewModel::cambiarUsuario, "Usuario",
                error = e.errores[CampoRegistro.USUARIO], ayuda = "Con él vas a iniciar sesión (también con tu correo).",
                habilitado = !e.cargando, teclado = KeyboardType.Ascii,
            )
            CampoContrasena(
                e.contrasena, viewModel::cambiarContrasena, "Contraseña", e.mostrarContrasena, viewModel::alternarMostrar,
                error = e.errores[CampoRegistro.CONTRASENA], ayuda = AYUDA_CONTRASENA, habilitado = !e.cargando,
            )
            BarraFuerza(e.fuerza)
            CampoContrasena(
                e.confirmacion, viewModel::cambiarConfirmacion, "Confirma la contraseña", e.mostrarContrasena, viewModel::alternarMostrar,
                error = e.errores[CampoRegistro.CONFIRMACION], habilitado = !e.cargando, accion = ImeAction.Done,
            )
        }

        CasillaAviso(
            marcada = e.aceptaAviso,
            alCambiar = viewModel::cambiarAceptaAviso,
            alVerAviso = viewModel::verAviso,
            error = e.errores[CampoRegistro.AVISO],
        )

        MensajeError(e.error)
        BotonPrincipal("Crear cuenta", e.cargando, viewModel::registrar)
    }

    if (e.mostrandoAviso) {
        DialogoAviso(e.aviso, alCerrar = { viewModel.cerrarAviso(aceptar = false) }, alAceptar = { viewModel.cerrarAviso(aceptar = true) })
    }
}

@Composable
private fun CasillaAviso(marcada: Boolean, alCambiar: (Boolean) -> Unit, alVerAviso: () -> Unit, error: String?) {
    TarjetaPhysio(borde = if (error != null) ColoresPhysio.Alerta else MaterialTheme.colorScheme.outline) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = marcada, role = Role.Checkbox, onValueChange = alCambiar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = marcada, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = ColoresPhysio.Lila))
            Text(
                "Leí y acepto el aviso de privacidad. Doy mi consentimiento para el uso de mis datos de salud en mi tratamiento.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        BotonEnlace("Leer el aviso de privacidad", alVerAviso)
        MensajeError(error)
    }
}

@Composable
private fun DialogoAviso(aviso: AvisoPrivacidad?, alCerrar: () -> Unit, alAceptar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(aviso?.titulo ?: "Aviso de privacidad") },
        text = {
            if (aviso == null) {
                Cargando("Cargando aviso…")
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    aviso.secciones.forEach { s ->
                        Text(s.titulo, fontWeight = FontWeight.SemiBold)
                        Text(s.texto, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Versión ${aviso.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = alAceptar, enabled = aviso != null) { Text("Acepto") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}

// =========================================================================== VERIFICAR CORREO (USR-20)

@Composable
fun VerificarCorreoPantalla(
    viewModel: VerificarCorreoViewModel,
    alRegresar: () -> Unit,
    alTerminar: () -> Unit,
) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    PantallaFormulario(
        titulo = "Confirma tu correo",
        subtitulo = "Escribe el código de 6 dígitos que enviamos a ${e.correo}. Vence en 15 minutos.",
        alRegresar = alRegresar,
    ) {
        if (e.listo != null) {
            MensajeInfo(e.listo)
            BotonPrincipal("Iniciar sesión", cargando = false, alPulsar = alTerminar)
            return@PantallaFormulario
        }
        TarjetaPhysio {
            CampoTexto(
                e.codigo, viewModel::cambiarCodigo, "Código de 6 dígitos",
                habilitado = !e.cargando, teclado = KeyboardType.NumberPassword, accion = ImeAction.Done, alTerminar = viewModel::verificar,
            )
            MensajeError(e.error)
            BotonPrincipal("Confirmar correo", e.cargando, viewModel::verificar)
        }
        MensajeInfo(e.info)
        Text(
            "¿No te llegó? Revisa la carpeta de spam o pide otro código.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BotonEnlace(
            if (e.segundosParaReenviar > 0) "Reenviar código en ${e.segundosParaReenviar} s" else "Reenviar código",
            viewModel::reenviar,
            habilitado = e.segundosParaReenviar == 0 && !e.cargando,
        )
    }
}

// =========================================================================== RECUPERAR Y RESTABLECER (USR-10, USR-11)

@Composable
fun RecuperarContrasenaPantalla(
    viewModel: RecuperarContrasenaViewModel,
    alRegresar: () -> Unit,
) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    val subtitulo = when (e.paso) {
        PasoRecuperacion.CORREO -> "Escribe el correo de tu cuenta y te enviaremos un código para crear una contraseña nueva."
        PasoRecuperacion.CODIGO -> "Escribe el código que te llegó a ${e.correo} y tu contraseña nueva."
        PasoRecuperacion.LISTO -> null
    }
    PantallaFormulario("Restablecer contraseña", subtitulo, alRegresar) {
        when (e.paso) {
            PasoRecuperacion.CORREO -> TarjetaPhysio {
                CampoTexto(
                    e.correo, viewModel::cambiarCorreo, "Correo electrónico",
                    habilitado = !e.cargando, teclado = KeyboardType.Email, accion = ImeAction.Done, alTerminar = viewModel::enviarCodigo,
                )
                MensajeError(e.error)
                BotonPrincipal("Enviar código", e.cargando, viewModel::enviarCodigo)
            }

            PasoRecuperacion.CODIGO -> {
                MensajeInfo(e.info)
                TarjetaPhysio {
                    CampoTexto(
                        e.codigo, viewModel::cambiarCodigo, "Código de 6 dígitos",
                        habilitado = !e.cargando, teclado = KeyboardType.NumberPassword,
                    )
                    CampoContrasena(
                        e.nueva, viewModel::cambiarNueva, "Contraseña nueva", e.mostrarContrasena, viewModel::alternarMostrar,
                        ayuda = AYUDA_CONTRASENA, habilitado = !e.cargando,
                    )
                    BarraFuerza(e.fuerza)
                    CampoContrasena(
                        e.confirmacion, viewModel::cambiarConfirmacion, "Confirma la contraseña nueva", e.mostrarContrasena,
                        viewModel::alternarMostrar, habilitado = !e.cargando, accion = ImeAction.Done, alTerminar = viewModel::restablecer,
                    )
                    MensajeError(e.error)
                    BotonPrincipal("Guardar contraseña nueva", e.cargando, viewModel::restablecer)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BotonEnlace(
                        if (e.segundosParaReenviar > 0) "Reenviar en ${e.segundosParaReenviar} s" else "Reenviar código",
                        viewModel::enviarCodigo,
                        habilitado = e.segundosParaReenviar == 0 && !e.cargando,
                    )
                    BotonEnlace("Usar otro correo", viewModel::cambiarDeCorreo, habilitado = !e.cargando)
                }
            }

            PasoRecuperacion.LISTO -> {
                MensajeInfo(e.info ?: "Tu contraseña se cambió.")
                Text(
                    "Por seguridad cerramos tu sesión en todos los dispositivos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BotonPrincipal("Iniciar sesión", cargando = false, alPulsar = alRegresar)
            }
        }
    }
}

// =========================================================================== CAMBIAR CONTRASEÑA (USR-12, USR-02)

@Composable
fun CambiarContrasenaPantalla(
    viewModel: CambiarContrasenaViewModel,
    alRegresar: () -> Unit,
    alCerrarSesion: () -> Unit,
) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    PantallaFormulario(
        titulo = if (e.obligatorio) "Crea tu contraseña" else "Cambiar contraseña",
        subtitulo = if (e.obligatorio) {
            "Tu cuenta se creó con una contraseña temporal. Antes de continuar, elige una que solo tú conozcas."
        } else {
            "Al cambiarla se cerrará tu sesión en tus otros dispositivos."
        },
        alRegresar = if (e.obligatorio) null else alRegresar,
    ) {
        if (e.listo != null && !e.obligatorio) {
            MensajeInfo(e.listo)
            BotonPrincipal("Regresar", cargando = false, alPulsar = alRegresar)
            return@PantallaFormulario
        }
        TarjetaPhysio {
            CampoContrasena(
                e.actual, viewModel::cambiarActual, if (e.obligatorio) "Contraseña temporal" else "Contraseña actual",
                e.mostrarContrasena, viewModel::alternarMostrar, habilitado = !e.cargando,
            )
            CampoContrasena(
                e.nueva, viewModel::cambiarNueva, "Contraseña nueva", e.mostrarContrasena, viewModel::alternarMostrar,
                ayuda = AYUDA_CONTRASENA, habilitado = !e.cargando,
            )
            BarraFuerza(e.fuerza)
            CampoContrasena(
                e.confirmacion, viewModel::cambiarConfirmacion, "Confirma la contraseña nueva", e.mostrarContrasena,
                viewModel::alternarMostrar, habilitado = !e.cargando, accion = ImeAction.Done, alTerminar = viewModel::guardar,
            )
            MensajeError(e.error)
            BotonPrincipal("Guardar", e.cargando, viewModel::guardar)
        }
        if (e.obligatorio) BotonEnlace("Cerrar sesión", alCerrarSesion)
    }
}

// =========================================================================== AVISO DE PRIVACIDAD (USR-21)

@Composable
fun AvisoPrivacidadPantalla(
    viewModel: AvisoPrivacidadViewModel,
    alRechazar: () -> Unit,
) {
    val e by viewModel.estado.collectAsStateWithLifecycle()
    PantallaFormulario(
        titulo = "Tu privacidad",
        subtitulo = "PhysioGestion guarda información de tu salud. Antes de continuar, lee cómo la usamos.",
        alRegresar = null,
    ) {
        when {
            e.cargando -> Cargando("Cargando aviso de privacidad…")
            e.aviso == null -> ErrorConReintento(e.error ?: "No se pudo cargar el aviso.", viewModel::cargar)
            else -> {
                val aviso = e.aviso!!
                TarjetaPhysio {
                    Text(aviso.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    aviso.secciones.forEach { s ->
                        Text(s.titulo, fontWeight = FontWeight.SemiBold, color = ColoresPhysio.Lila)
                        Text(s.texto, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Versión ${aviso.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TarjetaPhysio {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(value = e.leido, role = Role.Checkbox, onValueChange = viewModel::cambiarLeido),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = e.leido, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = ColoresPhysio.Lila))
                        Text(
                            "Leí el aviso y doy mi consentimiento expreso para el tratamiento de mis datos de salud.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                MensajeError(e.error)
                BotonPrincipal("Acepto", e.enviando, viewModel::aceptar, habilitado = e.leido)
                OutlinedButton(onClick = alRechazar, enabled = !e.enviando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("No acepto · cerrar sesión")
                }
            }
        }
    }
}
