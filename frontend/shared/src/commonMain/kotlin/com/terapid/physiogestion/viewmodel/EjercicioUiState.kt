package com.terapid.physiogestion.viewmodel

import com.terapid.physiogestion.modelo.Ejercicio
import com.terapid.physiogestion.modelo.MetodoMedicion
import com.terapid.physiogestion.servicio.movimiento.RangoArticular
import com.terapid.physiogestion.servicio.postura.AvisoCamara
import com.terapid.physiogestion.servicio.postura.PuntoCuerpo
import com.terapid.physiogestion.servicio.postura.ResultadoVerificacion

/** Fase en la que está la medición (las cuatro fases de la gestión de sensores). */
enum class FaseMedicion {
    /** Habilitación: se revisa que haya sensores y se muestran las instrucciones. */
    PREPARADO,

    /** Calibración: el paciente mantiene el teléfono quieto mientras se mide la postura inicial. */
    CALIBRANDO,

    /** Obtención y procesamiento: se cuentan repeticiones y se mide el ángulo. */
    MIDIENDO,

    /** Terminó: se muestra el resumen y se guarda. */
    TERMINADO,
}

/**
 * ESTADO VOLÁTIL de la pantalla de medición. Vive en memoria mientras la
 * pantalla existe; nada de esto se escribe en disco durante el ejercicio:
 * se guarda una sola vez, al terminar, y como resumen.
 */
data class EjercicioUiState(
    val ejercicio: Ejercicio? = null,
    val fase: FaseMedicion = FaseMedicion.PREPARADO,
    val repeticiones: Int = 0,
    val anguloActual: Float = 0f,
    val anguloMaximo: Float = 0f,
    val rango: RangoArticular = RangoArticular.ACEPTABLE,
    /** false en un dispositivo sin acelerómetro (por ejemplo, la computadora): se cuenta a mano. */
    val hayAcelerometro: Boolean = true,
    val hayGiroscopio: Boolean = true,
    val mensajeError: String? = null,
    val guardando: Boolean = false,
    /** null mientras no termina; true si el resumen llegó al servidor; false si quedó en el teléfono. */
    val enviado: Boolean? = null,
    val segundos: Int = 0,
    val detalleTecnico: String? = null,
    // ---- Verificación con cámara (RFN-27) ----
    /** Cómo se va a medir: sensores (teléfono sujeto al cuerpo) o cámara (teléfono apoyado). */
    val metodo: MetodoMedicion = MetodoMedicion.SENSORES,
    /** true si el ejercicio admite cámara, el dispositivo la tiene y hay una articulación que medir. */
    val camaraDisponible: Boolean = false,
    val usarFrontal: Boolean = true,
    val avisoCamara: AvisoCamara = AvisoCamara.NINGUNO,
    /** Lo vigila el acelerómetro mientras mide la cámara. */
    val telefonoQuieto: Boolean = true,
    /** Puntos del cuerpo que se dibujan resaltados (los de la articulación que se mide). */
    val puntosResaltados: Set<PuntoCuerpo> = emptySet(),
    /** Resumen de la verificación con cámara, al terminar. */
    val verificacion: ResultadoVerificacion? = null,
) {
    val usaCamara: Boolean get() = metodo == MetodoMedicion.CAMARA

    /** Sin acelerómetro y sin cámara se cuenta a mano. */
    val modoManual: Boolean get() = !hayAcelerometro && !usaCamara

    /** La cámara se muestra mientras calibra y mientras mide (sin reiniciarse entre una fase y otra). */
    val mostrarCamara: Boolean
        get() = usaCamara && (fase == FaseMedicion.CALIBRANDO || fase == FaseMedicion.MIDIENDO)

    val objetivo: Int get() = ejercicio?.repeticionesTotales ?: 0

    /** Avance de 0 a 1 para la barra de progreso. */
    val progreso: Float
        get() = if (objetivo <= 0) 0f else (repeticiones.toFloat() / objetivo).coerceIn(0f, 1f)

    /** true cuando se completaron las repeticiones (y el ángulo, si se midió con sensores). */
    val objetivoCumplido: Boolean
        get() {
            val e = ejercicio ?: return false
            val anguloOk = modoManual || e.anguloObjetivo == null || anguloMaximo >= e.anguloObjetivo
            return repeticiones >= e.repeticionesTotales && anguloOk
        }
}
