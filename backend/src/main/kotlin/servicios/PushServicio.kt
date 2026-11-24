package com.terapid.physiogestion.servicios

import com.terapid.physiogestion.datos.AlmacenDocumentos
import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.datos.Coleccion
import com.terapid.physiogestion.modelos.Aviso
import com.terapid.physiogestion.modelos.Destinatarios
import com.terapid.physiogestion.modelos.Dispositivo
import com.terapid.physiogestion.modelos.DispositivoRegistrado
import com.terapid.physiogestion.modelos.DispositivoSolicitud
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.TipoAviso
import com.terapid.physiogestion.modelos.UsuarioPublico
import com.terapid.physiogestion.plugins.DatosNoProcesables
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * NOTIFICACIONES PUSH (Firebase Cloud Messaging).
 *
 *  - Cada teléfono registra su token al iniciar sesión (POST /dispositivos) y lo da de
 *    baja al cerrarla. El servidor lo suscribe al tema de su rol para los anuncios.
 *  - Cada aviso nuevo ([AvisosServicio.alCrear]) se manda a los teléfonos de quien lo
 *    recibe. El envío corre en otro hilo para no hacer esperar a quien causó el aviso.
 *  - Los tokens que FCM reporta como dados de baja (app desinstalada) se desactivan solos.
 *
 * Temas: «prod-pacientes» y «prod-personal» en Railway; «dev-…» en la computadora, para
 * que las pruebas locales nunca le lleguen a los teléfonos de la clínica (PREFIJO_TEMAS).
 */
