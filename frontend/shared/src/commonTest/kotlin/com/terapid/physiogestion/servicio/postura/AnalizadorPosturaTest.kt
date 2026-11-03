package com.terapid.physiogestion.servicio.postura

import com.terapid.physiogestion.datos.sensores.LecturaEje
import com.terapid.physiogestion.modelo.MetodoMedicion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Persona simulada vista de lado: tobillo fijo, espinilla y muslo de 200 px.
 * Con flexión f, la espinilla se inclina f/2 hacia adelante y el muslo f/2
 * hacia atrás, así el ángulo interno de la rodilla es 180 − f.
 */
private fun persona(flexion: Float, marca: Long, confianza: Float = 0.95f, ladoVisible: Lado = Lado.IZQUIERDO): Postura {
    val mitad = flexion / 2f * PI.toFloat() / 180f
    val tobillo = PuntoDetectado(300f, 900f, confianza)
    val rodilla = PuntoDetectado(tobillo.x + 200f * sin(mitad), tobillo.y - 200f * cos(mitad), confianza)
    val cadera = PuntoDetectado(rodilla.x - 200f * sin(mitad), rodilla.y - 200f * cos(mitad), confianza)
    val oculto = { p: PuntoDetectado -> p.copy(confianza = 0.1f) }
    val izq = ladoVisible == Lado.IZQUIERDO
    return Postura(
        puntos = mapOf(
            PuntoCuerpo.TOBILLO_IZQ to if (izq) tobillo else oculto(tobillo),
            PuntoCuerpo.RODILLA_IZQ to if (izq) rodilla else oculto(rodilla),
            PuntoCuerpo.CADERA_IZQ to if (izq) cadera else oculto(cadera),
            PuntoCuerpo.TOBILLO_DER to if (izq) oculto(tobillo) else tobillo,
            PuntoCuerpo.RODILLA_DER to if (izq) oculto(rodilla) else rodilla,
            PuntoCuerpo.CADERA_DER to if (izq) oculto(cadera) else cadera,
        ),
        marcaTiempo = marca,
        ancho = 720,
        alto = 960,
    )
}

private const val CUADRO_MS = 33L

/**
 * Persona con LOS DOS lados visibles. [separacionCaderas] en px: 0 es de lado
 * (caderas encimadas); unos 150 px con muslos de 200 px es de frente.
 */
private fun personaDosLados(
    flexion: Float,
    marca: Long,
    confIzq: Float = 0.95f,
    confDer: Float = 0.95f,
    separacionCaderas: Float = 0f,
): Postura {
    val base = persona(flexion, marca)
    fun mover(p: PuntoDetectado, conf: Float) = p.copy(x = p.x + separacionCaderas, confianza = conf)
    val izq = base.puntos.filterKeys { it.name.endsWith("IZQ") }
    return base.copy(
        puntos = izq.mapValues { it.value.copy(confianza = confIzq) } + mapOf(
            PuntoCuerpo.CADERA_DER to mover(izq.getValue(PuntoCuerpo.CADERA_IZQ), confDer),
            PuntoCuerpo.RODILLA_DER to mover(izq.getValue(PuntoCuerpo.RODILLA_IZQ), confDer),
            PuntoCuerpo.TOBILLO_DER to mover(izq.getValue(PuntoCuerpo.TOBILLO_IZQ), confDer),
        ),
    )
}

/** Genera cuadros a 30 fps: [duracionMs] llevando la flexión de [desde] a [hasta]. */
private fun tramo(desde: Float, hasta: Float, duracionMs: Long, inicio: Long, conf: Float = 0.95f): List<Postura> {
    val cuadros = (duracionMs / CUADRO_MS).toInt().coerceAtLeast(1)
    return (1..cuadros).map { i -> persona(desde + (hasta - desde) * i / cuadros, inicio + i * CUADRO_MS, conf) }
}

/** Calibra de pie 2 s y devuelve la marca de tiempo final. */
private fun AnalizadorPostura.calibrarDePie(): Long {
    var marca = 0L
    var listo = false
    repeat(70) {
        marca += CUADRO_MS
        if (!listo) listo = calibrar(persona(4f, marca))
    }
    assertTrue(listo, "la calibración de pie debió terminar")
    return marca
}

