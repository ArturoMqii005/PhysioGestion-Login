package com.terapid.physiogestion.servicios

import com.terapid.physiogestion.datos.AlmacenDocumentos
import com.terapid.physiogestion.datos.AlmacenEnMemoria
import com.terapid.physiogestion.datos.Coleccion
import com.terapid.physiogestion.modelos.AltaUsuarioRespuesta
import com.terapid.physiogestion.modelos.AltaUsuarioSolicitud
import com.terapid.physiogestion.modelos.Invitacion
import com.terapid.physiogestion.modelos.LoginRespuesta
import com.terapid.physiogestion.modelos.RegistroRespuesta
import com.terapid.physiogestion.modelos.RegistroSolicitud
import com.terapid.physiogestion.modelos.Rol
import com.terapid.physiogestion.modelos.UsuarioAdministrado
import com.terapid.physiogestion.modelos.UsuarioPublico
import com.terapid.physiogestion.modelos.UsuarioRegistrado
import com.terapid.physiogestion.plugins.AccesoDenegado
import com.terapid.physiogestion.plugins.Conflicto
import com.terapid.physiogestion.plugins.CorreoSinVerificar
import com.terapid.physiogestion.plugins.DatosNoProcesables
import com.terapid.physiogestion.plugins.DemasiadosIntentos
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Token abierto. Se guarda el SHA-256 del token, no el token: quien lea la base no puede usarlo. */
@Serializable
data class SesionAbierta(val tokenHash: String, val usuarioId: Int, val creadaEn: Long, val ultimoUso: Long)

/** Código de un solo uso (verificación de correo o recuperación). Solo se guarda su hash. */
@Serializable
data class CodigoPendiente(val clave: String, val hash: String, val venceEn: Long, val intentos: Int = 0)

/**
 * CUENTAS DE USUARIO (módulo USR). Reúne todo el ciclo de vida de una cuenta:
 *
 *  - Inicio de sesión con bloqueo tras 5 intentos fallidos (USR-15) y token que vence
 *    tras 30 días sin uso; cada petición lo renueva (USR-14).
 *  - Registro del paciente con el código de invitación de la clínica (USR-01),
 *    verificación del correo con un código de 6 dígitos (USR-20) y aceptación del
 *    aviso de privacidad (USR-21).
 *  - Recuperación y restablecimiento de la contraseña con un código de un solo uso
 *    (USR-10, USR-11) y cambio de contraseña desde el perfil (USR-12).
 *  - Alta y desactivación de cuentas del personal por el administrador (USR-02, ADM-03).
 *
 * Las contraseñas se guardan con BCrypt ([Contrasenas]); los tokens y los códigos, con SHA-256.
 */
