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
import com.terapid.physiogestion.modelo.Usuario
import com.terapid.physiogestion.viewmodel.AdministradorViewModel
import com.terapid.physiogestion.viewmodel.AgendaViewModel
import com.terapid.physiogestion.viewmodel.DetalleSesionViewModel
import com.terapid.physiogestion.viewmodel.EjercicioViewModel
import com.terapid.physiogestion.viewmodel.HistorialViewModel
import com.terapid.physiogestion.viewmodel.InicioPacienteViewModel
import com.terapid.physiogestion.viewmodel.LoginViewModel
import com.terapid.physiogestion.viewmodel.PerfilViewModel
import com.terapid.physiogestion.viewmodel.RutinaViewModel
import com.terapid.physiogestion.viewmodel.SesionViewModel
import com.terapid.physiogestion.vista.AdministradorPantalla
import com.terapid.physiogestion.vista.DetalleSesionPantalla
import com.terapid.physiogestion.vista.EjercicioPantalla
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
        val destino: Any = sesion?.let { rutaInicialPara(it.usuario.rol) } ?: RutaLogin
        val actual = navegador.currentDestination ?: return@LaunchedEffect
        if (actual.hasRoute(destino::class)) return@LaunchedEffect
        navegador.navigate(destino) {
            popUpTo(navegador.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navegador, startDestination = RutaLogin) {
        composable<RutaLogin> {
            LoginPantalla(viewModel { LoginViewModel(Dependencias.autenticacion, Dependencias.preferencias) })
        }
        composable<RutaAdministrador> {
            val usuario = sesion?.usuario ?: return@composable
            AdministradorPantalla(
                usuario = usuario,
                viewModel = viewModel { AdministradorViewModel(Dependencias.clinica) },
                alCerrarSesion = sesionVm::cerrarSesion,
            )
        }
        composable<RutaFisioterapeuta> {
            val usuario = sesion?.usuario ?: return@composable
            FisioterapeutaPantalla(
                usuario = usuario,
                viewModel = viewModel { AgendaViewModel(Dependencias.clinica, conSeguimiento = true) },
                alCerrarSesion = sesionVm::cerrarSesion,
            )
        }
        composable<RutaRecepcion> {
            val usuario = sesion?.usuario ?: return@composable
            RecepcionPantalla(
                usuario = usuario,
                viewModel = viewModel { AgendaViewModel(Dependencias.clinica) },
                alCerrarSesion = sesionVm::cerrarSesion,
            )
        }

        // ---------------- Recorrido del PACIENTE ----------------

        composable<RutaPaciente> {
            val usuario = sesion?.usuario ?: return@composable
            InicioPacientePantalla(
                usuario = usuario,
                viewModel = viewModel {
                    InicioPacienteViewModel(
                        pacienteId = usuario.pacienteId ?: 0,
                        rutinas = Dependencias.rutinas,
                        sesiones = Dependencias.sesiones,
                        sensores = Dependencias.sensores,
                        preferencias = Dependencias.preferencias,
                        sensorPasos = Dependencias.sensorPasos,
                        permisos = Dependencias.permisos,
                    )
                },
                alAbrirRutina = { navegador.navigate(RutaRutina) },
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
                    )
                },
                alRegresar = { navegador.popBackStack() },
                alTerminar = { completado ->
                    rutinaVm?.medicionRegistrada(ruta.idEjercicio, completado)
                    navegador.popBackStack()
                },
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
                    )
                },
                alRegresar = { navegador.popBackStack() },
                alCerrarSesion = sesionVm::cerrarSesion,
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