/** Una sentadilla: baja en 1 s hasta [profundidad] y sube en 1 s. */
private fun sentadilla(profundidad: Float, inicio: Long, conf: Float = 0.95f): List<Postura> {
    val bajada = tramo(4f, profundidad, 1_000, inicio, conf)
    val subida = tramo(profundidad, 4f, 1_000, bajada.last().marcaTiempo, conf)
    return bajada + subida
}

class AnalizadorPosturaTest {

    @Test
    fun anguloRectoMideNoventaGrados() {
        val a = PuntoDetectado(0f, 0f, 1f)
        val b = PuntoDetectado(0f, 100f, 1f)
        val c = PuntoDetectado(100f, 100f, 1f)
        assertEquals(90f, anguloEn(a, b, c)!!, 0.01f)
        assertNull(anguloEn(a, a, c)) // puntos encimados: no hay ángulo
    }

    @Test
    fun laZonaDelEjercicioDefineLaArticulacion() {
        assertEquals(ArticulacionMedida.RODILLA, ArticulacionMedida.deZona("Rodilla"))
        assertEquals(ArticulacionMedida.CADERA, ArticulacionMedida.deZona(" cadera "))
        assertEquals(ArticulacionMedida.TOBILLO, ArticulacionMedida.deZona("Tobillo"))
        assertNull(ArticulacionMedida.deZona("Muslo"))
    }

