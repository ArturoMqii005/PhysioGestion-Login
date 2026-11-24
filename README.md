# PhysioGestion · App móvil con inicio de sesión por roles

Proyecto de **Desarrollo de Aplicaciones Móviles** para la Clínica Terapid By Farmacias
(Tulancingo de Bravo, Hidalgo).

**Equipo:** Luis Arturo Jiménez Morales · Sebastián Banda Pasquel · Johan Dereck Cabañas Salazar

El proyecto está dividido en dos partes que se ejecutan por separado y se comunican por HTTP con JSON:

|Parte|Carpeta|Tecnología|Qué hace|
|-|-|-|-|
|**Backend**|[`backend/`](backend)|Ktor Server 3.6 (generado con el asistente start.ktor.io), Netty, Docker|API REST en el puerto 8080 (o el de `PORT`), local o desplegada en Railway. Valida las credenciales del archivo JSON y entrega los datos de cada rol|
|**Frontend**|[`frontend/`](frontend)|Kotlin Multiplatform + Compose Multiplatform, Ktor Client|App para Android (Android Studio), escritorio, iOS y **web** (WebAssembly). Login, navegación por rol, sensores, almacenamiento local y los cinco métodos HTTP|

### Carpetas del código compartido (`frontend/shared/src`)

Toda la lógica (pantallas, ViewModels, repositorios, consumo de la API, reglas) está en `commonMain` y se escribe
una sola vez. Cada plataforma solo aporta lo que depende de ella con `expect`/`actual`:

|Carpeta|Para qué plataforma|Qué tiene|
|-|-|-|
|`commonMain`|Todas|La app completa: navegación, pantallas de los cuatro roles, ViewModels, repositorios y API|
|`androidMain`|Android|Sensores, cámara (CameraX + ML Kit), notificaciones, Firebase, permisos|
|`iosMain`|iOS|Preferencias y token (NSUserDefaults), sensores de Core Motion, permisos de cámara|
|`jvmMain`|Escritorio|Preferencias de Java y motor HTTP de Java|
|`webMain`|Navegador|`main.kt` (arranca la app en la página), `index.html`, localStorage, sessionStorage y fetch|
|`sqliteMain`|Android, iOS y escritorio|Base de datos local SQLite (el navegador no tiene SQLite: su historial va en memoria)|

## Arquitectura local

```
┌──────────── FRONTEND (Kotlin Multiplatform) ────────────┐        ┌──────── BACKEND (Ktor Server) ────────┐
│                                                          │        │                                        │
│  LoginPantalla ─► LoginViewModel ─► AutenticacionRepo.   │  JSON  │  POST /api/v1/auth/login               │
│                                       │                  │ ─────► │     │                                  │
│                              AutenticacionApi (Ktor      │        │  AutenticacionServicio                 │
│                              Client + ContentNegotiation)│ ◄───── │     │  valida contra                   │
│                                       │                  │ token  │  resources/datos/usuarios.json         │
│                                 GestorSesion             │ + rol  │                                        │
│                                       │                  │        │  GET /api/v1/usuarios   (admin)        │
│  NavegacionPrincipal ─► rutaInicialPara(rol)             │        │  GET /api/v1/citas      (personal)     │
│      ├─ ADMINISTRADOR  → AdministradorPantalla           │ Bearer │  GET /api/v1/pacientes/{id}/rutina     │
│      ├─ FISIOTERAPEUTA → FisioterapeutaPantalla          │ token  │       (paciente)                       │
│      ├─ RECEPCIONISTA  → RecepcionPantalla               │ ─────► │  GET·POST·PUT·PATCH·DELETE             │
│      └─ PACIENTE       → InicioPacientePantalla          │        │       /api/v1/sesiones                 │
│            ├─ Rutina ─► Ejercicio (sensores)             │        │  POST /api/v1/resultados-ejercicio     │
│            ├─ Historial ─► Detalle de sesión             │        │                                        │
│            └─ Perfil (almacenamiento y entorno)          │        │  citas.json · rutinas.json ·           │
│                                                          │        │  sesiones.json                         │
│  SQLite · preferencias · caché · token cifrado           │        │                                        │
└──────────────────────────────────────────────────────────┘        └────────────────────────────────────────┘
```

