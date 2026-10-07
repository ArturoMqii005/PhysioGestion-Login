# PhysioGestion · Backend (Ktor Server)

Proyecto creado con el asistente oficial de Ktor ([start.ktor.io](https://start.ktor.io)) con estas opciones:

- Ktor 3.6.0
- Motor Netty
- Gradle Kotlin DSL
- Configuración en YAML

| Plugin del asistente | Archivo | Uso en PhysioGestion |
|---|---|---|
| Content Negotiation + kotlinx.serialization | `plugins/Serialization.kt` | Convierte JSON ↔ objetos `@Serializable` |
| Authentication (Bearer) | `plugins/Seguridad.kt` | Protege las rutas con el token del login y revisa el rol |
| Status Pages | `plugins/StatusPages.kt` | Respuestas de error en JSON (400, 403, 404, 500) |
| Call Logging | `plugins/Monitoreo.kt` | Muestra en consola cada petición y su resultado |
| CORS | `plugins/Http.kt` | Permite una futura versión web |

## Estructura

```
backend/
├── build.gradle.kts            Dependencias (catálogo de versiones de Ktor)
├── settings.gradle.kts
├── gradle/libs.versions.toml   Versiones de Kotlin y Logback
└── src/
    ├── main/kotlin/            Paquete com.terapid.physiogestion
    │   ├── main.kt             Arranca el motor Netty (generado por el asistente)
    │   ├── Application.kt      module(): crea los objetos e instala los plugins
    │   ├── plugins/            Configuración de cada plugin de Ktor
    │   ├── modelos/            DTOs @Serializable: Usuario, Rol, LoginSolicitud, LoginRespuesta, Cita, Rutina
    │   ├── datos/              AlmacenJson: lee los archivos JSON de resources/datos
    │   ├── servicios/          AutenticacionServicio: valida credenciales y maneja tokens
    │   └── rutas/              Endpoints agrupados por recurso
    ├── main/resources/
    │   ├── application.yaml    Puerto 8080 y módulo principal
    │   ├── logback.xml         Formato del registro en consola
    │   └── datos/              usuarios.json (credenciales), citas.json, rutinas.json
    └── test/kotlin/ServerTest.kt   Pruebas de la API con testApplication
```

## Endpoints

| Método | Ruta | Acceso | Respuesta |
|---|---|---|---|
| GET | `/` | Público | Texto de estado |
| POST | `/api/v1/auth/login` | Público | `LoginRespuesta` (token + usuario con rol) o 401 |
| POST | `/api/v1/auth/logout` | Con token | 204 |
| GET | `/api/v1/usuarios` | ADMINISTRADOR | Lista de usuarios sin contraseña |
| GET | `/api/v1/citas` | ADMINISTRADOR, RECEPCIONISTA, FISIOTERAPEUTA | Agenda del día. El fisioterapeuta solo recibe las suyas |
| GET | `/api/v1/pacientes/{id}/rutina` | PACIENTE (solo la suya), FISIOTERAPEUTA | Rutina vigente |

Ejemplo de login:

```
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{ "usuario": "admin", "contrasena": "Admin2026" }
```

## Ejecutar

- `gradlew.bat run`: inicia el servidor en http://localhost:8080
- `gradlew.bat test`: ejecuta las pruebas