    @Test
    fun cuentaCincoSentadillasCompletas() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        var marca = analizador.calibrarDePie()
        repeat(5) {
            val cuadros = sentadilla(95f, marca)
            cuadros.forEach { analizador.procesar(it) }
            marca = cuadros.last().marcaTiempo
        }
        val r = analizador.resultado()
        assertEquals(5, r.repeticiones)
        assertEquals(5, r.repeticionesCompletas)
        assertEquals(0, r.totalDescartadas)
        assertTrue(r.anguloMaximo in 85f..96f, "máximo ${r.anguloMaximo}")
        assertTrue(r.porcentajeVisible >= 95)
        assertEquals(MetodoMedicion.CAMARA, analizador.resultadoEjercicio(1, 30, 10).metodo)
    }

    @Test
    fun mediaSentadillaCuentaPeroNoComoCompleta() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        val marca = analizador.calibrarDePie()
        sentadilla(55f, marca).forEach { analizador.procesar(it) }
        val r = analizador.resultado()
        assertEquals(1, r.repeticiones)
        assertEquals(0, r.repeticionesCompletas)
    }

    @Test
    fun sinPersonaVisibleNoSeCuentaNada() {
        // Mover el teléfono con la mano frente a una pared: no hay rodilla que medir.
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        var marca = analizador.calibrarDePie()
        repeat(90) {
            marca += CUADRO_MS
            analizador.procesar(Postura(emptyMap(), marca, 720, 960))
        }
        assertEquals(0, analizador.totalRepeticiones)
        assertEquals(AvisoCamara.NO_TE_VEO, analizador.aviso)
        assertEquals(0, analizador.resultado().porcentajeVisible)
    }

    @Test
    fun puntosConPocaConfianzaNoCuentan() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        val marca = analizador.calibrarDePie()
        sentadilla(95f, marca, conf = 0.2f).forEach { analizador.procesar(it) }
        assertEquals(0, analizador.totalRepeticiones)
    }

    @Test
    fun siElTelefonoSeMueveLaRepeticionSeDescarta() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        val marca = analizador.calibrarDePie()
        val cuadros = sentadilla(95f, marca)
        cuadros.forEachIndexed { i, postura ->
            // A la mitad de la bajada alguien levanta el teléfono.
            analizador.procesar(postura, telefonoQuieto = i !in 20..25)
        }
        val r = analizador.resultado()
        assertEquals(0, r.repeticiones)
        assertEquals(1, r.descartadas[MotivoDescarte.TELEFONO_MOVIDO])
    }

    @Test
    fun perderALaPersonaMasDeUnSegundoDescartaLaRepeticion() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        var marca = analizador.calibrarDePie()
        val bajada = tramo(4f, 95f, 1_000, marca)
        bajada.forEach { analizador.procesar(it) }
        marca = bajada.last().marcaTiempo
        repeat(40) { // 1.3 s fuera del cuadro
            marca += CUADRO_MS
            analizador.procesar(Postura(emptyMap(), marca, 720, 960))
        }
        tramo(95f, 4f, 1_000, marca).forEach { analizador.procesar(it) }
        val r = analizador.resultado()
        assertEquals(0, r.repeticiones)
        assertEquals(1, r.descartadas[MotivoDescarte.SE_DEJO_DE_VER])
    }

    @Test
    fun unaRepeticionDemasiadoRapidaSeDescarta() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        val marca = analizador.calibrarDePie()
        val bajada = tramo(4f, 95f, 200, marca)
        val subida = tramo(95f, 4f, 200, bajada.last().marcaTiempo)
        val dePie = tramo(4f, 4f, 300, subida.last().marcaTiempo)
        (bajada + subida + dePie).forEach { analizador.procesar(it) }
        val r = analizador.resultado()
        assertEquals(0, r.repeticiones)
        assertEquals(1, r.descartadas[MotivoDescarte.DEMASIADO_RAPIDA])
    }

    @Test
    fun unSaltoAisladoDelDetectorSeIgnora() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        var marca = analizador.calibrarDePie()
        repeat(30) {
            marca += CUADRO_MS
            // Un solo cuadro con la rodilla «doblada» 110° entre cuadros de pie.
            analizador.procesar(persona(if (it == 15) 110f else 4f, marca))
        }
        assertEquals(0, analizador.totalRepeticiones)
        assertTrue(analizador.anguloMaximo < 15f)
    }

    @Test
    fun laCalibracionDeSentadillaExigeEstarDePie() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        var marca = 0L
        repeat(70) {
            marca += CUADRO_MS
            assertFalse(analizador.calibrar(persona(60f, marca)))
        }
        assertEquals(AvisoCamara.PONTE_DERECHO, analizador.aviso)
        assertNull(analizador.anguloBase)
    }

    @Test
    fun laCalibracionEsperaAQueLaPersonaEsteQuieta() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        var marca = 0L
        // Se balancea 1 s: no debe calibrar.
        repeat(30) {
            marca += CUADRO_MS
            assertFalse(analizador.calibrar(persona(if (it % 2 == 0) 2f else 20f, marca)))
        }
        assertEquals(AvisoCamara.QUEDATE_QUIETO, analizador.aviso)
        // Quieto 1.6 s: ya calibra.
        var listo = false
        repeat(50) {
            marca += CUADRO_MS
            if (!listo) listo = analizador.calibrar(persona(5f, marca))
        }
        assertTrue(listo)
        assertEquals(175f, analizador.anguloBase!!, 0.5f)
    }

    @Test
    fun conElTelefonoEnMovimientoNoSeCalibra() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        var marca = 0L
        repeat(70) {
            marca += CUADRO_MS
            assertFalse(analizador.calibrar(persona(4f, marca), telefonoQuieto = false))
        }
        assertEquals(AvisoCamara.TELEFONO_MOVIDO, analizador.aviso)
    }

    @Test
    fun siDePieLaRodillaNoSeEstiraDelTodoLasSentadillasIgualCuentan() {
        // De pie marca 25° de flexión (155° internos): con el umbral fijo de 18° nunca «regresaría».
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        var marca = 0L
        var listo = false
        repeat(70) {
            marca += CUADRO_MS
            if (!listo) listo = analizador.calibrar(persona(25f, marca))
        }
        assertTrue(listo)
        assertEquals(33f, analizador.regreso, 0.6f) // 25° de pie + 8° de margen
        repeat(3) {
            val bajada = tramo(25f, 95f, 1_000, marca)
            val subida = tramo(95f, 25f, 1_000, bajada.last().marcaTiempo)
            (bajada + subida).forEach { analizador.procesar(it) }
            marca = subida.last().marcaTiempo
        }
        assertEquals(3, analizador.totalRepeticiones)
    }

    @Test
    fun deFrenteALaCamaraPidePonerseDeLado() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        var marca = 0L
        repeat(70) {
            marca += CUADRO_MS
            assertFalse(analizador.calibrar(personaDosLados(4f, marca, separacionCaderas = 150f)))
        }
        assertEquals(AvisoCamara.PONTE_DE_LADO, analizador.aviso)
        // De lado (caderas encimadas) sí calibra.
        var listo = false
        repeat(70) {
            marca += CUADRO_MS
            if (!listo) listo = analizador.calibrar(personaDosLados(4f, marca))
        }
        assertTrue(listo)
    }

    @Test
    fun sigueMidiendoConElLadoDeLaCalibracion() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        var marca = 0L
        var listo = false
        repeat(70) {
            marca += CUADRO_MS
            if (!listo) listo = analizador.calibrar(personaDosLados(4f, marca, confIzq = 0.95f, confDer = 0.6f))
        }
        assertTrue(listo)
        assertEquals(Lado.IZQUIERDO, analizador.lado)
        // Ahora el lado derecho se ve «mejor», pero el izquierdo sigue visible: no se cambia de pierna.
        repeat(10) {
            marca += CUADRO_MS
            analizador.procesar(personaDosLados(4f, marca, confIzq = 0.8f, confDer = 0.99f))
        }
        assertEquals(Lado.IZQUIERDO, analizador.lado)
        // Si el izquierdo deja de verse, se usa el derecho.
        marca += CUADRO_MS
        analizador.procesar(personaDosLados(4f, marca, confIzq = 0.2f, confDer = 0.99f))
        assertEquals(Lado.DERECHO, analizador.lado)
    }

    @Test
    fun midiendoUsaElLadoQueMejorSeVe() {
        val analizador = AnalizadorPostura(ArticulacionMedida.RODILLA, anguloObjetivo = 90)
        val medida = ArticulacionMedida.RODILLA.medir(persona(30f, 0L, ladoVisible = Lado.DERECHO))
        assertNotNull(medida)
        assertEquals(Lado.DERECHO, medida.lado)
        assertTrue(abs(medida.angulo - 150f) < 0.5f)
        assertEquals(45f, analizador.umbralSubida)
    }
}

