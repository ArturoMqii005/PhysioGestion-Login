package com.terapid.physiogestion.vista.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/*
 * Piezas de los formularios de cuenta (registro, recuperación, cambio de
 * contraseña). Todas respetan el mínimo de 48 dp para tocar (RNF-13).
 */

/** Pantalla de formulario: regresar, título, subtítulo y contenido con desplazamiento. */
@Composable
fun PantallaFormulario(
    titulo: String,
    subtitulo: String?,
    alRegresar: (() -> Unit)?,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (alRegresar != null) {
                    EncabezadoSecundario(titulo, alRegresar, subtitulo)
                } else {
                    Text("PhysioGestion", color = ColoresPhysio.Turquesa, style = MaterialTheme.typography.labelLarge)
                    Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    subtitulo?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                contenido()
            }
        }
    }
}

@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    ayuda: String? = null,
    habilitado: Boolean = true,
    teclado: KeyboardType = KeyboardType.Text,
    mayusculas: KeyboardCapitalization = KeyboardCapitalization.None,
    accion: ImeAction = ImeAction.Next,
    alTerminar: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        enabled = habilitado,
        isError = error != null,
        supportingText = (error ?: ayuda)?.let { texto -> { Text(texto) } },
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = accion, capitalization = mayusculas, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { alTerminar?.invoke() }),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Campo de contraseña con «Mostrar / Ocultar». */
@Composable
fun CampoContrasena(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    mostrar: Boolean,
    alAlternar: () -> Unit,
    error: String? = null,
    ayuda: String? = null,
    habilitado: Boolean = true,
    accion: ImeAction = ImeAction.Next,
    alTerminar: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        enabled = habilitado,
        isError = error != null,
        supportingText = (error ?: ayuda)?.let { texto -> { Text(texto) } },
        visualTransformation = if (mostrar) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = accion, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { alTerminar?.invoke() }),
        trailingIcon = {
            TextButton(onClick = alAlternar) { Text(if (mostrar) "Ocultar" else "Mostrar") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Barra de 4 segmentos que indica qué tan segura es la contraseña nueva. */
@Composable
fun BarraFuerza(fuerza: Int) {
    val (texto, color) = when (fuerza) {
        0 -> "" to MaterialTheme.colorScheme.outline
        1 -> "Débil" to ColoresPhysio.Alerta
        2 -> "Aceptable" to ColoresPhysio.Pendiente
        3 -> "Buena" to ColoresPhysio.Exito
        else -> "Muy buena" to ColoresPhysio.Exito
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics { contentDescription = "Seguridad de la contraseña: ${texto.ifEmpty { "sin escribir" }}" },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(4) { i ->
                Box(
                    Modifier.weight(1f).height(6.dp)
                        .background(if (i < fuerza) color else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                )
            }
        }
        if (texto.isNotEmpty()) Text(texto, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Botón principal del formulario, con indicador mientras carga. */
@Composable
fun BotonPrincipal(texto: String, cargando: Boolean, alPulsar: () -> Unit, habilitado: Boolean = true) {
    Button(
        onClick = alPulsar,
        enabled = !cargando && habilitado,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
    ) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(texto, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MensajeError(texto: String?) {
    if (texto == null) return
    Text(texto, color = ColoresPhysio.Alerta, style = MaterialTheme.typography.bodyMedium)
}

/** Aviso informativo (por ejemplo, «Te enviamos un código…»). */
@Composable
fun MensajeInfo(texto: String?) {
    if (texto == null) return
    TarjetaPhysio(borde = ColoresPhysio.Turquesa) {
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Botón de texto secundario (enlaces como «¿Olvidaste tu contraseña?»). */
@Composable
fun BotonEnlace(texto: String, alPulsar: () -> Unit, habilitado: Boolean = true, modifier: Modifier = Modifier) {
    TextButton(onClick = alPulsar, enabled = habilitado, modifier = modifier.heightIn(min = 48.dp)) {
        Text(texto, color = if (habilitado) ColoresPhysio.Turquesa else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
