package com.terapid.physiogestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terapid.physiogestion.datos.CuentaRepositorio
import com.terapid.physiogestion.datos.remoto.ErrorApi
import com.terapid.physiogestion.modelo.AvisoPrivacidad
import com.terapid.physiogestion.modelo.Invitacion
import com.terapid.physiogestion.servicio.ReglasCuenta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * VIEW MODELS de las cuentas (módulo USR). Todos siguen el mismo patrón: validan
 * en la app con [ReglasCuenta] para avisar al momento, llaman al repositorio y
 * muestran el mensaje del servidor si algo falla. Ignoran la doble pulsación
 * mientras hay una petición en curso (RFX-18).
 */

/** Mensaje para la vista a partir de cualquier error. */
internal fun mensajeDe(e: Exception): String =
    (e as? ErrorApi)?.message ?: "Ocurrió un problema. Inténtalo de nuevo."

/** Segundos de espera antes de poder pedir otro código. */
const val ESPERA_REENVIO_S = 60

// =========================================================================== REGISTRO (USR-01)

enum class CampoRegistro { CODIGO, CORREO, USUARIO, CONTRASENA, CONFIRMACION, AVISO }

data class RegistroUiState(
    val codigo: String = "",
    val correo: String = "",
    val usuario: String = "",
    val contrasena: String = "",
    val confirmacion: String = "",
    val mostrarContrasena: Boolean = false,
    val aceptaAviso: Boolean = false,
    val aviso: AvisoPrivacidad? = null,
    val mostrandoAviso: Boolean = false,
    val cargando: Boolean = false,
    val errores: Map<CampoRegistro, String> = emptyMap(),
    val error: String? = null,
    /** Cuando no es null, la cuenta se creó y la vista va a confirmar este correo. */
    val correoRegistrado: String? = null,
) {
    val fuerza: Int get() = ReglasCuenta.fuerza(contrasena)
}

class RegistroViewModel(private val cuentas: CuentaRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(RegistroUiState())
    val estado: StateFlow<RegistroUiState> = _estado.asStateFlow()

    /** true mientras el paciente no escribe su propio usuario: se sugiere a partir del correo. */
    private var usuarioSugerido = true

    fun cambiarCodigo(t: String) = _estado.update { it.copy(codigo = ReglasCuenta.formatearInvitacion(t), errores = it.errores - CampoRegistro.CODIGO, error = null) }

    fun cambiarCorreo(t: String) = _estado.update {
        val correo = t.trim().take(120)
        it.copy(
            correo = correo,
            usuario = if (usuarioSugerido) ReglasCuenta.usuarioSugerido(correo) else it.usuario,
            errores = it.errores - CampoRegistro.CORREO,
            error = null,
        )
    }

    fun cambiarUsuario(t: String) {
        usuarioSugerido = false
        _estado.update { it.copy(usuario = t.lowercase().trim().take(30), errores = it.errores - CampoRegistro.USUARIO, error = null) }
    }

    fun cambiarContrasena(t: String) = _estado.update { it.copy(contrasena = t.take(64), errores = it.errores - CampoRegistro.CONTRASENA, error = null) }

    fun cambiarConfirmacion(t: String) = _estado.update { it.copy(confirmacion = t.take(64), errores = it.errores - CampoRegistro.CONFIRMACION, error = null) }

    fun alternarMostrar() = _estado.update { it.copy(mostrarContrasena = !it.mostrarContrasena) }

    fun cambiarAceptaAviso(valor: Boolean) = _estado.update { it.copy(aceptaAviso = valor, errores = it.errores - CampoRegistro.AVISO) }

    /** Abre el texto completo del aviso (se descarga la primera vez). */
    fun verAviso() {
        _estado.update { it.copy(mostrandoAviso = true) }
        if (_estado.value.aviso != null) return
        viewModelScope.launch {
            try {
                val aviso = cuentas.avisoPrivacidad()
                _estado.update { it.copy(aviso = aviso) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(mostrandoAviso = false, error = mensajeDe(e)) }
            }
        }
    }

    fun cerrarAviso(aceptar: Boolean) = _estado.update {
        it.copy(mostrandoAviso = false, aceptaAviso = if (aceptar) true else it.aceptaAviso, errores = if (aceptar) it.errores - CampoRegistro.AVISO else it.errores)
    }

    /** Errores de cada campo; vacío si todo se puede enviar. */
    fun validar(s: RegistroUiState = _estado.value): Map<CampoRegistro, String> = buildMap {
        ReglasCuenta.problemaInvitacion(s.codigo)?.let { put(CampoRegistro.CODIGO, it) }
        ReglasCuenta.problemaCorreo(s.correo)?.let { put(CampoRegistro.CORREO, it) }
        ReglasCuenta.problemaUsuario(s.usuario)?.let { put(CampoRegistro.USUARIO, it) }
        ReglasCuenta.problemaContrasena(s.contrasena, s.usuario)?.let { put(CampoRegistro.CONTRASENA, it) }
        if (s.confirmacion != s.contrasena) put(CampoRegistro.CONFIRMACION, "Las contraseñas no coinciden.")
        if (!s.aceptaAviso) put(CampoRegistro.AVISO, "Necesitas aceptar el aviso de privacidad.")
    }

    fun registrar() {
        val s = _estado.value
        if (s.cargando) return
        val errores = validar(s)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, error = "Revisa los campos marcados.") }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                cuentas.registrar(s.codigo, s.usuario, s.correo, s.contrasena, s.aceptaAviso)
                _estado.update { it.copy(cargando = false, correoRegistrado = s.correo.lowercase(), contrasena = "", confirmacion = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }
}

