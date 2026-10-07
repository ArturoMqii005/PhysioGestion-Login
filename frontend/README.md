# PhysioGestion · Frontend (Kotlin Multiplatform)

App de PhysioGestion hecha con **Kotlin Multiplatform + Compose Multiplatform** en Android Studio.
Una sola base de código para Android, escritorio e iOS. La arquitectura es **MVVM**.

- **Login** contra el backend Ktor, con las credenciales de `usuarios.json`.
- **Navegación por rol:** cada perfil abre su propia vista.
- **Consumo de API** con Ktor Client y kotlinx.serialization.
- **Almacenamiento local** SQLite, para que el paciente use su rutina sin conexión.

## Estructura de carpetas

```
frontend/
├── androidApp/                 App Android: MainActivity.kt, manifiesto, permiso de red, íconos
├── desktopApp/                 App de escritorio: main.kt abre una ventana con App()
├── iosApp/                     Proyecto de Xcode (se abre en una Mac)
├── gradle/libs.versions.toml   Versiones de todas las librerías
└── shared/src/
    ├── commonMain/             CÓDIGO COMPARTIDO por todas las plataformas
    │   └── kotlin/com/terapid/physiogestion/
    │       ├── App.kt                Punto de entrada común: tema + navegación
    │       ├── navegacion/           Rutas.kt (rutas y rutaInicialPara(rol)) y NavegacionPrincipal.kt (NavHost)
    │       ├── vista/                VISTA: LoginPantalla, AdministradorPantalla, FisioterapeutaPantalla,
    │       │   │                     RecepcionPantalla, RutinaPantalla (paciente)
    │       │   ├── componentes/      Piezas reutilizables: encabezado del rol, tarjetas, etiquetas
    │       │   └── tema/             Colores de PhysioGestion
    │       ├── viewmodel/            VIEW MODEL: Login, Sesion, Administrador, Agenda, Rutina
    │       ├── modelo/               MODELO: DTOs @Serializable (Usuario, Rol, LoginSolicitud, Cita, Rutina…)
    │       ├── servicio/             Reglas de negocio: cumplimiento, alerta de dolor, puntos
    │       ├── datos/                Repositorios y GestorSesion (token de la sesión)
    │       │   ├── remoto/           Ktor Client: ClienteHttp, AutenticacionApi, ClinicaApi, RutinaApi, errores
    │       │   └── local/            SQLite: BaseDatosLocal y AlmacenamientoLocal
    │       ├── dependencias/         Crea y conecta los objetos (inyección manual)
    │       └── plataforma/           expect: lo que cambia por plataforma
    ├── androidMain/            CÓDIGO ESPECÍFICO de Android (actual)
    ├── jvmMain/                CÓDIGO ESPECÍFICO de escritorio (actual)
    ├── iosMain/                CÓDIGO ESPECÍFICO de iOS (actual)
    ├── commonTest/             Pruebas de navegación por rol y del servicio
    └── jvmTest/                Pruebas de login y rutina con backend simulado y SQLite
```

## Navegación por rol

`navegacion/NavegacionPrincipal.kt` observa la sesión:

- **Sin sesión:** se muestra `RutaLogin`.
- **Al iniciar sesión:** `rutaInicialPara(rol)` decide el destino.

| Rol | Ruta | Vista | Datos que consume |
|---|---|---|---|
| ADMINISTRADOR | `RutaAdministrador` | `AdministradorPantalla` | `GET /api/v1/usuarios` |
| FISIOTERAPEUTA | `RutaFisioterapeuta` | `FisioterapeutaPantalla` | `GET /api/v1/citas` (solo las suyas) |
| RECEPCIONISTA | `RutaRecepcion` | `RecepcionPantalla` | `GET /api/v1/citas` (todas) |
| PACIENTE | `RutaPaciente` | `RutinaPantalla` | `GET /api/v1/pacientes/{id}/rutina` |

Al navegar se borra el historial. Así el botón «atrás» no regresa al login ni a la vista de otro usuario.
Al cerrar sesión, o si el servidor rechaza el token, la app vuelve al login.

## Código compartido y código específico

| Declaración común (`expect`) | Android | Escritorio | iOS |
|---|---|---|---|
| `urlServidorPorDefecto()` | `http://10.0.2.2:8080` (emulador) | `http://localhost:8080` | `http://localhost:8080` |
| `crearMotorHttp()` | OkHttp | Java HttpClient | Darwin |
| `rutaBaseDatos()` | carpeta privada de la app | `~/.physiogestion/` | carpeta Documents |
| `formatearFechaHora()` | `SimpleDateFormat` es-MX | `SimpleDateFormat` es-MX | `NSDateFormatter` es_MX |

Permisos de red de la etapa local:
- **Android:** `res/xml/network_security_config.xml`.
- **iOS:** `NSAllowsLocalNetworking` en `Info.plist`.

## Ejecutar

1. Encender el backend (ver `../backend/README.md`).
2. En Android Studio elegir **androidApp**, elegir un emulador y presionar Run.
3. Pruebas: `gradlew.bat :shared:jvmTest`.