1. El usuario escribe su usuario y contraseña en **LoginPantalla**.
2. **LoginViewModel** valida que no estén vacíos y llama al repositorio.
3. **AutenticacionApi** manda `POST /api/v1/auth/login`. Ktor Client convierte el objeto `LoginSolicitud` a JSON.
4. El backend convierte ese JSON en `LoginSolicitud` y lo compara con **usuarios.json**.

   * Si coincide, responde con un `token` y el usuario con su `rol`.
   * Si no coincide, responde `401` con el mensaje de error.
5. **GestorSesion** guarda la sesión. **NavegacionPrincipal** detecta el cambio y, con `rutaInicialPara(rol)`, abre la vista que corresponde a ese rol.
6. Cada vista pide sus datos con el token en el encabezado `Authorization: Bearer <token>`. El backend revisa el rol antes de responder.

## Lo que se agregó en la versión 1.1

Sobre el proyecto del login se integraron las prácticas de la estructura de la app (zip *PhysioGestion_1*), adaptadas al backend real:

|Práctica|Qué se hizo|Dónde|
|-|-|-|
|**Métodos de petición HTTP**|GET, POST, PUT, PATCH y DELETE sobre `/api/v1/sesiones`, con 201 + `Location`, 204, 409, 422 e `Idempotency-Key`|`backend/.../rutas/SesionRutas.kt` · `frontend/.../datos/remoto/SesionApi.kt`|
|**Navegación**|Recorrido del paciente: inicio → rutina → ejercicio, historial → detalle, perfil (rutas tipadas con argumentos)|`frontend/.../navegacion/`|
|**Almacenamiento local**|Preferencias, SQLite (versión 2 con migración), caché de archivos y token cifrado; sesión que se recuerda|`frontend/.../datos/local/`|
|**Sensores**|Acelerómetro + giroscopio: calibración, filtro, fusión, ángulo y conteo de repeticiones; contador de pasos|`frontend/.../datos/sensores/` · `servicio/movimiento/`|
|**Permisos**|Cámara y actividad física con un gestor común (expect/actual)|`frontend/.../plataforma/permisos/`|
|**Variables de entorno**|`Entorno` (ambiente, servidor, tiempos), `BuildConfig.SERVIDOR_URL` desde `local.properties`, `PORT` en el backend|`frontend/.../plataforma/Entorno.kt` · `application.yaml`|
|**Seguimiento remoto**|El fisioterapeuta ve las sesiones que sus pacientes enviaron desde casa, con semáforo|`FisioterapeutaPantalla.kt`|

## Usuarios de prueba (backend/src/main/resources/datos/usuarios.json)

|Rol|Usuario|Contraseña|Vista que abre|
|-|-|-|-|
|Administrador|`admin`|`Admin2026`|Panel de administración: usuarios por perfil y lista de usuarios|
|Fisioterapeuta|`fisio`|`Fisio2026`|Mi agenda de hoy: solo sus citas, con la siguiente resaltada, y el seguimiento remoto de sus pacientes|
|Fisioterapeuta|`fisio2`|`Fisio2026`|Mi agenda de hoy (otra agenda)|
|Recepción|`recepcion`|`Recepcion2026`|Agenda de la clínica, agrupada por fisioterapeuta|
|Paciente|`paciente`|`Paciente2026`|Primero el aviso de privacidad; después Mi tratamiento: rutina con sensores o cámara, escaleras, historial, perfil y pasos|

> Desde la versión 1.2 `usuarios.json` guarda solo el **hash BCrypt** de cada contraseña. También se puede entrar con el correo.

**Paciente nuevo:** en el login, «Crear mi cuenta» con el código de invitación `DEMO-2026` (María López Hernández, paciente 2,
con rutina de la Ft. Mariana Soto). Recepción genera códigos nuevos desde su pantalla. Sin proveedor de correo configurado,
el código de 6 dígitos aparece en el registro del servidor (`railway logs`).

## Lo que se agregó en la versión 1.7 (web con Compose)