class VigilanteQuietudTest {
    private fun gravedad(inclinacion: Float, marca: Long): LecturaEje {
        val r = inclinacion * PI.toFloat() / 180f
        return LecturaEje(x = 0f, y = 9.81f * cos(r), z = 9.81f * sin(r), marcaTiempo = marca)
    }

    @Test
    fun apoyadoSigueQuietoAunqueVibrePoco() {
        val v = VigilanteQuietud()
        var marca = 0L
        repeat(100) {
            marca += 20
            assertTrue(v.procesar(gravedad(if (it % 2 == 0) 0f else 1.5f, marca)))
        }
    }

    @Test
    fun levantarloLoMarcaComoMovidoYVuelveAQuietoTrasUnSegundo() {
        val v = VigilanteQuietud()
        var marca = 0L
        repeat(10) { marca += 20; v.procesar(gravedad(0f, marca)) }
        repeat(20) { marca += 20; v.procesar(gravedad(35f, marca)) }
        assertFalse(v.quieto)
        // Lo regresan a su lugar: medio segundo después todavía no se confía.
        repeat(25) { marca += 20; v.procesar(gravedad(0f, marca)) }
        assertFalse(v.quieto)
        repeat(60) { marca += 20; v.procesar(gravedad(0f, marca)) }
        assertTrue(v.quieto)
    }

    @Test
    fun unaSacudidaFuerteCuentaComoMovimiento() {
        val v = VigilanteQuietud()
        var marca = 0L
        repeat(10) { marca += 20; v.procesar(gravedad(0f, marca)) }
        repeat(10) { marca += 20; v.procesar(LecturaEje(0f, 16f, 0f, marca)) }
        assertFalse(v.quieto)
    }
}
