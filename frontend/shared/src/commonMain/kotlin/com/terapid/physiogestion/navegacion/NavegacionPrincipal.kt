package com.terapid.physiogestion.navegacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.terapid.physiogestion.dependencias.Dependencias
import com.terapid.physiogestion.modelo.Aviso
import com.terapid.physiogestion.modelo.Rol
import com.terapid.physiogestion.modelo.TipoAviso
import com.terapid.physiogestion.servicio.ReglasAvisos
import com.terapid.physiogestion.viewmodel.AvisosViewModel
import com.terapid.physiogestion.viewmodel.ResumenAvisosViewModel
import com.terapid.physiogestion.vista.AvisosPantalla
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.claveDelDia
import com.terapid.physiogestion.viewmodel.AdministradorViewModel
import com.terapid.physiogestion.viewmodel.AgendaViewModel
import com.terapid.physiogestion.viewmodel.AgendarCitaViewModel
import com.terapid.physiogestion.viewmodel.EditarRutinaViewModel
import com.terapid.physiogestion.viewmodel.FichaPacienteViewModel
import com.terapid.physiogestion.viewmodel.MisCitasViewModel
import com.terapid.physiogestion.viewmodel.PacientesViewModel
import com.terapid.physiogestion.viewmodel.AvisoPrivacidadViewModel
import com.terapid.physiogestion.viewmodel.CambiarContrasenaViewModel
import com.terapid.physiogestion.viewmodel.InvitacionesViewModel
import com.terapid.physiogestion.viewmodel.RecuperarContrasenaViewModel
import com.terapid.physiogestion.viewmodel.RegistroViewModel
import com.terapid.physiogestion.viewmodel.VerificarCorreoViewModel
import com.terapid.physiogestion.viewmodel.DetalleSesionViewModel
import com.terapid.physiogestion.viewmodel.EjercicioViewModel
import com.terapid.physiogestion.viewmodel.EscalerasViewModel
import com.terapid.physiogestion.viewmodel.HistorialViewModel
import com.terapid.physiogestion.viewmodel.InicioPacienteViewModel
import com.terapid.physiogestion.viewmodel.LoginViewModel
import com.terapid.physiogestion.viewmodel.PerfilViewModel
import com.terapid.physiogestion.viewmodel.RutinaViewModel
import com.terapid.physiogestion.viewmodel.SesionViewModel
import com.terapid.physiogestion.vista.AdministradorPantalla
import com.terapid.physiogestion.vista.AgendarCitaPantalla
import com.terapid.physiogestion.vista.EditarRutinaPantalla
import com.terapid.physiogestion.vista.FichaPacientePantalla
import com.terapid.physiogestion.vista.PacientesPantalla
import com.terapid.physiogestion.vista.AvisoPrivacidadPantalla
import com.terapid.physiogestion.vista.CambiarContrasenaPantalla
import com.terapid.physiogestion.vista.RecuperarContrasenaPantalla
import com.terapid.physiogestion.vista.RegistroPantalla
import com.terapid.physiogestion.vista.VerificarCorreoPantalla
import com.terapid.physiogestion.vista.DetalleSesionPantalla
import com.terapid.physiogestion.vista.EjercicioPantalla
import com.terapid.physiogestion.vista.EscalerasPantalla
import com.terapid.physiogestion.vista.FisioterapeutaPantalla
import com.terapid.physiogestion.vista.HistorialPantalla
import com.terapid.physiogestion.vista.InicioPacientePantalla
import com.terapid.physiogestion.vista.LoginPantalla
import com.terapid.physiogestion.vista.PerfilPantalla
import com.terapid.physiogestion.vista.RecepcionPantalla
import com.terapid.physiogestion.vista.RutinaPantalla

/**
 * Navegación principal de la app.
 *
 * 1. Arranca en el login (o, si había una sesión guardada, en la vista de su rol).
 * 2. Cuando se abre la sesión, enruta a la vista del rol (rutaInicialPara).
 * 3. Al cerrar la sesión (o si el servidor la invalida) regresa al login.
 * En ambos casos se borra el historial, así el botón "atrás" no regresa
 * a una vista de otro usuario.
 *
 * El paciente tiene además su propio recorrido: Inicio → Rutina → Ejercicio
 * (sensores), Inicio → Historial → Detalle de sesión, e Inicio → Perfil.
 * Cada pantalla recibe funciones (alRegresar, alAbrir…), nunca el
 * controlador: así no depende de la biblioteca de navegación.
 */
