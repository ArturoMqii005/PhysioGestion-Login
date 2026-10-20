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
| Status Pages | `plugins/StatusPages.kt` | Respuestas de error en JSON (400, 403, 404, 409, 422, 500) |
| Call Logging | `plugins/Monitoreo.kt` | Muestra en consola cada petición y su resultado |
| CORS | `plugins/Http.kt` | Permite una futura versión web (GET, POST, PUT, PATCH, DELETE) |

## Estructura

```
backend/
├── Dockerfile                  Imagen para Railway: compila el fat jar y lo ejecuta con Java 21
├── .dockerignore               Carpetas que no se mandan a Docker (build, .gradle…)
├── build.gradle.kts            Dependencias (catálogo de versiones de Ktor)
├── settings.gradle.kts
├── gradle/libs.versions.toml   Versiones de Kotlin y Logback
└── src/
    ├── main/kotlin/            Paquete com.terapid.physiogestion
    │   ├── main.kt             Arranca el motor Netty (generado por el asistente)
    │   ├── Application.kt      module(): crea los objetos e instala los plugins
    │   ├── plugins/            Configuración de cada plugin de Ktor
    │   ├── modelos/            DTOs @Serializable: Usuario, Rol, Cita, Rutina, SesionTerapia, ResultadoEjercicio…
    │   ├── datos/              AlmacenJson: lee los archivos JSON de resources/datos
    │   ├── servicios/          AutenticacionServicio (credenciales y tokens) y SesionesServicio (sesiones y semáforo)
    │   └── rutas/              Endpoints agrupados por recurso (SesionRutas.kt: los cinco métodos HTTP)
    ├── main/resources/
    │   ├── application.yaml    Puerto (variable de entorno PORT, 8080 por defecto) y módulo principal
    │   ├── logback.xml         Formato del registro en consola
    │   └── datos/              usuarios.json (credenciales), citas.json, rutinas.json, sesiones.json
    └── test/kotlin/            ServerTest.kt (login y roles) y SesionesTest.kt (métodos HTTP)
```

## Endpoints

| Método | Ruta | Acceso | Respuesta |
|---|---|---|---|
| GET | `/` | Público | Texto de estado |
| POST | `/api/v1/auth/login` | Público | `LoginRespuesta` (token + usuario con rol) o 401 |
| POST | `/api/v1/auth/logout` | Con token | 204 |
| GET | `/api/v1/usuarios` | ADMINISTRADOR | Lista de usuarios sin contraseña |
| GET | `/api/v1/citas` | ADMINISTRADOR, RECEPCIONISTA, FISIOTERAPEUTA | Agenda del día. El fisioterapeuta solo recibe las suyas |
| GET | `/api/v1/pacientes/{id}/rutina` | PACIENTE (solo la suya), FISIOTERAPEUTA | Rutina vigente (con ángulo objetivo y si se mide con sensores) |
| GET | `/api/v1/sesiones?idPaciente=&pagina=&tamano=` | PACIENTE (las suyas), FISIOTERAPEUTA (sus pacientes) | Historial paginado; total en `X-Total-Count` |
| GET | `/api/v1/sesiones/{id}` | PACIENTE, FISIOTERAPEUTA | Una sesión |
| POST | `/api/v1/sesiones` | PACIENTE | **201 Created** + `Location`. Con `Idempotency-Key` repetida responde 200 y no duplica. Misma hora de inicio: **409** |
| PUT | `/api/v1/sesiones/{id}` | PACIENTE (solo las suyas) | Reemplaza dolor, dificultad y observación |
| PATCH | `/api/v1/sesiones/{id}` | PACIENTE (solo las suyas) | Cambia solo los campos enviados |
| DELETE | `/api/v1/sesiones/{id}` | PACIENTE (solo las suyas) | **204 No Content**; la segunda vez, 404 |
| POST | `/api/v1/resultados-ejercicio` | PACIENTE | Resumen medido con sensores → 201 |
| GET | `/api/v1/resultados-ejercicio` | PACIENTE, FISIOTERAPEUTA | Resultados de sensores |

Errores de validación (dolor fuera de 0 a 10, ejercicio que no está en la rutina) responden **422** con un mensaje claro.
Cada sesión trae su **semáforo**: ROJO si el dolor después alcanza el umbral de la rutina, AMARILLO si el cumplimiento es menor a 60 %, VERDE en otro caso.
En esta etapa las sesiones viven en memoria (se recargan de `sesiones.json` al reiniciar), igual que los tokens.

Ejemplo de login:

```
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{ "usuario": "admin", "contrasena": "Admin2026" }
```

## Ejecutar

- `gradlew.bat run`: inicia el servidor en http://localhost:8080
- Otro puerto (variable de entorno): `set PORT=8090` y luego `gradlew.bat run`
- `gradlew.bat test`: ejecuta las 15 pruebas

## Despliegue en Railway

El `Dockerfile` de esta carpeta hace todo en los servidores de Railway:

1. **Etapa de compilación** (`eclipse-temurin:21-jdk`): `./gradlew buildFatJar` genera `build/libs/backend-all.jar`,
   un solo archivo con el servidor y todas sus librerías.
2. **Etapa de ejecución** (`eclipse-temurin:21-jre`): copia ese jar y lo arranca con `java -jar`.

Railway asigna el puerto en la variable de entorno `PORT`; `application.yaml` la lee (`"$PORT:8080"`), así que no hay que
cambiar código entre la computadora y la nube.

En Railway: **New Project → Deploy from GitHub repo → PhysioGestion-Login**, y en el servicio
**Settings → Root Directory = `/backend`**. Después, **Settings → Networking → Generate Domain** da la dirección pública
(`https://…up.railway.app`), que se escribe en el login de la app con «Servidor · Cambiar».

Notas:
- Si Railway dice que no encuentra el Dockerfile, agregar en **Variables** `RAILWAY_DOCKERFILE_PATH=Dockerfile`.
- Los datos y los tokens viven en memoria: cada nuevo despliegue o reinicio vuelve a cargar los JSON y pide iniciar sesión otra vez.
- El contenedor usa unos 90 MB de memoria; el `-XX:MaxRAMPercentage=75` del Dockerfile lo mantiene dentro del límite del plan.

