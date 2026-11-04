package com.terapid.physiogestion.plataforma.camara

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.terapid.physiogestion.plataforma.ContextoAndroid
import com.terapid.physiogestion.servicio.postura.PuntoCuerpo
import com.terapid.physiogestion.servicio.postura.PuntoDetectado
import com.terapid.physiogestion.servicio.postura.Postura
import com.terapid.physiogestion.vista.tema.ColoresPhysio
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Implementación ANDROID de la verificación con cámara.
 *
 *  - CameraX entrega dos flujos de la misma cámara: la vista previa (lo que
 *    ve el paciente) y el análisis (un cuadro a la vez para el detector).
 *  - ML Kit Pose Detection encuentra 33 puntos del cuerpo en cada cuadro,
 *    DENTRO del teléfono: el video nunca sale del dispositivo ni se guarda.
 *  - Encima de la vista previa se dibuja el esqueleto detectado.
 */
actual val camaraPosturaDisponible: Boolean
    get() = ContextoAndroid.aplicacion.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

/** Puntos de ML Kit que usa la app (ML Kit los nombra desde el punto de vista de la persona). */
private val PUNTOS_MLKIT = mapOf(
    PuntoCuerpo.HOMBRO_IZQ to PoseLandmark.LEFT_SHOULDER,
    PuntoCuerpo.HOMBRO_DER to PoseLandmark.RIGHT_SHOULDER,
    PuntoCuerpo.CADERA_IZQ to PoseLandmark.LEFT_HIP,
    PuntoCuerpo.CADERA_DER to PoseLandmark.RIGHT_HIP,
    PuntoCuerpo.RODILLA_IZQ to PoseLandmark.LEFT_KNEE,
    PuntoCuerpo.RODILLA_DER to PoseLandmark.RIGHT_KNEE,
    PuntoCuerpo.TOBILLO_IZQ to PoseLandmark.LEFT_ANKLE,
    PuntoCuerpo.TOBILLO_DER to PoseLandmark.RIGHT_ANKLE,
    PuntoCuerpo.PUNTA_PIE_IZQ to PoseLandmark.LEFT_FOOT_INDEX,
    PuntoCuerpo.PUNTA_PIE_DER to PoseLandmark.RIGHT_FOOT_INDEX,
)

/** Segmentos del esqueleto que se dibujan. */
private val HUESOS = listOf(
    PuntoCuerpo.HOMBRO_IZQ to PuntoCuerpo.HOMBRO_DER,
    PuntoCuerpo.CADERA_IZQ to PuntoCuerpo.CADERA_DER,
    PuntoCuerpo.HOMBRO_IZQ to PuntoCuerpo.CADERA_IZQ,
    PuntoCuerpo.HOMBRO_DER to PuntoCuerpo.CADERA_DER,
    PuntoCuerpo.CADERA_IZQ to PuntoCuerpo.RODILLA_IZQ,
    PuntoCuerpo.CADERA_DER to PuntoCuerpo.RODILLA_DER,
    PuntoCuerpo.RODILLA_IZQ to PuntoCuerpo.TOBILLO_IZQ,
    PuntoCuerpo.RODILLA_DER to PuntoCuerpo.TOBILLO_DER,
    PuntoCuerpo.TOBILLO_IZQ to PuntoCuerpo.PUNTA_PIE_IZQ,
    PuntoCuerpo.TOBILLO_DER to PuntoCuerpo.PUNTA_PIE_DER,
)

private const val CONFIANZA_DIBUJO = 0.5f