class PushServicio(
    private val cuentas: AutenticacionServicio,
    private val avisos: AvisosServicio,
    private val enviador: EnviadorPush,
    private val reloj: Clock = Clock.systemUTC(),
    almacen: AlmacenDocumentos = AlmacenEnMemoria(),
    json: Json = Json { ignoreUnknownKeys = true },
    private val prefijo: String = prefijoDesdeEntorno(),
    private val ejecutor: Executor = Executors.newSingleThreadExecutor { Thread(it, "push").apply { isDaemon = true } },
) {
    companion object {
        /** Teléfonos activos por cuenta; al registrar uno más se desactiva el que lleva más tiempo sin usarse. */
        const val MAXIMO_POR_USUARIO = 5
        private val PLATAFORMAS = setOf("ANDROID", "IOS", "ESCRITORIO")

        fun prefijoDesdeEntorno(): String =
            System.getenv("PREFIJO_TEMAS")?.filter { it.isLetterOrDigit() || it == '-' }?.takeIf { it.isNotEmpty() }
                ?: if (System.getenv("RAILWAY_ENVIRONMENT_NAME") == "production") "prod" else "dev"
    }

    private val log = LoggerFactory.getLogger("Push")
    private val col = Coleccion(almacen, "dispositivos", Dispositivo.serializer(), json) { it.token }
    private val dispositivos = ConcurrentHashMap<String, Dispositivo>().apply { col.cargar().forEach { put(it.token, it) } }

    val configurado: Boolean get() = enviador.configurado

    fun tema(d: Destinatarios): List<String> = when (d) {
        Destinatarios.PACIENTES -> listOf("$prefijo-pacientes")
        Destinatarios.PERSONAL -> listOf("$prefijo-personal")
        Destinatarios.TODOS -> listOf("$prefijo-pacientes", "$prefijo-personal")
    }

    private fun temasDe(rol: Rol) = tema(if (rol == Rol.PACIENTE) Destinatarios.PACIENTES else Destinatarios.PERSONAL)

    fun activosDe(usuarioId: Int): List<Dispositivo> = dispositivos.values.filter { it.usuarioId == usuarioId && it.activo }

    // ======================================================================= REGISTRO

    /**
     * La app manda su token de FCM. Si la cuenta ya tenía otros teléfonos y este no se
     * había visto antes, se avisa en esos teléfonos (posible acceso de otra persona).
     */
    @Synchronized
    fun registrar(usuario: UsuarioPublico, s: DispositivoSolicitud): DispositivoRegistrado {
        val token = s.token.trim()
        if (token.length !in 20..4096 || token.any { it.isWhitespace() }) throw DatosNoProcesables("El token del dispositivo no es válido.")
        val plataforma = s.plataforma.trim().uppercase().takeIf { it in PLATAFORMAS } ?: "ANDROID"
        val ahora = reloj.millis()
        val anterior = dispositivos[token]
        val conocido = anterior?.usuarioId == usuario.id

        // El teléfono era de otra cuenta (cerró sesión sin red): deja de recibir lo de esa cuenta.
        if (anterior != null && !conocido && anterior.activo) {
            val rolAnterior = cuentas.usuarios().firstOrNull { it.id == anterior.usuarioId }?.rol
            if (rolAnterior != null) enSegundoPlano { temasDe(rolAnterior).forEach { enviador.desuscribir(listOf(token), it) } }
        }

        // Se crea antes de guardar el token, así la push llega a los otros teléfonos y no a este.
        if (!conocido && activosDe(usuario.id).isNotEmpty()) {
            val nombre = when (plataforma) { "IOS" -> "un iPhone"; "ESCRITORIO" -> "una computadora"; else -> "un teléfono Android" }
            avisos.seguridad(
                usuario.id, "Entraron a tu cuenta en otro dispositivo",
                "Se inició sesión en $nombre nuevo. Si no fuiste tú, cambia tu contraseña en Mi cuenta y avisa a la clínica.",
            )
        }

        guardar(Dispositivo(token, usuario.id, plataforma, registrado = if (conocido) anterior!!.registrado else ahora, ultimoUso = ahora))
        activosDe(usuario.id).sortedByDescending { it.ultimoUso }.drop(MAXIMO_POR_USUARIO).forEach { desactivar(it.token) }

        val temas = temasDe(usuario.rol)
        enSegundoPlano { temas.forEach { enviador.suscribir(listOf(token), it) } }
        return DispositivoRegistrado(temas, nuevo = !conocido)
    }

    /** Cierre de sesión: el teléfono deja de recibir push de esa cuenta. */
    fun quitar(usuario: UsuarioPublico, token: String): Boolean {
        val d = dispositivos[token]?.takeIf { it.usuarioId == usuario.id && it.activo } ?: return false
        desactivar(d.token)
        return true
    }

    // ======================================================================= ENVÍO

    /** Se llama con cada aviso nuevo. Los anuncios no van por aquí: salen una vez por tema. */
    fun enviarAviso(aviso: Aviso) {
        if (aviso.tipo == TipoAviso.ANUNCIO) return
        val tokens = activosDe(aviso.usuarioId).map { it.token }
        if (tokens.isEmpty()) return
        val datos = buildMap {
            put("tipo", aviso.tipo.name)
            put("avisoId", aviso.id.toString())
            // Los avisos de seguridad llevan el texto: la sesión de ese teléfono puede estar
            // cerrada (cambio de contraseña) y la app no podría pedirlo. No traen datos de salud.
            if (aviso.tipo == TipoAviso.SEGURIDAD) {
                put("titulo", aviso.titulo)
                put("mensaje", aviso.mensaje)
            }
        }
        enSegundoPlano {
            enviador.enviarATokens(tokens, datos).forEach { token ->
                log.info("FCM dio de baja un token; se desactiva.")
                desactivar(token)
            }
        }
    }

    /** Anuncio de la clínica: un mensaje por tema, con el texto (es información general). */
    fun enviarAnuncio(destinatarios: Destinatarios, anuncioId: Int, titulo: String, texto: String) {
        val datos = mapOf("tipo" to TipoAviso.ANUNCIO.name, "anuncioId" to anuncioId.toString(), "titulo" to titulo, "mensaje" to texto)
        enSegundoPlano { tema(destinatarios).forEach { enviador.enviarATema(it, datos) } }
    }

    // ======================================================================= AUXILIARES

    private fun guardar(d: Dispositivo) {
        dispositivos[d.token] = d
        col.guardar(d)
    }

    private fun desactivar(token: String) {
        val d = dispositivos[token]?.takeIf { it.activo } ?: return
        guardar(d.copy(activo = false))
        val rol = cuentas.usuarios().firstOrNull { it.id == d.usuarioId }?.rol ?: return
        enSegundoPlano { temasDe(rol).forEach { enviador.desuscribir(listOf(token), it) } }
    }

    private fun enSegundoPlano(tarea: () -> Unit) {
        ejecutor.execute {
            try {
                tarea()
            } catch (e: Exception) {
                // Una push que falla nunca rompe la operación: el aviso ya quedó guardado.
                log.warn("No se pudo mandar la push: {}", e.message)
            }
        }
    }
}