@Composable
fun NavegacionPrincipal() {
    val navegador = rememberNavController()
    val sesionVm = viewModel { SesionViewModel(Dependencias.autenticacion) }
    val sesion by sesionVm.sesion.collectAsStateWithLifecycle()

    LaunchedEffect(sesion) {
        // Primero lo que falta (aviso de privacidad, contraseña temporal) y después la vista del rol.
        val destino: Any = sesion?.let(::rutaPara) ?: RutaLogin
        val actual = navegador.currentDestination ?: return@LaunchedEffect
        if (actual.hasRoute(destino::class)) return@LaunchedEffect
        navegador.navigate(destino) {
            popUpTo(navegador.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Notificaciones (NOT-01..04): con la sesión abierta, Android revisa los avisos en
    // segundo plano y programa el recordatorio de la rutina; al cerrarla se cancela todo.
    val usuarioActivo = sesion?.takeIf { !it.requiereConsentimiento && !it.debeCambiarContrasena }?.usuario
    LaunchedEffect(usuarioActivo?.id) {
        val notificador = Dependencias.notificador
        if (usuarioActivo == null) {
            notificador.detenerTodo()
            Dependencias.avisos.limpiar()
            return@LaunchedEffect
        }
        notificador.iniciarRevision(usuarioActivo.rol)
        val pacienteId = usuarioActivo.pacienteId
        if (usuarioActivo.rol == Rol.PACIENTE && pacienteId != null) {
            notificador.programarRecordatorioRutina(Dependencias.preferencias.recordatorioRutina(pacienteId))
        }
    }

    NavHost(navController = navegador, startDestination = RutaLogin) {
        composable<RutaLogin> {
            LoginPantalla(
                viewModel { LoginViewModel(Dependencias.autenticacion, Dependencias.preferencias) },
                alCrearCuenta = { navegador.navigate(RutaRegistro) },
                alOlvidarContrasena = { navegador.navigate(RutaRecuperarContrasena) },
                alVerificarCorreo = { correo -> navegador.navigate(RutaVerificarCorreo(correo)) },
            )
        }

        // ---------------- Cuentas (USR-01, USR-10, USR-11, USR-12, USR-20, USR-21) ----------------

        composable<RutaRegistro> {
            RegistroPantalla(
                viewModel = viewModel { RegistroViewModel(Dependencias.cuentas) },
                alRegresar = { navegador.popBackStack() },
                // Al crear la cuenta se reemplaza el registro por la verificación: «atrás» regresa al login.
                alRegistrado = { correo ->
                    navegador.navigate(RutaVerificarCorreo(correo)) { popUpTo<RutaLogin>() }
                },
            )
        }
        composable<RutaVerificarCorreo> { entrada ->
            val ruta = entrada.toRoute<RutaVerificarCorreo>()
            VerificarCorreoPantalla(
                viewModel = viewModel { VerificarCorreoViewModel(ruta.correo, Dependencias.cuentas) },
                alRegresar = { navegador.popBackStack() },
                alTerminar = { navegador.popBackStack(RutaLogin, inclusive = false) },
            )
        }
        composable<RutaRecuperarContrasena> {
            RecuperarContrasenaPantalla(
                viewModel = viewModel { RecuperarContrasenaViewModel(Dependencias.cuentas) },
                alRegresar = { navegador.popBackStack() },
            )
        }
        composable<RutaAvisoPrivacidad> {
            AvisoPrivacidadPantalla(
                viewModel = viewModel { AvisoPrivacidadViewModel(Dependencias.cuentas) },
                alRechazar = sesionVm::cerrarSesion,
            )
        }
        composable<RutaCambiarContrasena> { entrada ->
            val ruta = entrada.toRoute<RutaCambiarContrasena>()
            CambiarContrasenaPantalla(
                viewModel = viewModel { CambiarContrasenaViewModel(ruta.obligatorio, Dependencias.cuentas, sesion?.usuario?.usuario) },
                alRegresar = { navegador.popBackStack() },
                alCerrarSesion = sesionVm::cerrarSesion,
            )
        }
        composable<RutaAdministrador> {
            val usuario = sesion?.usuario ?: return@composable
            AdministradorPantalla(
                usuario = usuario,
                viewModel = viewModel { AdministradorViewModel(Dependencias.clinica, Dependencias.cuentas, usuario.id) },
                alCerrarSesion = sesionVm::cerrarSesion,
                alCambiarContrasena = { navegador.navigate(RutaCambiarContrasena(obligatorio = false)) },
            )
        }
        composable<RutaFisioterapeuta> {
            val usuario = sesion?.usuario ?: return@composable
            FisioterapeutaPantalla(
                usuario = usuario,
                viewModel = viewModel { AgendaViewModel(Dependencias.clinica, conSeguimiento = true) },
                alCerrarSesion = sesionVm::cerrarSesion,
                alCambiarContrasena = { navegador.navigate(RutaCambiarContrasena(obligatorio = false)) },
                alAbrirPacientes = { navegador.navigate(RutaPacientes) },
                avisos = resumenAvisos(),
                alAbrirAvisos = { navegador.navigate(RutaAvisos) },
            )
        }
        composable<RutaRecepcion> {
            val usuario = sesion?.usuario ?: return@composable
            RecepcionPantalla(
                usuario = usuario,
                viewModel = viewModel { AgendaViewModel(Dependencias.clinica) },
                alCerrarSesion = sesionVm::cerrarSesion,
                invitaciones = viewModel { InvitacionesViewModel(Dependencias.cuentas) },
                alCambiarContrasena = { navegador.navigate(RutaCambiarContrasena(obligatorio = false)) },
                alAgendar = { navegador.navigate(RutaAgendarCita) },
                alReprogramar = { cita -> navegador.navigate(cita.aRutaReprogramar()) },
                alAbrirPacientes = { navegador.navigate(RutaPacientes) },
                avisos = resumenAvisos(),
                alAbrirAvisos = { navegador.navigate(RutaAvisos) },
            )
        }

        // ---------------- Avisos (NOT-01..04) ----------------

        composable<RutaAvisos> {
            val usuario = sesion?.usuario ?: return@composable
            val esPaciente = usuario.rol == Rol.PACIENTE
            AvisosPantalla(
                viewModel = viewModel { AvisosViewModel(Dependencias.avisos, { Dependencias.notificador }, Dependencias.permisos) },
                motivoNotificaciones = when (usuario.rol) {
                    Rol.FISIOTERAPEUTA -> "Te avisamos en cuanto un paciente termine su sesión con dolor alto, aunque tengas la app cerrada."
                    Rol.PACIENTE -> "Te recordamos tus citas un día y una hora antes, y tu rutina a la hora que elijas en tu perfil."
                    else -> "Te avisamos cuando un paciente cancele su cita desde la app."
                },
                textoVacio = when (usuario.rol) {
                    Rol.FISIOTERAPEUTA -> "Sin avisos. Aquí aparecerán las sesiones con dolor alto, los pacientes nuevos y los cambios en tus citas."
                    Rol.PACIENTE -> "Sin avisos. Aquí aparecerán tus citas nuevas o reprogramadas y los cambios en tu rutina."
                    else -> "Sin avisos. Aquí aparecerán las citas que cancelen los pacientes desde la app."
                },
                destino = { aviso -> destinoDeAviso(aviso, esPaciente) },
                alRegresar = { navegador.popBackStack() },
                alAbrirAviso = { aviso ->
                    when {
                        destinoDeAviso(aviso, esPaciente) == null -> Unit
                        !esPaciente -> aviso.pacienteId?.let { navegador.navigate(RutaFichaPaciente(it)) }
                        aviso.tipo == TipoAviso.RUTINA_ACTUALIZADA -> navegador.navigate(RutaRutina)
                        else -> navegador.popBackStack() // las citas se ven en el inicio del paciente
                    }
                },
            )
        }

        // ---------------- Operación clínica (CIT-05..08, PAC-02..04, RUT-08) ----------------

        composable<RutaAgendarCita> {
            val agenda = agendaDeRecepcion(navegador)
            AgendarCitaPantalla(
                viewModel = viewModel { AgendarCitaViewModel(Dependencias.clinica) },
                alRegresar = { navegador.popBackStack() },
                alGuardar = { cita ->
                    navegador.popBackStack()
                    agenda?.mostrar(cita)
                },
            )
        }
        composable<RutaReprogramarCita> { entrada ->
            val ruta = entrada.toRoute<RutaReprogramarCita>()
            val agenda = agendaDeRecepcion(navegador)
            AgendarCitaPantalla(
                viewModel = viewModel { AgendarCitaViewModel(Dependencias.clinica, reprogramar = ruta.aCita()) },
                alRegresar = { navegador.popBackStack() },
                alGuardar = { cita ->
                    navegador.popBackStack()
                    agenda?.mostrar(cita)
                },
            )
        }
        composable<RutaPacientes> {
            PacientesPantalla(
                viewModel = viewModel { PacientesViewModel(Dependencias.clinica) },
                hoy = claveDelDia(ahoraEnMilisegundos()),
                alRegresar = { navegador.popBackStack() },
                alAbrir = { id -> navegador.navigate(RutaFichaPaciente(id)) },
            )
        }
        composable<RutaFichaPaciente> { entrada ->
            val usuario = sesion?.usuario ?: return@composable
            val ruta = entrada.toRoute<RutaFichaPaciente>()
            FichaPacientePantalla(
                viewModel = viewModel(entrada) {
                    FichaPacienteViewModel(ruta.pacienteId, Dependencias.clinica, puedeAsignar = usuario.rol != Rol.FISIOTERAPEUTA)
                },
                hoy = claveDelDia(ahoraEnMilisegundos()),
                puedeEditarRutina = usuario.rol == Rol.FISIOTERAPEUTA,
                alRegresar = { navegador.popBackStack() },
                alEditarRutina = { navegador.navigate(RutaEditarRutina(ruta.pacienteId)) },
            )
        }
        composable<RutaEditarRutina> { entrada ->
            val ruta = entrada.toRoute<RutaEditarRutina>()
            val ficha = remember(navegador) { runCatching { navegador.previousBackStackEntry }.getOrNull() }
            EditarRutinaPantalla(
                viewModel = viewModel { EditarRutinaViewModel(ruta.pacienteId, Dependencias.clinica) },
                alRegresar = { navegador.popBackStack() },
                alGuardar = {
                    navegador.popBackStack()
                    // La ficha vuelve a cargar para mostrar la rutina nueva.
                    ficha?.let { e -> runCatching { androidx.lifecycle.ViewModelProvider.create(e)[FichaPacienteViewModel::class].cargar() } }
                },
            )
        }

        // ---------------- Recorrido del PACIENTE ----------------

        composable<RutaPaciente> {
            val usuario = sesion?.usuario ?: return@composable
            InicioPacientePantalla(
                usuario = usuario,
                misCitas = viewModel {
                    MisCitasViewModel(Dependencias.clinica) { citas ->
                        // NOT-02: un recordatorio un día antes y otro una hora antes de cada cita.
                        Dependencias.notificador.programarRecordatoriosDeCitas(
                            ReglasAvisos.recordatoriosDeCitas(citas, claveDelDia(ahoraEnMilisegundos())),
                        )
                    }
                },
                avisos = resumenAvisos(),
                alAbrirAvisos = { navegador.navigate(RutaAvisos) },
                viewModel = viewModel {
                    InicioPacienteViewModel(
                        pacienteId = usuario.pacienteId ?: 0,
                        rutinas = Dependencias.rutinas,
                        sesiones = Dependencias.sesiones,
                        sensores = Dependencias.sensores,
                        preferencias = Dependencias.preferencias,
                        sensorPasos = Dependencias.sensorPasos,
                        permisos = Dependencias.permisos,
                        escaleras = Dependencias.escaleras,
                    )
                },
                alAbrirRutina = { navegador.navigate(RutaRutina) },
                alAbrirEscaleras = { navegador.navigate(RutaEscaleras) },
                alAbrirHistorial = { navegador.navigate(RutaHistorial) },
                alAbrirPerfil = { navegador.navigate(RutaPerfil) },
                alCerrarSesion = sesionVm::cerrarSesion,
            )
        }

        composable<RutaRutina> { entrada ->
            val usuario = sesion?.usuario ?: return@composable
            RutinaPantalla(
                usuario = usuario,
                viewModel = viewModel(entrada) { crearRutinaViewModel(usuario) },
                alRegresar = { navegador.popBackStack() },
                // El argumento viaja dentro de la ruta tipada: RutaEjercicio(idEjercicio = 3).
                alMedirEjercicio = { id -> navegador.navigate(RutaEjercicio(id)) },
                alVerHistorial = { navegador.navigate(RutaHistorial) },
            )
        }

        composable<RutaEjercicio> { entrada ->
            val usuario = sesion?.usuario ?: return@composable
            val ruta = entrada.toRoute<RutaEjercicio>()
            // Se comparte el ViewModel de la rutina para marcar el ejercicio al terminar.
            val rutinaVm = rutinaViewModelCompartido(navegador, usuario)
            EjercicioPantalla(
                viewModel = viewModel {
                    EjercicioViewModel(
                        idEjercicio = ruta.idEjercicio,
                        pacienteId = usuario.pacienteId ?: 0,
                        rutinas = Dependencias.rutinas,
                        repositorio = Dependencias.sensores,
                        sensor = Dependencias.nuevoSensorMovimiento(),
                        permisos = Dependencias.permisos,
                    )
                },
                alRegresar = { navegador.popBackStack() },
                alTerminar = { completado ->
                    rutinaVm?.medicionRegistrada(ruta.idEjercicio, completado)
                    navegador.popBackStack()
                },
            )
        }

        composable<RutaEscaleras> {
            val usuario = sesion?.usuario ?: return@composable
            EscalerasPantalla(
                viewModel = viewModel {
                    EscalerasViewModel(
                        pacienteId = usuario.pacienteId ?: 0,
                        rutinas = Dependencias.rutinas,
                        repositorio = Dependencias.escaleras,
                        barometro = Dependencias.nuevoSensorPresion(),
                        sensorPasos = Dependencias.sensorPasos,
                        permisos = Dependencias.permisos,
                    )
                },
                alRegresar = { navegador.popBackStack() },
            )
        }

        composable<RutaHistorial> {
            HistorialPantalla(
                viewModel = viewModel { HistorialViewModel(Dependencias.sesiones) },
                alRegresar = { navegador.popBackStack() },
                alAbrirSesion = { id -> navegador.navigate(RutaDetalleSesion(id)) },
            )
        }

        composable<RutaDetalleSesion> { entrada ->
            val usuario = sesion?.usuario ?: return@composable
            val ruta = entrada.toRoute<RutaDetalleSesion>()
            DetalleSesionPantalla(
                viewModel = viewModel {
                    DetalleSesionViewModel(ruta.idSesion, Dependencias.sesiones, Dependencias.rutinas, usuario.pacienteId ?: 0)
                },
                alRegresar = { navegador.popBackStack() },
            )
        }

        composable<RutaPerfil> {
            val usuario = sesion?.usuario ?: return@composable
            PerfilPantalla(
                usuario = usuario,
                viewModel = viewModel {
                    PerfilViewModel(
                        pacienteId = usuario.pacienteId ?: 0,
                        preferencias = Dependencias.preferencias,
                        almacenamiento = Dependencias.almacenamiento,
                        archivos = Dependencias.archivos,
                        permisos = Dependencias.permisos,
                        notificador = { Dependencias.notificador },
                    )
                },
                alRegresar = { navegador.popBackStack() },
                alCerrarSesion = sesionVm::cerrarSesion,
                alCambiarContrasena = { navegador.navigate(RutaCambiarContrasena(obligatorio = false)) },
            )
        }
    }
}

private fun crearRutinaViewModel(usuario: Usuario) = RutinaViewModel(
    repositorio = Dependencias.rutinas,
    servicio = Dependencias.servicio,
    pacienteId = usuario.pacienteId ?: 0,
    sesiones = Dependencias.sesiones,
    sensores = Dependencias.sensores,
    preferencias = Dependencias.preferencias,
)

/** ViewModel de la pantalla de rutina que está debajo en la pila, o null si no está. */
@Composable
private fun rutinaViewModelCompartido(navegador: NavHostController, usuario: Usuario): RutinaViewModel? {
    val entradaRutina = remember(navegador) {
        runCatching { navegador.getBackStackEntry<RutaRutina>() }.getOrNull()
    } ?: return null
    return viewModel(entradaRutina) { crearRutinaViewModel(usuario) }
}

/** Contador de avisos de la vista del rol (uno por pantalla, comparte el repositorio). */
@Composable
private fun resumenAvisos(): ResumenAvisosViewModel =
    viewModel { ResumenAvisosViewModel(Dependencias.avisos, { Dependencias.notificador }, Dependencias.permisos) }

/** Texto del enlace de un aviso, o null si solo se marca como leído. */
private fun destinoDeAviso(aviso: Aviso, esPaciente: Boolean): String? = when {
    !esPaciente -> if (aviso.pacienteId != null) "Ver ficha" else null
    aviso.tipo == TipoAviso.RUTINA_ACTUALIZADA -> "Ver mi rutina"
    aviso.tipo in setOf(TipoAviso.CITA_NUEVA, TipoAviso.CITA_REPROGRAMADA, TipoAviso.CITA_CANCELADA) -> "Ver mis citas"
    else -> null
}

/** AgendaViewModel de la pantalla de recepción que está debajo en la pila (para recargarla al volver). */
@Composable
private fun agendaDeRecepcion(navegador: NavHostController): AgendaViewModel? {
    val entrada = remember(navegador) {
        runCatching { navegador.getBackStackEntry<RutaRecepcion>() }.getOrNull()
    } ?: return null
    return viewModel(entrada) { AgendaViewModel(Dependencias.clinica) }
}
