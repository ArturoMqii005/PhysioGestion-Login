package com.terapid.physiogestion.vista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terapid.physiogestion.datos.local.LIMITE_CACHE_VIDEOS_MB
import com.terapid.physiogestion.datos.local.formatearTamano
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.plataforma.formatearFechaHora
import com.terapid.physiogestion.viewmodel.PerfilViewModel
import com.terapid.physiogestion.vista.componentes.EncabezadoSecundario
import com.terapid.physiogestion.vista.componentes.Etiqueta
import com.terapid.physiogestion.vista.componentes.TarjetaPhysio
import com.terapid.physiogestion.vista.componentes.color
import com.terapid.physiogestion.vista.componentes.etiqueta
import com.terapid.physiogestion.vista.tema.ColoresPhysio

/** VISTA del perfil: cuenta, variables de entorno, almacenamiento local y permisos. */
@Composable
fun PerfilPantalla(
    usuario: Usuario,
    viewModel: PerfilViewModel,
    alRegresar: () -> Unit,
    alCerrarSesion: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EncabezadoSecundario("Mi perfil", alRegresar)

            TarjetaPhysio {
                Text(usuario.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("@${usuario.usuario}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Etiqueta(usuario.rol.etiqueta, usuario.rol.color)
                Text(
                    "Racha actual: ${estado.racha} " + if (estado.racha == 1) "día seguido" else "días seguidos",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Seccion("Permisos") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Verificación con cámara")
                        Text(
                            "Los ejercicios marcados como verificables podrán revisarse con la cámara. " +
                                "Por ahora solo se concede el permiso; las imágenes nunca salen del teléfono.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = estado.camaraActiva,
                        onCheckedChange = viewModel::cambiarVerificacionCamara,
                        colors = SwitchDefaults.colors(checkedTrackColor = ColoresPhysio.Turquesa),
                    )
                }
            }

            Seccion("Guardado en este teléfono") {
                Dato(
                    "Base de datos (SQLite, versión ${estado.versionEsquema})",
                    plural(estado.sesionesGuardadas, "sesión", "sesiones") + " · " +
                        plural(estado.mediciones, "medición", "mediciones"),
                )
                Dato("Pendientes de enviar", "${estado.pendientesEnvio}")
                Dato(
                    "Última sincronización",
                    estado.ultimaSincronizacion?.let(::formatearFechaHora) ?: "todavía no",
                )
                Dato(
                    "Caché de videos",
                    "${formatearTamano(estado.cacheBytes)} de $LIMITE_CACHE_VIDEOS_MB MB · " + plural(estado.cacheVideos, "archivo", "archivos"),
                )
                Text(estado.carpetaCache, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = viewModel::limpiarCache, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Vaciar caché de videos")
                }
                Text(
                    "Preferencias: racha, servidor y permisos. En Android, el token de acceso se guarda cifrado con el Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Seccion("Variables de entorno") {
                Dato("Ambiente", estado.ambiente)
                Dato("Servidor", estado.servidor)
                Dato("Plataforma", estado.plataforma)
                Dato("Versión de la app", estado.version)
            }

            estado.aviso?.let { Text(it, color = ColoresPhysio.Turquesa) }

            OutlinedButton(onClick = alCerrarSesion, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cerrar sesión")
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    TarjetaPhysio {
        Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        contenido()
    }
}

@Composable
private fun Dato(nombre: String, valor: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(nombre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** 1 -> "1 sesión", 3 -> "3 sesiones" */
private fun plural(n: Int, singular: String, plural: String) = "$n " + if (n == 1) singular else plural
