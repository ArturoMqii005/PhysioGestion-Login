# PhysioGestion · Frontend (Kotlin Multiplatform)

App de PhysioGestion hecha con **Kotlin Multiplatform + Compose Multiplatform** en Android Studio.
Una sola base de código para Android, escritorio e iOS. La arquitectura es **MVVM**.

- **Login** contra el backend Ktor, con las credenciales de `usuarios.json`. La sesión queda guardada (token cifrado).
- **Navegación por rol:** cada perfil abre su propia vista; el paciente tiene su propio recorrido de pantallas.
- **Consumo de API** con Ktor Client y kotlinx.serialization, con los cinco métodos HTTP (GET, POST, PUT, PATCH, DELETE).
- **Almacenamiento local** en cuatro formas: preferencias, base de datos SQLite, archivos y almacén cifrado.
- **Sensores:** acelerómetro y giroscopio para contar repeticiones y medir el ángulo; contador de pasos.
- **Variables de entorno:** ambiente, servidor y versión en un solo lugar (`Entorno`), y la IP del backend en `local.properties`.

## Estructura de carpetas

```
frontend/
├── androidApp/                 App Android: MainActivity.kt, manifiesto (permisos y sensores), íconos
├── desktopApp/                 App de escritorio: main.kt abre una ventana con App()
├── iosApp/                     Proyecto de Xcode (se abre en una Mac); Info.plist con textos de permisos
├── local.properties.ejemplo    Variables de entorno de cada computadora (SERVIDOR_URL)
├── gradle/libs.versions.toml   Versiones de todas las librerías
└── shared/src/
    ├── commonMain/             CÓDIGO COMPARTIDO por todas las plataformas
    │   └── kotlin/com/terapid/physiogestion/
    │       ├── App.kt                Punto de entrada común: tema + navegación
    │       ├── navegacion/           Rutas.kt (rutas tipadas) y NavegacionPrincipal.kt (NavHost)
    │       ├── vista/                VISTA: Login, Administrador, Fisioterapeuta, Recepción y, del paciente,
    │       │   │                     InicioPaciente, Rutina, Ejercicio (sensores), Historial, DetalleSesion, Perfil
    │       │   ├── componentes/      Piezas reutilizables: encabezados, tarjetas, etiquetas, selectores
    │       │   └── tema/             Colores de PhysioGestion
    │       ├── viewmodel/            VIEW MODEL de cada pantalla
    │       ├── modelo/               MODELO: DTOs @Serializable (Usuario, Rol, Cita, Rutina, SesionTerapia…)
    │       ├── servicio/             Reglas de negocio: cumplimiento, dolor, puntos, semáforo, racha, pasos
    │       │   └── movimiento/       PROCESAMIENTO de sensores: AnalizadorMovimiento, RangoArticular
    │       ├── datos/                Repositorios y GestorSesion
    │       │   ├── remoto/           Ktor Client: ClienteHttp, SesionApi (5 métodos), EjercicioApi, errores
    │       │   ├── local/            SQLite, preferencias, archivos, almacén cifrado, sesión guardada
    │       │   └── sensores/         SensorMovimiento, SensorPasos (expect), LecturaEje, FiltroSenal
    │       ├── dependencias/         Crea y conecta los objetos (inyección manual)
    │       └── plataforma/           Entorno, Plataforma, permisos y las demás declaraciones expect
    ├── androidMain/            CÓDIGO ESPECÍFICO de Android (actual)
    ├── jvmMain/                CÓDIGO ESPECÍFICO de escritorio (actual)
    ├── iosMain/                CÓDIGO ESPECÍFICO de iOS (actual)
    ├── commonTest/             Pruebas de navegación, servicio, filtro y análisis de movimiento
    └── jvmTest/                Pruebas con backend simulado (MockEngine) y SQLite real
```

## Navegación

`navegacion/NavegacionPrincipal.kt` observa la sesión: sin sesión muestra `RutaLogin`; al iniciar sesión,
`rutaInicialPara(rol)` decide el destino y se borra el historial (el botón «atrás» no regresa al login).

| Rol | Ruta | Vista | Datos que consume |
|---|---|---|---|
| ADMINISTRADOR | `RutaAdministrador` | `AdministradorPantalla` | `GET /usuarios` |
| FISIOTERAPEUTA | `RutaFisioterapeuta` | `FisioterapeutaPantalla` | `GET /citas` (solo las suyas) y `GET /sesiones` (seguimiento remoto con semáforo) |
| RECEPCIONISTA | `RutaRecepcion` | `RecepcionPantalla` | `GET /citas` (todas) |
| PACIENTE | `RutaPaciente` | `InicioPacientePantalla` | rutina, racha, pasos y cola de envío |

Recorrido del paciente (las rutas con datos son `data class` y el argumento viaja dentro de la ruta):

```
RutaPaciente (inicio) ─┬─► RutaRutina ─► RutaEjercicio(idEjercicio)   medición con sensores
                       ├─► RutaHistorial ─► RutaDetalleSesion(idSesion) GET · PATCH · PUT · DELETE
                       └─► RutaPerfil                                  almacenamiento, entorno y permisos
```