// =========================================================================== VERIFICAR CORREO (USR-20)

data class VerificarCorreoUiState(
    val correo: String,
    val codigo: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val segundosParaReenviar: Int = ESPERA_REENVIO_S,
    /** Mensaje de éxito: el correo quedó confirmado. */
    val listo: String? = null,
)

class VerificarCorreoViewModel(correo: String, private val cuentas: CuentaRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(VerificarCorreoUiState(correo = correo))
    val estado: StateFlow<VerificarCorreoUiState> = _estado.asStateFlow()
    private var cuenta: Job? = null

    init {
        iniciarEspera()
    }

    fun cambiarCodigo(t: String) {
        val codigo = ReglasCuenta.limpiarCodigo(t)
        _estado.update { it.copy(codigo = codigo, error = null) }
        if (codigo.length == 6) verificar() // al escribir el sexto dígito se envía solo
    }

    fun verificar() {
        val s = _estado.value
        if (s.cargando || s.listo != null) return
        if (s.codigo.length != 6) {
            _estado.update { it.copy(error = "El código tiene 6 dígitos.") }
            return
        }
        _estado.update { it.copy(cargando = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val mensaje = cuentas.verificarCorreo(s.correo, s.codigo)
                _estado.update { it.copy(cargando = false, listo = mensaje) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, codigo = "", error = mensajeDe(e)) }
            }
        }
    }

    fun reenviar() {
        val s = _estado.value
        if (s.cargando || s.segundosParaReenviar > 0) return
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val mensaje = cuentas.reenviarVerificacion(s.correo)
                _estado.update { it.copy(cargando = false, info = mensaje) }
                iniciarEspera()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    private fun iniciarEspera() {
        cuenta?.cancel()
        _estado.update { it.copy(segundosParaReenviar = ESPERA_REENVIO_S) }
        cuenta = viewModelScope.launch {
            while (_estado.value.segundosParaReenviar > 0) {
                delay(1_000)
                _estado.update { it.copy(segundosParaReenviar = it.segundosParaReenviar - 1) }
            }
        }
    }
}

// =========================================================================== RECUPERAR Y RESTABLECER (USR-10, USR-11)

enum class PasoRecuperacion { CORREO, CODIGO, LISTO }

