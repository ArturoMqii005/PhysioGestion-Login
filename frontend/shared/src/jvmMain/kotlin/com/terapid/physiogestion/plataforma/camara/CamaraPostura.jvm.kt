package com.terapid.physiogestion.plataforma.camara

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.terapid.physiogestion.servicio.postura.PuntoCuerpo
import com.terapid.physiogestion.servicio.postura.Postura

/** ESCRITORIO: la verificación con cámara solo existe en la app de Android. */
actual val camaraPosturaDisponible: Boolean = false

@Composable
actual fun VistaCamaraPostura(
    modifier: Modifier,
    usarFrontal: Boolean,
    puntosResaltados: Set<PuntoCuerpo>,
    alDetectar: (Postura) -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("La verificación con cámara está disponible en la app de Android.")
    }
}
