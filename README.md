# PhysioGestion · App móvil con inicio de sesión por roles

Proyecto de **Desarrollo de Aplicaciones Móviles** para la Clínica Terapid By Farmacias
(Tulancingo de Bravo, Hidalgo).

**Equipo:** Luis Arturo Jiménez Morales · Sebastián Banda Pasquel · Johan Dereck Cabañas Salazar

El proyecto está dividido en dos partes que se ejecutan por separado y se comunican por HTTP con JSON:

|Parte|Carpeta|Tecnología|Qué hace|
|-|-|-|-|
|**Backend**|[`backend/`](backend)|Ktor Server 3.6 (generado con el asistente start.ktor.io), Netty|API REST local en el puerto 8080. Valida las credenciales del archivo JSON y entrega los datos de cada rol|
|**Frontend**|[`frontend/`](frontend)|Kotlin Multiplatform + Compose Multiplatform, Ktor Client|App para Android (Android Studio), escritorio e iOS. Login, navegación por rol, sensores, almacenamiento local y los cinco métodos HTTP|

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
|Paciente|`paciente`|`Paciente2026`|Mi tratamiento: rutina con medición por sensores, historial (editar/eliminar), perfil y pasos|

> En esta etapa las credenciales son predefinidas y están en texto plano dentro del JSON, como lo pide la práctica.
> En la siguiente etapa se cambiarán por una base de datos con contraseñas cifradas.

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

**2. Frontend.** Abrir la carpeta `frontend` en Android Studio, elegir **androidApp** y un emulador, y presionar Run.

* El emulador llega a la computadora por `http://10.0.2.2:8080`. Ya viene configurado.
* En un celular físico conectado a la misma red Wi‑Fi, tocar «Servidor · Cambiar» en el login y escribir la IP de la computadora, por ejemplo `http://192.168.1.70:8080`. La app la recuerda para la próxima vez.
* También se puede fijar en `frontend/local.properties` con `SERVIDOR_URL=...` (ver `frontend/local.properties.ejemplo`).
* Versión de escritorio: `gradlew.bat :desktopApp:run` dentro de `frontend`.

**3. Pruebas**

* `backend`: `gradlew.bat test`. Son 15 pruebas: login, token, permisos por rol y los cinco métodos HTTP.
* `frontend`: `gradlew.bat :shared:jvmTest`. Son 54 pruebas: login, navegación, rutina, métodos HTTP, almacenamiento, migración de la base de datos, filtro y análisis de movimiento.

## Repositorio

https://github.com/ArturoMqii005/PhysioGestion-Login

