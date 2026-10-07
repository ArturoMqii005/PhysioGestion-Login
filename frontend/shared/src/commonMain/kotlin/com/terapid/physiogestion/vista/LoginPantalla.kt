package com.terapid.physiogestion.vista

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.viewmodel.LoginViewModel
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/**
 * VISTA: inicio de sesión. Solo dibuja el estado del LoginViewModel y le
 * pasa lo que escribe el usuario. Las credenciales las valida el backend.
 */
@Composable
fun LoginPantalla(viewModel: LoginViewModel) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Marca
                Box(
                    Modifier.size(64.dp).background(ColoresPhysio.Turquesa, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("PG", color = ColoresPhysio.Fondo, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PhysioGestion", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Clínica Terapid By Farmacias",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TarjetaPhysio {
                    Text("Inicia sesión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = estado.usuario,
                        onValueChange = viewModel::cambiarUsuario,
                        label = { Text("Usuario") },
                        singleLine = true,
                        enabled = !estado.cargando,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = estado.contrasena,
                        onValueChange = viewModel::cambiarContrasena,
                        label = { Text("Contraseña") },
                        singleLine = true,
                        enabled = !estado.cargando,
                        visualTransformation = if (estado.mostrarContrasena) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.iniciarSesion() }),
                        trailingIcon = {
                            TextButton(onClick = viewModel::alternarMostrarContrasena) {
                                Text(if (estado.mostrarContrasena) "Ocultar" else "Mostrar")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    estado.error?.let { Text(it, color = ColoresPhysio.Alerta, style = MaterialTheme.typography.bodyMedium) }
                    Button(
                        onClick = viewModel::iniciarSesion,
                        enabled = !estado.cargando,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColoresPhysio.Turquesa),
                    ) {
                        if (estado.cargando) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Iniciar sesión", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Servidor (etapa local): permite apuntar a la IP de la computadora desde un celular físico.
                if (estado.editandoServidor) {
                    OutlinedTextField(
                        value = estado.urlServidor,
                        onValueChange = viewModel::cambiarServidor,
                        label = { Text("Dirección del servidor") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = viewModel::alternarEdicionServidor, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        if (estado.editandoServidor) "Listo" else "Servidor: ${estado.urlServidor} · Cambiar",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