@Composable
actual fun VistaCamaraPostura(
    modifier: Modifier,
    usarFrontal: Boolean,
    puntosResaltados: Set<PuntoCuerpo>,
    alDetectar: (Postura) -> Unit,
) {
    val contexto = LocalContext.current
    val dueno = LocalLifecycleOwner.current
    val alDetectarActual by rememberUpdatedState(alDetectar)
    var ultima by remember { mutableStateOf<Postura?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val permitido = ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    if (!permitido) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Sin permiso de cámara. Actívalo en Perfil o en los ajustes del teléfono.", Modifier.padding(16.dp))
        }
        return
    }

    // El paciente está lejos y no toca el teléfono: la pantalla no debe apagarse.
    val vista = LocalView.current
    DisposableEffect(vista) {
        vista.keepScreenOn = true
        onDispose { vista.keepScreenOn = false }
    }

    val vistaPrevia = remember {
        PreviewView(contexto).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    // STREAM_MODE: aprovecha el cuadro anterior para seguir a la persona y responder más rápido.
    val detector = remember {
        PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    }
    val ejecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            detector.close()
            ejecutor.shutdown()
        }
    }

    DisposableEffect(usarFrontal, dueno) {
        var proveedor: ProcessCameraProvider? = null
        var activo = true
        val futuro = ProcessCameraProvider.getInstance(contexto)
        futuro.addListener({
            if (!activo) return@addListener
            try {
                val p = futuro.get()
                proveedor = p
                // Misma proporción (4:3) en vista previa y análisis: así el esqueleto cae encima de la persona.
                val proporcion = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val previa = Preview.Builder().setResolutionSelector(proporcion).build()
                previa.setSurfaceProvider(vistaPrevia.surfaceProvider)
                val analisis = ImageAnalysis.Builder()
                    .setResolutionSelector(proporcion)
                    // Si el detector va lento se descartan cuadros viejos: siempre se analiza el más reciente.
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analisis.setAnalyzer(ejecutor) { imagen -> analizar(imagen, detector) { postura ->
                    ultima = postura
                    alDetectarActual(postura)
                } }
                val pedida = if (usarFrontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val camara = if (p.hasCamera(pedida)) pedida else CameraSelector.DEFAULT_BACK_CAMERA
                p.unbindAll()
                p.bindToLifecycle(dueno, camara, previa, analisis)
                error = null
            } catch (e: Exception) {
                error = "No se pudo abrir la cámara: ${e.message ?: e::class.simpleName}"
            }
        }, ContextCompat.getMainExecutor(contexto))
        onDispose {
            activo = false
            proveedor?.unbindAll()
            ultima = null
        }
    }

    Box(modifier) {
        AndroidView(factory = { vistaPrevia }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            ultima?.let { dibujarEsqueleto(it, espejo = usarFrontal, resaltados = puntosResaltados) }
        }
        error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(16.dp))
        }
    }
}

/** Manda un cuadro a ML Kit y cierra la imagen al terminar (si no, CameraX deja de entregar cuadros). */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analizar(imagen: ImageProxy, detector: PoseDetector, alResultado: (Postura) -> Unit) {
    val media = imagen.image
    if (media == null) {
        imagen.close()
        return
    }
    val rotacion = imagen.imageInfo.rotationDegrees
    // ML Kit devuelve coordenadas de la imagen ya enderezada.
    val ancho = if (rotacion % 180 == 0) imagen.width else imagen.height
    val alto = if (rotacion % 180 == 0) imagen.height else imagen.width
    val marca = SystemClock.elapsedRealtime()
    detector.process(InputImage.fromMediaImage(media, rotacion))
        .addOnSuccessListener { pose -> alResultado(pose.aPostura(marca, ancho, alto)) }
        .addOnFailureListener { alResultado(Postura(emptyMap(), marca, ancho, alto)) }
        .addOnCompleteListener { imagen.close() }
}

private fun Pose.aPostura(marca: Long, ancho: Int, alto: Int): Postura {
    val puntos = buildMap {
        for ((punto, tipo) in PUNTOS_MLKIT) {
            val marcaMlkit = getPoseLandmark(tipo) ?: continue
            put(punto, PuntoDetectado(marcaMlkit.position.x, marcaMlkit.position.y, marcaMlkit.inFrameLikelihood))
        }
    }
    return Postura(puntos, marca, ancho, alto)
}

/**
 * Dibuja el esqueleto sobre la vista previa. La vista previa recorta la
 * imagen para llenar el recuadro (FILL_CENTER), así que se aplica la misma
 * escala y el mismo desplazamiento; con la cámara frontal la vista previa es
 * un espejo, y el dibujo también.
 */
private fun DrawScope.dibujarEsqueleto(postura: Postura, espejo: Boolean, resaltados: Set<PuntoCuerpo>) {
    if (postura.ancho <= 0 || postura.alto <= 0 || !postura.hayPersona) return
    val escala = maxOf(size.width / postura.ancho, size.height / postura.alto)
    val dx = (size.width - postura.ancho * escala) / 2f
    val dy = (size.height - postura.alto * escala) / 2f
    fun enPantalla(p: PuntoDetectado) = Offset(
        dx + (if (espejo) postura.ancho - p.x else p.x) * escala,
        dy + p.y * escala,
    )
    val grosor = 6.dp.toPx()
    for ((a, b) in HUESOS) {
        val pa = postura.puntos[a] ?: continue
        val pb = postura.puntos[b] ?: continue
        if (pa.confianza < CONFIANZA_DIBUJO || pb.confianza < CONFIANZA_DIBUJO) continue
        val color = if (a in resaltados && b in resaltados) ColoresPhysio.Lila else Color.White.copy(alpha = 0.75f)
        drawLine(color, enPantalla(pa), enPantalla(pb), strokeWidth = grosor, cap = StrokeCap.Round)
    }
    for ((punto, p) in postura.puntos) {
        if (p.confianza < CONFIANZA_DIBUJO) continue
        val resaltado = punto in resaltados
        drawCircle(
            color = if (resaltado) ColoresPhysio.Lila else Color.White,
            radius = (if (resaltado) 8.dp else 5.dp).toPx(),
            center = enPantalla(p),
        )
    }
}
