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
    │   ├── modelos/            DTOs @Serializable: Usuario, Rol, Cita, Rutina, SesionTerapia, ResultadoEjercicio, EstadoServidor…
    │   ├── datos/              AlmacenJson: lee los archivos JSON de resources/datos
    │   ├── servicios/          AutenticacionServicio (credenciales y tokens) y SesionesServicio (sesiones y semáforo)
    │   └── rutas/              Endpoints agrupados por recurso (SesionRutas.kt: los cinco métodos HTTP; SaludRutas.kt: chequeo de salud)
    ├── main/resources/
    │   ├── application.yaml    Puerto (variable de entorno PORT, 8080 por defecto) y módulo principal
    │   ├── logback.xml         Formato del registro en consola
    │   └── datos/              usuarios.json (credenciales), citas.json, rutinas.json, sesiones.json
    └── test/kotlin/            ServerTest.kt (login, roles y salud) y SesionesTest.kt (métodos HTTP)
```

## Endpoints

| Método | Ruta | Acceso | Respuesta |
|---|---|---|---|
| GET | `/` | Público | Texto de estado |
| GET | `/api/v1/salud` | Público | `EstadoServidor`: estado, versión, entorno, región, commit desplegado y segundos encendido |
| POST | `/api/v1/auth/login` | Público | `LoginRespuesta` (token + usuario + pendientes) o 401. Acepta usuario **o correo**. 5 fallos → **429** por 5 min |
| POST | `/api/v1/auth/logout` | Con token | 204 |
| POST | `/api/v1/auth/registro` | Público | Paciente con **código de invitación** → 201 y código de 6 dígitos al correo |
| POST | `/api/v1/auth/verificar-correo` | Público | Confirma el correo (vence en 15 min, 5 intentos) |
| POST | `/api/v1/auth/reenviar-verificacion` | Público | 202 (misma respuesta exista o no la cuenta) |
| POST | `/api/v1/auth/recuperacion` | Público | 202 y código para restablecer (misma respuesta exista o no el correo) |
| POST | `/api/v1/auth/restablecer` | Público | Código + contraseña nueva; cierra todas las sesiones |
| GET | `/api/v1/aviso-privacidad` | Público | Texto vigente del aviso de privacidad |
| GET | `/api/v1/usuarios/yo` | Con token | Cuenta y pendientes (aviso, contraseña temporal) |
| PATCH | `/api/v1/usuarios/yo/contrasena` | Con token | Cambio de contraseña; cierra las otras sesiones |
| POST | `/api/v1/usuarios/yo/consentimiento` | PACIENTE | Acepta el aviso de privacidad |
| GET | `/api/v1/usuarios` | ADMINISTRADOR | Lista de usuarios sin contraseña (con activo y correo verificado) |
| POST | `/api/v1/usuarios` | ADMINISTRADOR | Alta de personal con contraseña temporal → 201 |
| PATCH | `/api/v1/usuarios/{id}` | ADMINISTRADOR | Activar o desactivar una cuenta |
| GET / POST | `/api/v1/invitaciones` | ADMINISTRADOR, RECEPCIONISTA | Códigos de invitación para pacientes nuevos (7 días, un solo uso) |
| GET | `/api/v1/citas?fecha=2026-11-04` | ADMINISTRADOR, RECEPCIONISTA, FISIOTERAPEUTA, PACIENTE | Agenda del día (hoy si no se indica). El fisioterapeuta solo recibe las suyas; el paciente, sus próximas citas |
| GET | `/api/v1/citas/libres?fisioterapeutaId=&fecha=&duracion=` | ADMINISTRADOR, RECEPCIONISTA | Horas de inicio libres, cada 30 min de 07:00 a 21:00 |
| POST | `/api/v1/citas` | ADMINISTRADOR, RECEPCIONISTA | Agenda una cita → 201. Choque con otra cita del fisioterapeuta → **409** |
| PATCH | `/api/v1/citas/{id}` | ADMINISTRADOR, RECEPCIONISTA | Reprograma (fecha, hora, fisioterapeuta, duración) |
| POST | `/api/v1/citas/{id}/estado` | según el rol | Recepción confirma o cancela; el fisioterapeuta marca atendida o no asistió; el paciente cancela la suya |
| GET | `/api/v1/pacientes` · `/api/v1/pacientes/{id}` | FISIOTERAPEUTA (los suyos), RECEPCIONISTA, ADMINISTRADOR | Lista y ficha (recepción no ve datos de salud) |
| PATCH | `/api/v1/pacientes/{id}/fisioterapeuta` | RECEPCIONISTA, ADMINISTRADOR | Asigna o cambia el fisioterapeuta |
| GET | `/api/v1/pacientes/{id}/rutina` | PACIENTE (solo la suya), FISIOTERAPEUTA (sus pacientes) | Rutina vigente (con ángulo objetivo y si se mide con sensores) |
| PUT | `/api/v1/pacientes/{id}/rutina` | FISIOTERAPEUTA | Crea o modifica la rutina con ejercicios del catálogo (`GET /api/v1/ejercicios`) |
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
Las rutas de `/auth` aceptan como máximo 30 peticiones por minuto por IP (429 con `Retry-After`).

## Seguridad de las cuentas

- Las contraseñas se guardan **solo como hash BCrypt** (`contrasenaHash` en `usuarios.json`); los tokens y los códigos, como SHA-256.
- El token vence tras **30 días sin uso** y cada petición lo renueva.
- Recuperar y restablecer siguen las recomendaciones de OWASP: respuesta idéntica exista o no el correo,
  código de un solo uso de 15 minutos con 5 intentos y cierre de todas las sesiones al cambiar la contraseña.

## Variables de entorno

| Variable | Para qué | Si falta |
|---|---|---|
| `PORT` | Puerto (Railway lo asigna) | 8080 |
| `DATABASE_URL` | PostgreSQL de Railway: usuarios, sesiones y mediciones **sobreviven a los reinicios** | Todo vive en memoria |
| `BREVO_API_KEY` y `CORREO_REMITENTE` | Envío de los códigos por correo con la API de Brevo (300 correos/día gratis) | Los códigos se escriben en el registro del servidor |
| `NOMBRE_REMITENTE` | Nombre que ve el paciente en el correo | PhysioGestion |
| `CORS_ORIGENES` | Dominios web permitidos, separados por coma | Cualquier origen |

`/api/v1/salud` muestra `baseDatos` (memoria o postgresql) y `correoConfigurado`.

Ejemplo de login:

```
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{ "usuario": "admin", "contrasena": "Admin2026" }
```

## Ejecutar

- `gradlew.bat run`: inicia el servidor en http://localhost:8080
- Otro puerto (variable de entorno): `set PORT=8090` y luego `gradlew.bat run`
- `gradlew.bat test`: ejecuta las 42 pruebas (cuentas, citas, pacientes, rutinas y persistencia con SQLite)

## Despliegue en Railway

El `Dockerfile` de esta carpeta hace todo en los servidores de Railway:

1. **Etapa de compilación** (`eclipse-temurin:21-jdk`): `./gradlew buildFatJar` genera `build/libs/backend-all.jar`,
   un solo archivo con el servidor y todas sus librerías.
2. **Etapa de ejecución** (`eclipse-temurin:21-jre`): copia ese jar y lo arranca con `java -jar`.

Railway asigna el puerto en la variable de entorno `PORT`; `application.yaml` la lee (`"$PORT:8080"`), así que no hay que
cambiar código entre la computadora y la nube.

**Dirección pública:** https://physiogestion-login-production.up.railway.app
(chequeo de salud: https://physiogestion-login-production.up.railway.app/api/v1/salud).

Cada `git push` a `main` (por SSH, `git@github.com:ArturoMqii005/PhysioGestion-Login.git`) dispara un nuevo despliegue.
`/api/v1/salud` muestra el commit que quedó corriendo, así se comprueba que el servidor tiene la última versión.

En Railway: **New Project → Deploy from GitHub repo → PhysioGestion-Login**, y en el servicio
**Settings → Root Directory = `/backend`**. Después, **Settings → Networking → Generate Domain** da la dirección pública
(`https://…up.railway.app`), que se escribe en el login de la app con «Servidor · Cambiar».

Notas:
- Si Railway dice que no encuentra el Dockerfile, agregar en **Variables** `RAILWAY_DOCKERFILE_PATH=Dockerfile`.
- **Base de datos:** en el proyecto de Railway, **+ New → Database → PostgreSQL**. Después, en el servicio del backend,
  **Variables → New Variable → Add Reference → `DATABASE_URL`** del servicio Postgres. Sin esa variable, los datos viven en memoria
  y se pierden cada vez que el contenedor se duerme o se redespliega.
- **Correo:** crear una cuenta gratis en Brevo, verificar el correo remitente (Senders) y generar una clave en **SMTP & API → API Keys**.
  En Railway agregar `BREVO_API_KEY` y `CORREO_REMITENTE`. Railway bloquea el SMTP en los planes gratuitos; Brevo se usa por HTTPS.
- Revisión desde la terminal con la CLI de Railway: `railway logs` (registros) y `railway ssh` (consola dentro del contenedor).
- El contenedor usa unos 90 MB de memoria; el `-XX:MaxRAMPercentage=75` del Dockerfile lo mantiene dentro del límite del plan.