|Cambio|Dónde|
|-|-|
|La web es la app de Compose compilada a WebAssembly; se quitó la web de HTML y JavaScript|`shared/src/webMain`, tarea `:shared:publicarWeb`|
|Recepción y administración: **Solicitudes** de cambio de cita (aprobar eligiendo día, hora libre y fisioterapeuta, o rechazar con motivo)|`SolicitudesPantalla` · `SolicitudesViewModel`|
|Recepción y administración: **Anuncios** de la clínica (máximo 3 en 24 h)|`AnunciosPantalla` · `AnunciosViewModel`|
|Administración con pestañas: Usuarios, Agenda, Pacientes, Solicitudes y Anuncios|`NavegacionPrincipal`|
|Menú lateral y contenido centrado en pantallas anchas (web, tabletas)|`RielPestanas` · `NavegacionPrincipal`|
|El historial local se separó en una interfaz común: SQLite en Android, iOS y escritorio; memoria en el navegador|`AlmacenamientoLocal` · `sqliteMain` · `AlmacenamientoEnMemoria`|
|El backend comprime las respuestas con gzip (el WebAssembly baja mucho más rápido)|`backend/.../plugins/Http.kt`|

## Lo que se agregó en la versión 1.6 (reagendar, notificaciones push y Xochi)

<img src="docs/marca/logo-512.png" alt="Logo de PhysioGestion: Xochi, el ajolote" width="96">

|Cambio|Dónde|
|---|---|
|**Reagendar (paciente):** «Cambiar fecha u hora» en sus citas. Más de 24 h antes y hasta 2 veces elige otra hora libre; si no, manda una solicitud que recepción aprueba o rechaza|App · `POST /citas/{id}/reagendar` · `POST /citas/{id}/solicitudes` · Web «Solicitudes»|
|**Reagendar (fisioterapeuta):** «No puedo atender» mueve la cita a otra hora libre o se la pasa a otro fisioterapeuta libre a la misma hora; el motivo le llega al paciente|App y web · `PATCH /citas/{id}` con `nota`|
|**Push con Firebase Cloud Messaging:** cada aviso del servidor (citas, dolor alto, solicitudes, seguridad, pacientes sin actividad) llega al teléfono aunque la app esté cerrada|`PushServicio` · `ServicioMensajes` (Android)|
|**Anuncios de la clínica** por tema de Firebase (pacientes, personal o todos), máximo 3 en 24 h|Web «Anuncios» · `POST /anuncios`|
|**Revisión diaria (push programada):** a las 9:00 GitHub Actions pide al servidor avisar a cada fisioterapeuta qué pacientes llevan 3 días sin hacer su rutina|`.github/workflows/revision-diaria.yml` · `POST /tareas/revision-diaria`|
|**Seguridad:** aviso al entrar desde un teléfono nuevo y al cambiar o restablecer la contraseña|`AvisosServicio.seguridad`|
|**Locales nuevas:** agenda del día (personal, 7:30), racha en riesgo (20:00), resumen semanal (domingo 19:00), prueba de escaleras (lunes, con barómetro), sesiones sin enviar cada 2 h sin red y fin del descanso entre series|`ReglasLocales` (común, con pruebas) · `TrabajosLocales.kt` (WorkManager y AlarmManager)|
|**Xochi, el ajolote:** mascota que explica, anima, descansa y celebra en cada ejercicio, como en una app de idiomas; barra de avance por series, descanso con cuenta regresiva y confeti al terminar|`vista/componentes/Xochi.kt` · `Leccion.kt`|
|**Logo nuevo** con la cara de Xochi en los tonos de la app: icono de Android, ventana de escritorio, favicon y web|`docs/marca/marca.py` genera todos|

**La push no lleva datos de salud:** el mensaje de Firebase solo trae el tipo y el número del aviso. La app lo pide
al servidor con su sesión y entonces muestra el texto. Los avisos de seguridad y los anuncios sí traen su texto
(no tienen información clínica) para mostrarse aunque la sesión ya se haya cerrado.

### Cómo activar Firebase (una sola vez)

Sin estos pasos todo funciona igual, solo que los avisos llegan con la revisión de WorkManager (cada 15 min) en lugar de al instante.

