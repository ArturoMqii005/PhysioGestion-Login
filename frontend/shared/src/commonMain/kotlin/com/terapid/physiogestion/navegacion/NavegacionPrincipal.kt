package com.terapid.physiogestion.navegacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.terapid.physiogestion.dependencias.Dependencias
import com.terapid.physiogestion.viewmodel.AdministradorViewModel
import com.terapid.physiogestion.viewmodel.AgendaViewModel
import com.terapid.physiogestion.viewmodel.LoginViewModel
import com.terapid.physiogestion.viewmodel.RutinaViewModel
import com.terapid.physiogestion.viewmodel.SesionViewModel
import com.terapid.physiogestion.vista.AdministradorPantalla
import com.terapid.physiogestion.vista.FisioterapeutaPantalla
import com.terapid.physiogestion.vista.LoginPantalla
import com.terapid.physiogestion.vista.RecepcionPantalla
import com.terapid.physiogestion.vista.RutinaPantalla

/**
 * Navegación principal de la app.
 *
 * 1. Arranca en el login.
 * 2. Cuando se abre la sesión, enruta a la vista del rol (rutaInicialPara).
 * 3. Al cerrar la sesión (o si el servidor la invalida) regresa al login.
 * En ambos casos se borra el historial, así el botón "atrás" no regresa
 * a una vista de otro usuario.
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
            LoginPantalla(viewModel { LoginViewModel(Dependencias.autenticacion) })
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
                viewModel = viewModel { AgendaViewModel(Dependencias.clinica) },
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
        composable<RutaPaciente> {
            val usuario = sesion?.usuario ?: return@composable
            RutinaPantalla(
                usuario = usuario,
                viewModel = viewModel {
                    RutinaViewModel(
                        repositorio = Dependencias.rutinas,
                        servicio = Dependencias.servicio,
                        pacienteId = usuario.pacienteId ?: 0,
                    )
                },
                alCerrarSesion = sesionVm::cerrarSesion,
            )
        }
    }
}