data class RecuperarUiState(
    val paso: PasoRecuperacion = PasoRecuperacion.CORREO,
    val correo: String = "",
    val codigo: String = "",
    val nueva: String = "",
    val confirmacion: String = "",
    val mostrarContrasena: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val segundosParaReenviar: Int = 0,
) {
    val fuerza: Int get() = ReglasCuenta.fuerza(nueva)
}

class RecuperarContrasenaViewModel(private val cuentas: CuentaRepositorio, correoInicial: String = "") : ViewModel() {
    private val _estado = MutableStateFlow(RecuperarUiState(correo = correoInicial))
    val estado: StateFlow<RecuperarUiState> = _estado.asStateFlow()
    private var cuenta: Job? = null

    fun cambiarCorreo(t: String) = _estado.update { it.copy(correo = t.trim().take(120), error = null) }
    fun cambiarCodigo(t: String) = _estado.update { it.copy(codigo = ReglasCuenta.limpiarCodigo(t), error = null) }
    fun cambiarNueva(t: String) = _estado.update { it.copy(nueva = t.take(64), error = null) }
    fun cambiarConfirmacion(t: String) = _estado.update { it.copy(confirmacion = t.take(64), error = null) }
    fun alternarMostrar() = _estado.update { it.copy(mostrarContrasena = !it.mostrarContrasena) }