1. En https://console.firebase.google.com crea un proyecto (por ejemplo `physiogestion`) y agrégale una app **Android**
   con el paquete `com.terapid.physiogestion`.
2. Descarga **`google-services.json`** y ponlo en `frontend/androidApp/`. Está en `.gitignore`: **no se sube a GitHub**.
   Con el archivo ahí, Gradle aplica el plugin de Google Services y la app queda conectada.
3. En la consola: ⚙ Configuración del proyecto › **Cuentas de servicio** › «Generar nueva clave privada». Se descarga un JSON
   con una **llave privada**: no lo subas al repositorio ni lo compartas.
4. En Railway (servicio del backend) › Variables, agrega:
   * `FIREBASE_CREDENCIALES` = el contenido completo de ese JSON (o en Base64).
   * `TAREA_CLAVE` = una clave larga al azar (por ejemplo, la salida de `openssl rand -hex 24`).
   * (opcional) `PREFIJO_TEMAS` = `prod`. Sin ella, Railway usa `prod` y la computadora `dev`, así las pruebas locales no le llegan a la clínica.
5. En GitHub › Settings › Secrets and variables › Actions › New repository secret: `TAREA_CLAVE` con la misma clave del paso 4.
6. Revisa `https://…railway.app/api/v1/salud`: debe decir `"pushConfigurado": true`. Borra el JSON de tu computadora cuando ya esté en Railway.

## Lo que se agregó en la versión 1.5 (diseño oscuro y animaciones fluidas)

