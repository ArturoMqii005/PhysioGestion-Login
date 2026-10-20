# ============================================================
#  Imagen del backend de PhysioGestion para Railway.
#  Railway la detecta sola porque está en la carpeta del servicio
#  (Root Directory = /backend) y la construye en sus servidores.
# ============================================================

# ---- Etapa 1: compilar el "fat jar" (el servidor con todas sus librerías en un solo .jar) ----
FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /app
COPY . .
# gradle-daemon-jvm.properties pide Java 25 para el daemon de Gradle. La imagen ya trae
# Java 21 (el que pide jvmToolchain), así que se quita SOLO dentro de la imagen para no
# descargar otro JDK. En el repositorio el archivo se queda igual.
RUN rm -f gradle/gradle-daemon-jvm.properties \
 && chmod +x gradlew \
 && ./gradlew buildFatJar --no-daemon --console=plain

# ---- Etapa 2: imagen ligera, solo para ejecutar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilacion /app/build/libs/*-all.jar physiogestion.jar
# VARIABLE DE ENTORNO: Railway asigna PORT y application.yaml la lee ("$PORT:8080").
ENV PORT=8080
EXPOSE 8080
# El heap usa como máximo el 75 % de la memoria del contenedor (el plan gratuito da 0.5 GB).
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "physiogestion.jar"]