    /** Paso 1: pide el código. La respuesta es la misma exista o no la cuenta. */
    fun enviarCodigo() {
        val s = _estado.value
        if (s.cargando || s.segundosParaReenviar > 0) return
        ReglasCuenta.problemaCorreo(s.correo.lowercase())?.let { msg ->
            _estado.update { it.copy(error = msg) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val mensaje = cuentas.solicitarRecuperacion(s.correo.lowercase())
                _estado.update { it.copy(cargando = false, paso = PasoRecuperacion.CODIGO, info = mensaje) }
                iniciarEspera()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    /** Paso 2: código + contraseña nueva. */
    fun restablecer() {
        val s = _estado.value
        if (s.cargando) return
        val problema = when {
            s.codigo.length != 6 -> "Escribe el código de 6 dígitos que te llegó."
            else -> ReglasCuenta.problemaContrasena(s.nueva) ?: if (s.nueva != s.confirmacion) "Las contraseñas no coinciden." else null
        }
        if (problema != null) {
            _estado.update { it.copy(error = problema) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val mensaje = cuentas.restablecer(s.correo.lowercase(), s.codigo, s.nueva)
                _estado.update { it.copy(cargando = false, paso = PasoRecuperacion.LISTO, info = mensaje, nueva = "", confirmacion = "", codigo = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    /** Regresa al paso del correo (por ejemplo, si se equivocó al escribirlo). */
    fun cambiarDeCorreo() = _estado.update { it.copy(paso = PasoRecuperacion.CORREO, codigo = "", error = null, info = null) }

    private fun iniciarEspera() {
        cuenta?.cancel()
        _estado.update { it.copy(segundosParaReenviar = ESPERA_REENVIO_S) }
        cuenta = viewModelScope.launch {
            while (_estado.value.segundosParaReenviar > 0) {
                delay(1_000)
                _estado.update { it.copy(segundosParaReenviar = it.segundosParaReenviar - 1) }
            }
        }
    }
}

// =========================================================================== CAMBIAR CONTRASEÑA (USR-12)

data class CambiarContrasenaUiState(
    val obligatorio: Boolean,
    val actual: String = "",
    val nueva: String = "",
    val confirmacion: String = "",
    val mostrarContrasena: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null,
    val listo: String? = null,
) {
    val fuerza: Int get() = ReglasCuenta.fuerza(nueva)
}

class CambiarContrasenaViewModel(
    obligatorio: Boolean,
    private val cuentas: CuentaRepositorio,
    private val usuario: String? = null,
) : ViewModel() {
    private val _estado = MutableStateFlow(CambiarContrasenaUiState(obligatorio = obligatorio))
    val estado: StateFlow<CambiarContrasenaUiState> = _estado.asStateFlow()

    fun cambiarActual(t: String) = _estado.update { it.copy(actual = t.take(64), error = null) }
    fun cambiarNueva(t: String) = _estado.update { it.copy(nueva = t.take(64), error = null) }
    fun cambiarConfirmacion(t: String) = _estado.update { it.copy(confirmacion = t.take(64), error = null) }
    fun alternarMostrar() = _estado.update { it.copy(mostrarContrasena = !it.mostrarContrasena) }

    fun guardar() {
        val s = _estado.value
        if (s.cargando || s.listo != null) return
        val problema = when {
            s.actual.isBlank() -> if (s.obligatorio) "Escribe la contraseña temporal que te dieron." else "Escribe tu contraseña actual."
            s.nueva == s.actual -> "La contraseña nueva debe ser distinta de la actual."
            else -> ReglasCuenta.problemaContrasena(s.nueva, usuario) ?: if (s.nueva != s.confirmacion) "Las contraseñas no coinciden." else null
        }
        if (problema != null) {
            _estado.update { it.copy(error = problema) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val mensaje = cuentas.cambiarContrasena(s.actual, s.nueva)
                _estado.update { it.copy(cargando = false, listo = mensaje, actual = "", nueva = "", confirmacion = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }
}

// =========================================================================== AVISO DE PRIVACIDAD (USR-21)

data class AvisoPrivacidadUiState(
    val cargando: Boolean = true,
    val aviso: AvisoPrivacidad? = null,
    val leido: Boolean = false,
    val enviando: Boolean = false,
    val error: String? = null,
)

class AvisoPrivacidadViewModel(private val cuentas: CuentaRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(AvisoPrivacidadUiState())
    val estado: StateFlow<AvisoPrivacidadUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val aviso = cuentas.avisoPrivacidad()
                _estado.update { it.copy(cargando = false, aviso = aviso) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = mensajeDe(e)) }
            }
        }
    }

    fun cambiarLeido(valor: Boolean) = _estado.update { it.copy(leido = valor) }

    /** Al aceptarse, la sesión ya no tiene pendientes y la navegación abre la vista del paciente. */
    fun aceptar() {
        val s = _estado.value
        val aviso = s.aviso ?: return
        if (s.enviando || !s.leido) return
        _estado.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                cuentas.aceptarAviso(aviso.version)
                _estado.update { it.copy(enviando = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(enviando = false, error = mensajeDe(e)) }
            }
        }
    }
}

// =========================================================================== INVITACIONES (recepción)

data class InvitacionesUiState(
    val nombre: String = "",
    val cargando: Boolean = false,
    val creando: Boolean = false,
    val error: String? = null,
    /** La que se acaba de generar, para mostrar el código en grande. */
    val nueva: Invitacion? = null,
    val recientes: List<Invitacion> = emptyList(),
)

class InvitacionesViewModel(private val cuentas: CuentaRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(InvitacionesUiState())
    val estado: StateFlow<InvitacionesUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true) }
        viewModelScope.launch {
            try {
                val lista = cuentas.invitaciones()
                _estado.update { it.copy(cargando = false, recientes = lista.take(5)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false) } // la lista es secundaria; crear sigue disponible
            }
        }
    }

    fun cambiarNombre(t: String) = _estado.update { it.copy(nombre = t.take(80), error = null) }

    fun crear() {
        val s = _estado.value
        if (s.creando) return
        val nombre = s.nombre.trim().replace(Regex("\\s+"), " ")
        if (nombre.length < 5 || !nombre.contains(' ')) {
            _estado.update { it.copy(error = "Escribe el nombre completo del paciente (nombre y apellidos).") }
            return
        }
        _estado.update { it.copy(creando = true, error = null) }
        viewModelScope.launch {
            try {
                val invitacion = cuentas.crearInvitacion(nombre)
                _estado.update { it.copy(creando = false, nueva = invitacion, nombre = "", recientes = (listOf(invitacion) + it.recientes).take(5)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(creando = false, error = mensajeDe(e)) }
            }
        }
    }

    fun ocultarNueva() = _estado.update { it.copy(nueva = null) }
}