class AutenticacionServicio(
    usuariosIniciales: List<UsuarioRegistrado>,
    invitacionesIniciales: List<Invitacion> = emptyList(),
    private val correo: EnviadorCorreo = CorreoEnRegistro(),
    private val reloj: Clock = Clock.systemUTC(),
    almacen: AlmacenDocumentos = AlmacenEnMemoria(),
    json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        const val VERSION_AVISO = "2026-11"
        const val DIAS_SESION = 30L
        private const val MS_DIA = 86_400_000L
        private const val MS_MINUTO = 60_000L
        const val MINUTOS_CODIGO = 15L
        const val MAX_INTENTOS_CODIGO = 5
        const val MAX_FALLOS_LOGIN = 5
        const val MINUTOS_BLOQUEO = 5L
        private const val MINUTOS_VENTANA_FALLOS = 15L
        /** Máximo de códigos que se envían al mismo correo en 15 minutos (evita llenar su bandeja). */
        private const val MAX_CORREOS_POR_VENTANA = 3
        const val DIAS_INVITACION = 7L
        private val PATRON_USUARIO = Regex("^[a-z0-9._]{4,30}$")
        private val PATRON_CORREO = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
    }

    private val colUsuarios = Coleccion(almacen, "usuarios", UsuarioRegistrado.serializer(), json) { it.id.toString() }
    private val colInvitaciones = Coleccion(almacen, "invitaciones", Invitacion.serializer(), json) { it.codigo }
    private val colSesiones = Coleccion(almacen, "sesiones_abiertas", SesionAbierta.serializer(), json) { it.tokenHash }
    private val colCodigos = Coleccion(almacen, "codigos", CodigoPendiente.serializer(), json) { it.clave }

    private val usuarios = ConcurrentHashMap<Int, UsuarioRegistrado>()
    private val invitaciones = ConcurrentHashMap<String, Invitacion>()
    private val sesiones = ConcurrentHashMap<String, SesionAbierta>()
    private val codigos = ConcurrentHashMap<String, CodigoPendiente>()

    /** Intentos fallidos de inicio de sesión por usuario o correo (solo en memoria). */
    private data class Fallos(val cuantos: Int, val desde: Long, val bloqueadoHasta: Long = 0)
    private val fallos = ConcurrentHashMap<String, Fallos>()

    /** Momentos en que se envió un código a cada correo, para no saturarlo. */
    private val enviosPorCorreo = ConcurrentHashMap<String, List<Long>>()

    init {
        colUsuarios.cargar(usuariosIniciales).forEach { usuarios[it.id] = it }
        colInvitaciones.cargar(invitacionesIniciales).forEach { invitaciones[it.codigo] = it }
        colSesiones.cargar().forEach { sesiones[it.tokenHash] = it }
        colCodigos.cargar().forEach { codigos[it.clave] = it }
    }

    private fun ahora(): Long = reloj.millis()
    private fun iso(ms: Long): String = Instant.ofEpochMilli(ms).toString()

    // ======================================================================= INICIO DE SESIÓN

    /**
     * Devuelve la sesión nueva, o null si el usuario o la contraseña no coinciden (401).
     * [identificador] puede ser el usuario o el correo.
     */
    fun iniciarSesion(identificador: String, contrasena: String): LoginRespuesta? {
        val clave = identificador.trim().lowercase()
        val t = ahora()
        fallos[clave]?.let { f ->
            if (f.bloqueadoHasta > t) throw bloqueo(f.bloqueadoHasta - t)
        }
        val encontrado = buscar(clave)
        // Se compara siempre (aunque el usuario no exista) para que la respuesta tarde lo mismo.
        if (!Contrasenas.coincide(contrasena, encontrado?.contrasenaHash) || encontrado == null) {
            registrarFallo(clave, t)
            return null
        }
        fallos.remove(clave)
        if (!encontrado.activo) {
            throw AccesoDenegado("Tu cuenta está desactivada. Comunícate con la clínica.", "CUENTA_DESACTIVADA")
        }
        if (!encontrado.correoVerificado) throw CorreoSinVerificar(encontrado.correo.orEmpty())

        val token = Contrasenas.token()
        val sesion = SesionAbierta(Contrasenas.sha256(token), encontrado.id, t, t)
        sesiones[sesion.tokenHash] = sesion
        colSesiones.guardar(sesion)
        return LoginRespuesta(
            token = token,
            usuario = encontrado.aPublico(),
            expiraEn = iso(t + DIAS_SESION * MS_DIA),
            requiereConsentimiento = requiereConsentimiento(encontrado),
            debeCambiarContrasena = encontrado.debeCambiarContrasena,
        )
    }

    private fun registrarFallo(clave: String, t: Long) {
        val previo = fallos[clave]
        val actual = if (previo == null || t - previo.desde > MINUTOS_VENTANA_FALLOS * MS_MINUTO) {
            Fallos(1, t)
        } else {
            previo.copy(cuantos = previo.cuantos + 1)
        }
        if (actual.cuantos >= MAX_FALLOS_LOGIN) {
            val hasta = t + MINUTOS_BLOQUEO * MS_MINUTO
            fallos[clave] = actual.copy(bloqueadoHasta = hasta)
            throw bloqueo(hasta - t)
        }
        fallos[clave] = actual
    }

    private fun bloqueo(msRestantes: Long): DemasiadosIntentos {
        val minutos = ((msRestantes + MS_MINUTO - 1) / MS_MINUTO).coerceAtLeast(1)
        return DemasiadosIntentos(
            "Demasiados intentos fallidos. Espera $minutos ${if (minutos == 1L) "minuto" else "minutos"} o restablece tu contraseña.",
            segundos = msRestantes / 1000,
        )
    }

    /**
     * Usuario dueño del token, o null si no existe, si venció (30 días sin uso) o si la
     * cuenta se desactivó. Cada uso recorre la fecha de vencimiento (renovación sin contraseña).
     */
    fun usuarioDeToken(token: String): UsuarioPublico? {
        val hash = Contrasenas.sha256(token)
        val sesion = sesiones[hash] ?: return null
        val t = ahora()
        val usuario = usuarios[sesion.usuarioId]
        if (t - sesion.ultimoUso > DIAS_SESION * MS_DIA || usuario == null || !usuario.activo) {
            sesiones.remove(hash)
            colSesiones.borrar(hash)
            return null
        }
        // Se escribe en la base como máximo una vez por hora para no hacer una escritura por petición.
        if (t - sesion.ultimoUso > 60 * MS_MINUTO) {
            val renovada = sesion.copy(ultimoUso = t)
            sesiones[hash] = renovada
            colSesiones.guardar(renovada)
        }
        return usuario.aPublico()
    }

    fun cerrarSesion(token: String) {
        val hash = Contrasenas.sha256(token)
        sesiones.remove(hash)
        colSesiones.borrar(hash)
    }

    /** Cierra todas las sesiones de un usuario, menos la del token [excepto] (si se indica). */
    private fun cerrarSesionesDe(usuarioId: Int, excepto: String? = null) {
        val conservar = excepto?.let(Contrasenas::sha256)
        sesiones.values.filter { it.usuarioId == usuarioId && it.tokenHash != conservar }.forEach {
            sesiones.remove(it.tokenHash)
            colSesiones.borrar(it.tokenHash)
        }
    }

    fun requiereConsentimiento(u: UsuarioRegistrado): Boolean = u.rol == Rol.PACIENTE && u.avisoAceptado != VERSION_AVISO

    fun perfil(usuarioId: Int): UsuarioRegistrado = usuarios[usuarioId] ?: throw AccesoDenegado("Inicia sesión para continuar.")

    // ======================================================================= REGISTRO DEL PACIENTE

    /** Crea la cuenta del paciente con el código de invitación y le envía el código de verificación. */
    fun registrar(s: RegistroSolicitud): RegistroRespuesta {
        val codigo = normalizarInvitacion(s.codigoInvitacion)
        val usuario = s.usuario.trim().lowercase()
        val correoNuevo = s.correo.trim().lowercase()
        validarUsuario(usuario)
        validarCorreo(correoNuevo)
        Contrasenas.problema(s.contrasena, usuario)?.let { throw DatosNoProcesables(it) }
        if (!s.aceptaAvisoPrivacidad) {
            throw DatosNoProcesables("Para crear tu cuenta necesitas aceptar el aviso de privacidad.")
        }
        val nuevo = synchronized(this) {
            val invitacion = invitaciones[codigo]
            if (invitacion == null || invitacion.usada || Instant.parse(invitacion.venceEn).toEpochMilli() < ahora()) {
                throw DatosNoProcesables("El código de invitación no es válido, ya se usó o venció. Pide uno nuevo en recepción.", "INVITACION_INVALIDA")
            }
            if (usuarios.values.any { it.usuario.equals(usuario, ignoreCase = true) }) throw Conflicto("Ese usuario ya está en uso. Elige otro.")
            if (usuarios.values.any { it.correo.equals(correoNuevo, ignoreCase = true) }) {
                throw Conflicto("Ese correo ya tiene una cuenta. Inicia sesión o recupera tu contraseña.")
            }
            val creado = UsuarioRegistrado(
                id = (usuarios.keys.maxOrNull() ?: 0) + 1,
                usuario = usuario,
                contrasenaHash = Contrasenas.hash(s.contrasena),
                nombre = invitacion.nombrePaciente,
                rol = Rol.PACIENTE,
                pacienteId = invitacion.pacienteId,
                correo = correoNuevo,
                correoVerificado = false,
                avisoAceptado = VERSION_AVISO,
            )
            guardarUsuario(creado)
            val usada = invitacion.copy(usada = true)
            invitaciones[codigo] = usada
            colInvitaciones.guardar(usada)
            creado
        }
        enviarCodigo("VERIFICACION:${nuevo.correo}", nuevo.correo!!) { PlantillasCorreo.verificacion(nuevo.nombre, it) }
        return RegistroRespuesta(
            mensaje = "Tu cuenta se creó. Te enviamos un código de 6 dígitos a ${enmascarar(correoNuevo)} para confirmar tu correo.",
            correoEnmascarado = enmascarar(correoNuevo),
        )
    }

    /** Confirma el correo con el código (USR-20). Después ya se puede iniciar sesión. */
    fun verificarCorreo(correoDado: String, codigo: String): String {
        val correoLimpio = correoDado.trim().lowercase()
        val u = buscarPorCorreo(correoLimpio)
        if (u != null && u.correoVerificado) return "Tu correo ya estaba confirmado. Ya puedes iniciar sesión."
        val clave = "VERIFICACION:$correoLimpio"
        comprobarCodigo(clave, codigo)
        if (u == null) throw DatosNoProcesables("El código no es correcto o ya venció.")
        guardarUsuario(u.copy(correoVerificado = true))
        return "¡Listo! Tu correo quedó confirmado. Ya puedes iniciar sesión."
    }

    /** Reenvía el código de verificación. La respuesta es la misma exista o no la cuenta. */
    fun reenviarVerificacion(correoDado: String): String {
        val correoLimpio = correoDado.trim().lowercase()
        val u = buscarPorCorreo(correoLimpio)
        if (u != null && !u.correoVerificado) {
            enviarCodigo("VERIFICACION:$correoLimpio", correoLimpio) { PlantillasCorreo.verificacion(u.nombre, it) }
        }
        return "Si hay una cuenta pendiente con ese correo, te enviamos un código nuevo."
    }

    // ======================================================================= CONTRASEÑA

    /**
     * Recuperación (USR-10). Siempre responde lo mismo, exista o no el correo, para que
     * nadie pueda averiguar qué correos tienen cuenta (OWASP).
     */
    fun solicitarRecuperacion(correoDado: String): String {
        val correoLimpio = correoDado.trim().lowercase()
        val u = buscarPorCorreo(correoLimpio)
        if (u != null && u.activo && u.correoVerificado) {
            enviarCodigo("RECUPERACION:${u.id}", correoLimpio) { PlantillasCorreo.recuperacion(u.nombre, it) }
        }
        return "Si ese correo tiene una cuenta, te enviamos un código para restablecer tu contraseña. Revisa también la carpeta de spam."
    }

    /** Restablecimiento (USR-11): código + contraseña nueva. Cierra todas las sesiones abiertas. */
    fun restablecer(correoDado: String, codigo: String, nueva: String): String {
        val correoLimpio = correoDado.trim().lowercase()
        val u = buscarPorCorreo(correoLimpio)
        Contrasenas.problema(nueva, u?.usuario)?.let { throw DatosNoProcesables(it) }
        if (u == null) throw DatosNoProcesables("El código no es correcto o ya venció. Pide uno nuevo.")
        comprobarCodigo("RECUPERACION:${u.id}", codigo)
        guardarUsuario(u.copy(contrasenaHash = Contrasenas.hash(nueva), debeCambiarContrasena = false))
        cerrarSesionesDe(u.id)
        fallos.remove(u.usuario.lowercase())
        fallos.remove(correoLimpio)
        correo.enviar(PlantillasCorreo.contrasenaCambiada(u.nombre).copy(para = correoLimpio))
        return "Tu contraseña se cambió. Inicia sesión con la nueva."
    }

    /** Cambio desde el perfil (USR-12): pide la actual y cierra las demás sesiones. */
    fun cambiarContrasena(usuarioId: Int, tokenActual: String, actual: String, nueva: String): String {
        val u = perfil(usuarioId)
        if (!Contrasenas.coincide(actual, u.contrasenaHash)) throw DatosNoProcesables("Tu contraseña actual no es correcta.")
        if (actual == nueva) throw DatosNoProcesables("La contraseña nueva debe ser distinta de la actual.")
        Contrasenas.problema(nueva, u.usuario)?.let { throw DatosNoProcesables(it) }
        guardarUsuario(u.copy(contrasenaHash = Contrasenas.hash(nueva), debeCambiarContrasena = false))
        cerrarSesionesDe(u.id, excepto = tokenActual)
        u.correo?.let { correo.enviar(PlantillasCorreo.contrasenaCambiada(u.nombre).copy(para = it)) }
        return "Tu contraseña se cambió. Cerramos tus sesiones en otros dispositivos."
    }

    /** Aviso de privacidad aceptado (USR-21). */
    fun aceptarAviso(usuarioId: Int, version: String) {
        if (version != VERSION_AVISO) throw DatosNoProcesables("El aviso de privacidad cambió. Vuelve a leerlo para aceptarlo.")
        val u = perfil(usuarioId)
        guardarUsuario(u.copy(avisoAceptado = VERSION_AVISO))
    }

    // ======================================================================= INVITACIONES Y PERSONAL

    /** Recepción o el administrador generan el código que el paciente usa para registrarse. */
    fun crearInvitacion(nombrePaciente: String, creadaPor: String): Invitacion {
        val nombre = nombrePaciente.trim().replace(Regex("\\s+"), " ")
        if (nombre.length !in 5..80 || !nombre.contains(' ')) {
            throw DatosNoProcesables("Escribe el nombre completo del paciente (nombre y apellidos).")
        }
        return synchronized(this) {
            var codigo: String
            do { codigo = Contrasenas.codigoInvitacion() } while (invitaciones.containsKey(codigo))
            val ids = usuarios.values.mapNotNull { it.pacienteId } + invitaciones.values.map { it.pacienteId }
            val t = ahora()
            val nueva = Invitacion(
                codigo = codigo,
                pacienteId = (ids.maxOrNull() ?: 0) + 1,
                nombrePaciente = nombre,
                creadaPor = creadaPor,
                creadaEn = iso(t),
                venceEn = iso(t + DIAS_INVITACION * MS_DIA),
            )
            invitaciones[codigo] = nueva
            colInvitaciones.guardar(nueva)
            nueva
        }
    }

    fun invitaciones(): List<Invitacion> = invitaciones.values.sortedByDescending { it.creadaEn }

    /** Alta del personal (USR-02): la cuenta nace con una contraseña temporal que hay que cambiar. */
    fun darDeAlta(s: AltaUsuarioSolicitud): AltaUsuarioRespuesta {
        val usuario = s.usuario.trim().lowercase()
        val correoNuevo = s.correo.trim().lowercase()
        val nombre = s.nombre.trim().replace(Regex("\\s+"), " ")
        if (s.rol == Rol.PACIENTE) throw DatosNoProcesables("Los pacientes crean su cuenta con un código de invitación.")
        validarUsuario(usuario)
        validarCorreo(correoNuevo)
        if (nombre.length !in 5..80) throw DatosNoProcesables("Escribe el nombre completo.")
        val temporal = Contrasenas.temporal()
        val creado = synchronized(this) {
            if (usuarios.values.any { it.usuario.equals(usuario, ignoreCase = true) }) throw Conflicto("Ese usuario ya está en uso.")
            if (usuarios.values.any { it.correo.equals(correoNuevo, ignoreCase = true) }) throw Conflicto("Ese correo ya tiene una cuenta.")
            UsuarioRegistrado(
                id = (usuarios.keys.maxOrNull() ?: 0) + 1,
                usuario = usuario,
                contrasenaHash = Contrasenas.hash(temporal),
                nombre = nombre,
                rol = s.rol,
                correo = correoNuevo,
                debeCambiarContrasena = true,
            ).also(::guardarUsuario)
        }
        return AltaUsuarioRespuesta(creado.aPublico(), temporal)
    }

    /**
     * Edita una cuenta (ADM-03): activar o desactivar, nombre, correo y rol. Desactivar
     * cierra sus sesiones; el historial se conserva. Los pacientes no cambian de rol.
     */
    fun editar(id: Int, s: com.terapid.physiogestion.modelos.EstadoCuentaSolicitud, quienPide: Int): UsuarioAdministrado = synchronized(this) {
        val u = usuarios[id] ?: throw io.ktor.server.plugins.NotFoundException("Ese usuario no existe.")
        if (id == quienPide && s.activo == false) throw DatosNoProcesables("No puedes desactivar tu propia cuenta.")
        if (id == quienPide && s.rol != null && s.rol != u.rol) throw DatosNoProcesables("No puedes cambiar tu propio rol.")
        if (s.rol != null && s.rol != u.rol && (s.rol == Rol.PACIENTE || u.rol == Rol.PACIENTE)) {
            throw DatosNoProcesables("Las cuentas de paciente no cambian de rol.")
        }
        val nombre = s.nombre?.trim()?.replace(Regex("\\s+"), " ")?.also {
            if (it.length !in 5..80) throw DatosNoProcesables("Escribe el nombre completo.")
        }
        val correoNuevo = s.correo?.trim()?.lowercase()?.also {
            validarCorreo(it)
            if (usuarios.values.any { o -> o.id != id && o.correo.equals(it, ignoreCase = true) }) throw Conflicto("Ese correo ya tiene una cuenta.")
        }
        val actualizado = u.copy(
            activo = s.activo ?: u.activo,
            nombre = nombre ?: u.nombre,
            correo = correoNuevo ?: u.correo,
            rol = s.rol ?: u.rol,
        )
        guardarUsuario(actualizado)
        if (!actualizado.activo || actualizado.rol != u.rol) cerrarSesionesDe(id)
        actualizado.administrado()
    }

    fun usuario(id: Int): UsuarioRegistrado? = usuarios[id]

    fun listar(): List<UsuarioAdministrado> = usuarios.values.sortedBy { it.id }.map { it.administrado() }

    /** Nombre del paciente (para enlazar su rutina y sus sesiones). */
    fun usuarios(): List<UsuarioRegistrado> = usuarios.values.toList()

    // ======================================================================= AUXILIARES

    private fun UsuarioRegistrado.administrado() =
        UsuarioAdministrado(id, usuario, nombre, rol, pacienteId, correo, activo, correoVerificado)

    private fun buscar(clave: String): UsuarioRegistrado? =
        usuarios.values.firstOrNull { it.usuario.lowercase() == clave || it.correo?.lowercase() == clave }

    private fun buscarPorCorreo(correoLimpio: String): UsuarioRegistrado? =
        usuarios.values.firstOrNull { it.correo?.lowercase() == correoLimpio }

    private fun guardarUsuario(u: UsuarioRegistrado) {
        usuarios[u.id] = u
        colUsuarios.guardar(u)
    }

    private fun validarUsuario(usuario: String) {
        if (!PATRON_USUARIO.matches(usuario)) {
            throw DatosNoProcesables("El usuario debe tener de 4 a 30 caracteres: letras minúsculas, números, punto o guion bajo.")
        }
    }

    private fun validarCorreo(c: String) {
        if (c.length > 120 || !PATRON_CORREO.matches(c)) throw DatosNoProcesables("Escribe un correo válido, por ejemplo nombre@gmail.com.")
    }

    /** «k7qm 4r2x» → «K7QM-4R2X». */
    private fun normalizarInvitacion(texto: String): String {
        val limpio = texto.uppercase().filter { it.isLetterOrDigit() }
        return if (limpio.length == 8) limpio.take(4) + "-" + limpio.drop(4) else texto.trim().uppercase()
    }

    /** Genera un código nuevo (invalida el anterior) y lo envía, respetando el límite por correo. */
    private fun enviarCodigo(clave: String, para: String, plantilla: (String) -> CorreoSaliente) {
        val t = ahora()
        val recientes = enviosPorCorreo[para].orEmpty().filter { t - it < 15 * MS_MINUTO }
        if (recientes.size >= MAX_CORREOS_POR_VENTANA) return // silencioso: la respuesta al cliente no cambia
        enviosPorCorreo[para] = recientes + t
        val codigo = Contrasenas.codigo()
        val pendiente = CodigoPendiente(clave, Contrasenas.sha256(codigo), t + MINUTOS_CODIGO * MS_MINUTO)
        codigos[clave] = pendiente
        colCodigos.guardar(pendiente)
        correo.enviar(plantilla(codigo).copy(para = para))
    }

    /** Valida un código de un solo uso: vigencia, máximo de intentos y comparación en tiempo constante. */
    private fun comprobarCodigo(clave: String, codigo: String) {
        val pendiente = codigos[clave] ?: throw DatosNoProcesables("El código no es correcto o ya venció. Pide uno nuevo.")
        if (ahora() > pendiente.venceEn) {
            borrarCodigo(clave)
            throw DatosNoProcesables("El código venció. Pide uno nuevo.", "CODIGO_VENCIDO")
        }
        if (!Contrasenas.iguales(Contrasenas.sha256(codigo.trim()), pendiente.hash)) {
            val intentos = pendiente.intentos + 1
            if (intentos >= MAX_INTENTOS_CODIGO) {
                borrarCodigo(clave)
                throw DemasiadosIntentos("Escribiste mal el código demasiadas veces. Pide uno nuevo.", 0)
            }
            val actualizado = pendiente.copy(intentos = intentos)
            codigos[clave] = actualizado
            colCodigos.guardar(actualizado)
            val quedan = MAX_INTENTOS_CODIGO - intentos
            throw DatosNoProcesables("El código no es correcto. Te ${if (quedan == 1) "queda 1 intento" else "quedan $quedan intentos"}.")
        }
        borrarCodigo(clave) // un solo uso
    }

    private fun borrarCodigo(clave: String) {
        codigos.remove(clave)
        colCodigos.borrar(clave)
    }
}