La pantalla de ejercicio comparte el `RutinaViewModel` de la pantalla de rutina: al terminar, el ejercicio queda marcado.

## Métodos HTTP (recurso `/api/v1/sesiones`)

| Método | Dónde se usa en la app | Archivo |
|---|---|---|
| GET | Historial (`?idPaciente=&pagina=&tamano=`) y detalle de una sesión | `datos/remoto/SesionApi.kt` |
| POST | «Guardar sesión»: primero se guarda en SQLite y luego se envía con `Idempotency-Key` (un reintento no duplica) | `SesionApi.registrar` |
| PUT | Detalle → «Editar todos los datos»: reemplaza dolor, dificultad y nota | `SesionApi.reemplazar` |
| PATCH | Detalle → «Corregir la nota»: solo viaja `{"observacion": …}` | `SesionApi.modificar` |
| DELETE | Detalle → «Eliminar sesión registrada por error» (204) | `SesionApi.eliminar` |

`datos/remoto/ErroresApi.kt` traduce cada código (400, 401, 403, 404, 409, 422, 429, 5xx) a un mensaje claro.
En el ambiente de desarrollo, cada pantalla muestra abajo el método, la ruta y el código de la última petición.

## Almacenamiento local

| Tipo | Para qué | Archivo común | Android | Escritorio | iOS |
|---|---|---|---|---|---|
| Preferencias | racha, servidor recordado, cámara, base del contador de pasos | `AlmacenPreferencias` + `ClavesAlmacen` | SharedPreferences | java.util.prefs | NSUserDefaults |
| Base de datos | rutina sin conexión, sesiones, cola de envío, mediciones | `BaseDatosLocal` (versión 2, con migración) | SQLite | SQLite | SQLite |
| Archivos | caché de videos de los ejercicios (límite 200 MB) | `AlmacenArchivos` | cacheDir | `~/.physiogestion/cache` | Caches |
| Cifrado | token de la sesión | `AlmacenSeguro` + `PersistenciaSesion` | Android Keystore (AES-GCM) | preferencias | NSUserDefaults (Llavero pendiente) |

## Sensores

| Fase | Qué se hace | Dónde |
|---|---|---|
| Habilitación | se revisa si hay acelerómetro/giroscopio; permisos de cámara y actividad física | `SensorMovimiento`, `GestorPermisos`, `AndroidManifest.xml` |
| Calibración | 1.5 s quieto: gravedad de la postura inicial y sesgo del giroscopio | `FiltroSenal.calibrar` |
| Obtención | lecturas a 50 Hz como `Flow`; el sensor se apaga al salir de la pantalla | `SensorMovimiento.android.kt` / `.ios.kt` |
| Procesamiento | filtro paso bajo, fusión acelerómetro + giroscopio, ángulo y conteo con histéresis | `AnalizadorMovimiento`, `RangoArticular` |

Solo viaja al servidor el resumen (6 campos) con `POST /resultados-ejercicio`. En un dispositivo sin acelerómetro
(la computadora) la pantalla ofrece contar las repeticiones a mano. En el emulador se pueden probar con
**⋯ Extended controls → Virtual sensors**, girando el dispositivo y regresándolo.

El contador de pasos (`SensorPasos`) aparece en el inicio del paciente; en Android pide `ACTIVITY_RECOGNITION`.

## Variables de entorno

- `plataforma/Entorno.kt`: ambiente (DESARROLLO / PRUEBAS / PRODUCCIÓN), servidor, tiempos de espera y si se registran las peticiones.
- `plataforma/Plataforma.kt` (expect): depuración, nombre del sistema y versión de la app.
- `local.properties` (no se sube): `SERVIDOR_URL=http://localhost:8080` para un celular con `adb reverse`, o la IP de la computadora.
  Gradle lo entrega como `BuildConfig.SERVIDOR_URL`. Ver `local.properties.ejemplo`.

## Código compartido y código específico

| Declaración común (`expect`) | Android | Escritorio | iOS |
|---|---|---|---|
| `urlServidorPorDefecto()` | `BuildConfig.SERVIDOR_URL` o `http://10.0.2.2:8080` | `http://localhost:8080` | `http://localhost:8080` |
| `crearMotorHttp()` | OkHttp | Java HttpClient | Darwin |
| `rutaBaseDatos()` | carpeta privada de la app | `~/.physiogestion/` | carpeta Documents |
| `Plataforma` | BuildConfig + Build.VERSION | propiedades de Java | Info.plist + UIDevice |
| `GestorPermisos` | ActivityResult (MainActivity) | no aplica | AVFoundation / CoreMotion |
| `SensorMovimiento` / `SensorPasos` | SensorManager | no hay sensores | CoreMotion |

## Ejecutar

1. Encender el backend (ver `../backend/README.md`).
2. En Android Studio elegir **androidApp**, elegir un emulador y presionar Run.
3. Escritorio: `gradlew.bat :desktopApp:run`.
4. Pruebas: `gradlew.bat :shared:jvmTest` (54 pruebas).