|Cambio|Dónde|
|-|-|
|Modo oscuro con violeta (#6C47FF) y lila; letras Inter (texto) y Bricolage Grotesque (títulos), con licencia OFL|`vista/tema/TemaPhysioGestion.kt`, `composeResources/font`|
|Portada tipo cine en el inicio del paciente: rutina, próxima cita y prueba de escaleras, que avanzan solas cada 6 s|`InicioPacientePantalla` · `PortadaEscaleras`|
|Barra de pestañas flotante: Inicio, Rutina, Progreso y Perfil (paciente); Agenda, Pacientes y Avisos (personal)|`componentes/BarraPestanas.kt`, `NavegacionPrincipal`|
|Pantalla nueva «Tu progreso»: dolor sesión por sesión contra su límite, ritmo en escaleras y calendario de 4 semanas|`ProgresoPantalla`, `ProgresoViewModel`, `ReglasProgreso`|
|Agenda del fisioterapeuta como línea de tiempo del día, con la marca de la hora actual|`FisioterapeutaPantalla`|
|Movimiento con resortes y la curva de iOS: pantallas que entran desde la derecha, pestañas con fundido, botones que se encogen al tocarlos, anillos y gráficas que se dibujan|`vista/tema/Movimiento.kt`|
|Gesto de «atrás» predictivo de Android y fondo de ventana oscuro (sin destello al abrir)|`AndroidManifest.xml`, `res/values/themes.xml`|

**Para que se sienta fluida hay que instalar la versión release**: la de depuración es más lenta a propósito
(Android no la optimiza). En Android Studio: *Build Variants* → `androidApp` → **release** y Run;
se firma con la llave de depuración, así que se instala igual que siempre y usa el servidor de Railway.

## Lo que se agregó en la versión 1.4 (avisos y recordatorios, antes de Firebase)

|Función|Dónde|
|-|-|
|Aviso al fisioterapeuta cuando un paciente termina su sesión con dolor alto (también si corrige el dolor después)|`AvisosServicio` · `GET /avisos` · notificación urgente en Android|
|Bandeja de avisos para cada rol, con contador en el encabezado; tocar un aviso lleva a la ficha, a la rutina o a las citas|`AvisosPantalla`, `TarjetaAvisosSinLeer`|
|Avisos al paciente: cita nueva, reprogramada o cancelada y rutina modificada; al personal: cancelaciones del paciente y pacientes asignados|`AvisosServicio`|
|Recordatorio de cita un día antes y una hora antes|`NotificadorAndroid` (WorkManager, en el teléfono)|
|Recordatorio diario de la rutina a la hora que elija el paciente (no suena si ya entrenó ese día)|`PerfilPantalla` → Recordatorios|
|Envío automático de las sesiones guardadas sin conexión y aviso si siguen pendientes|`RevisarAvisosTrabajo` → `RevisorSegundoPlano`|

**Cómo llegan sin Firebase:** el teléfono pregunta al servidor. Con la app abierta revisa cada 30 s; cerrada,
WorkManager revisa cada 15 min con internet (el mínimo de Android, que además puede retrasarlo para ahorrar batería).
Los recordatorios de cita y de rutina se programan en el propio teléfono y no necesitan internet.
En escritorio los avisos se ven en la bandeja, sin notificación del sistema.

## Lo que se agregó en la versión 1.3 (operación clínica)

|Función|Dónde|
|-|-|
|Recepción agenda, reprograma, confirma y cancela citas con fecha; solo muestra horas libres y el servidor evita choques|`AgendarCitaPantalla`, `RecepcionPantalla` · `/citas`, `/citas/libres`|
|El fisioterapeuta marca «atendida» o «no asistió» y navega por días|`FisioterapeutaPantalla`|
|El paciente ve sus próximas citas y puede cancelarlas|`InicioPacientePantalla` (Mis próximas citas)|
|Lista y ficha de pacientes; recepción asigna el fisioterapeuta|`PacientesPantalla`, `FichaPacientePantalla` · `/pacientes`|
|El fisioterapeuta arma y modifica la rutina con un catálogo de 12 ejercicios|`EditarRutinaPantalla` · `PUT /pacientes/{id}/rutina`|
|El administrador edita nombre, correo y rol de las cuentas|`AdministradorPantalla` · `PATCH /usuarios/{id}`|

## Lo que se agregó en la versión 1.2 (cuentas seguras)

|Función|Dónde|
|-|-|
|Registro del paciente con código de invitación, verificación del correo y aceptación del aviso de privacidad|`RegistroPantalla`, `VerificarCorreoPantalla`, `AvisoPrivacidadPantalla` · `/auth/registro`, `/auth/verificar-correo`|
|«¿Olvidaste tu contraseña?»: código de un solo uso por correo y restablecimiento|`RecuperarContrasenaPantalla` · `/auth/recuperacion`, `/auth/restablecer`|
|Cambio de contraseña desde el perfil (todos los roles) y contraseña temporal obligatoria para el personal nuevo|`CambiarContrasenaPantalla` · `PATCH /usuarios/yo/contrasena`|
|Hash BCrypt, token con vencimiento, bloqueo tras 5 intentos y límite de 30 peticiones por minuto|`backend/.../servicios/Contrasenas.kt`, `AutenticacionServicio.kt`, `plugins/LimiteSolicitudes.kt`|
|Alta y desactivación del personal (administrador) e invitaciones (recepción)|`AdministradorPantalla`, `RecepcionPantalla`|
|Base de datos PostgreSQL en Railway: lo registrado sobrevive a los reinicios|`backend/.../datos/AlmacenJdbc.kt` (variable `DATABASE_URL`)|

## Conexión con el serializador (kotlinx.serialization)

Las clases que viajan por la red llevan `@Serializable`. Así el compilador genera el código que las convierte a JSON y de regreso.

|Dónde|Archivo|Qué hace|
|-|-|-|
|Plugin de Gradle|`kotlin("plugin.serialization")` en los dos `build.gradle.kts`|Genera el serializador de cada clase `@Serializable`|
|Backend|`backend/src/main/kotlin/plugins/Serialization.kt`|`install(ContentNegotiation) { json(...) }`: `call.receive<T>()` lee JSON y `call.respond(obj)` responde JSON|
|Backend|`backend/src/main/kotlin/datos/AlmacenJson.kt`|`json.decodeFromString<List<UsuarioRegistrado>>()` lee los archivos JSON de credenciales y datos|
|Frontend|`frontend/shared/.../datos/remoto/ClienteHttp.kt`|`install(ContentNegotiation) { json(...) }` en Ktor Client: `setBody(obj)` envía JSON y `body<T>()` lo lee|
|Frontend|`frontend/shared/.../navegacion/Rutas.kt`|Las rutas de navegación también son `@Serializable` (rutas tipadas)|
|Ambos|`modelos/` (backend) y `modelo/` (frontend)|DTOs con los mismos nombres de campo: `LoginSolicitud`, `LoginRespuesta`, `Usuario`, `Rol`, `Cita`, `Rutina`|

Como backend y frontend son proyectos separados, los DTOs están definidos en cada uno con los mismos campos.

## Cómo ejecutarlo

**1. Backend.** Abrir la carpeta `backend` en Android Studio (o IntelliJ IDEA) y ejecutar `main.kt`. También se puede desde la terminal:

```
cd backend
gradlew.bat run        (Windows)
./gradlew run          (macOS / Linux)
```

Cuando aparezca `Responding at http://0.0.0.0:8080`, abrir http://localhost:8080 en el navegador. Debe mostrar
«PhysioGestion API v1 en funcionamiento».

**1b. Backend en la nube (Railway).** El backend está desplegado en Railway con `backend/Dockerfile`
(ver «Despliegue en Railway» en `backend/README.md`) en **https://physiogestion-login-production.up.railway.app**.
Con esa dirección la app funciona
en cualquier celular con Internet, sin `adb reverse` ni misma red Wi‑Fi: se escribe en «Servidor · Cambiar» del login.

**1c. Versión web.** Desde la versión 1.7 la web es la misma app de Compose compilada a WebAssembly
(`frontend/shared/src/webMain`), ya no páginas de HTML y JavaScript aparte. El backend la sirve en `/web/`:

* Railway: https://physiogestion-login-production.up.railway.app/web/
* Local: http://localhost:8080/web/ (después de `publicarWeb`, ver abajo)

En una pantalla ancha las pestañas de cada rol pasan a un menú lateral y el contenido se centra. Administración
tiene Usuarios, Agenda, Pacientes, Solicitudes y Anuncios; recepción, Agenda, Pacientes, Solicitudes, Anuncios y
Avisos; el fisioterapeuta, Agenda, Pacientes y Avisos. El token se guarda en `sessionStorage`, así que cerrar la
pestaña cierra la sesión. Necesita un navegador con WebAssembly GC: Chrome y Edge 119+, Firefox 120+ y Safari 18.2+.

Desde la carpeta `frontend`:

```
gradlew.bat :shared:wasmJsBrowserDevelopmentRun   (web en desarrollo: http://localhost:8081, usa el backend local)
gradlew.bat :shared:publicarWeb                   (compila la web optimizada y la copia a backend/src/main/resources/web)
```

Después de `publicarWeb` se hace commit de `backend/src/main/resources/web` (y de `frontend/kotlin-js-store` la
primera vez) y Railway publica la web nueva al desplegar.

**2. Frontend.** Abrir la carpeta `frontend` en Android Studio, elegir **androidApp** y un emulador, y presionar Run.

* El emulador llega a la computadora por `http://10.0.2.2:8080`. Ya viene configurado.
* En un celular físico conectado a la misma red Wi‑Fi, tocar «Servidor · Cambiar» en el login y escribir la IP de la computadora, por ejemplo `http://192.168.1.70:8080`. La app la recuerda para la próxima vez.
* También se puede fijar en `frontend/local.properties` con `SERVIDOR_URL=...` (ver `frontend/local.properties.ejemplo`).
* Versión de escritorio: `gradlew.bat :desktopApp:run` dentro de `frontend`.
* Versión web: `gradlew.bat :shared:wasmJsBrowserDevelopmentRun` dentro de `frontend` (ver 1c).

**3. Pruebas**

* `backend`: `gradlew.bat test`. Son 16 pruebas: login, token, permisos por rol, chequeo de salud y los cinco métodos HTTP.
* `frontend`: `gradlew.bat :shared:jvmTest`. Son 56 pruebas: login, navegación, rutina, métodos HTTP, almacenamiento, migración de la base de datos, filtro y análisis de movimiento, y la dirección del servidor por ambiente.

## Repositorio

https://github.com/ArturoMqii005/PhysioGestion-Login

<!-- Pruebas de despliegue automático -->

