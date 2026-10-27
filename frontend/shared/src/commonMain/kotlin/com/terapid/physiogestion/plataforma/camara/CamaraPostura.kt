package com.terapid.physiogestion.plataforma.camara

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.terapid.physiogestion.servicio.postura.PuntoCuerpo
import com.terapid.physiogestion.servicio.postura.Postura

/**
 * true si esta plataforma puede verificar ejercicios con la cámara.
 * Android: CameraX + ML Kit. Escritorio e iOS todavía no.
 */
expect val camaraPosturaDisponible: Boolean

/**
 * Vista previa de la cámara con el esqueleto detectado encima.
 *
 * Cada cuadro analizado llega a [alDetectar] como una [Postura] (vacía si no
 * se vio a nadie), siempre en el hilo principal. La vista no cuenta nada:
 * eso lo hace el AnalizadorPostura a través del ViewModel.
 *
 * Mientras está en pantalla mantiene la pantalla encendida, porque el
 * paciente la ve desde lejos y no toca el teléfono.
 */
@Composable
expect fun VistaCamaraPostura(
    modifier: Modifier,
    usarFrontal: Boolean,
    /** Puntos de la articulación que se mide: se dibujan resaltados. */
    puntosResaltados: Set<PuntoCuerpo>,
    alDetectar: (Postura) -> Unit,
)
