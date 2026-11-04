\# Guía de instalación y ejecución



Esta guía explica cómo instalar las herramientas necesarias y cómo correr PhysioGestion (backend y app Android) en tu computadora.



\## 1. Requisitos previos



\- \*\*Git\*\*: https://git-scm.com

\- \*\*Android Studio\*\* (versión reciente): https://developer.android.com/studio

\- \*\*JDK 21\*\*: necesario para compilar y correr el backend con Gradle.

\- Un emulador de Android (se crea desde Android Studio) o un teléfono con depuración USB activada.



Para comprobar que Java quedó instalado:



```powershell

java -version

```



Debe mostrar la versión 21.



\## 2. Clonar el repositorio



```powershell

git clone https://github.com/ArturoMqii005/PhysioGestion-Login.git

cd PhysioGestion-Login

```



\## 3. Correr el backend



```powershell

cd backend

gradlew.bat run

```



Cuando termine de arrancar, el servidor responde en:



http://localhost:8080



Para comprobarlo, abre esa dirección en el navegador. Deja esta terminal abierta mientras uses la app; para detener el servidor presiona `Ctrl + C`.



\## 4. Correr la app



1\. Abre \*\*Android Studio\*\*.

2\. Elige \*\*File → Open\*\* y selecciona la carpeta `frontend/` del repositorio.

3\. Espera a que Gradle termine de sincronizar.

4\. En la barra superior, en la lista de configuraciones de ejecución, elige \*\*androidApp\*\*.

5\. En \*\*Build Variants\*\*, selecciona la variante \*\*release\*\* para que la app use el servidor desplegado en Railway.

6\. Elige un emulador o dispositivo y presiona \*\*Run\*\*.



\## 5. Usuarios de prueba



| Rol | Usuario | Contraseña |

|---|---|---|

| Paciente | paciente | Paciente2026 |



\## 6. Problemas comunes



\- \*\*`gradlew.bat` no se reconoce\*\*: asegúrate de estar dentro de la carpeta `backend`.

\- \*\*Error de versión de Java\*\*: verifica con `java -version` que tengas JDK 21.

\- \*\*La app no conecta\*\*: confirma que elegiste la variante correcta y que tienes internet.

