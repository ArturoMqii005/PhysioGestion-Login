import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        // Clases expect/actual (AlmacenPreferencias, SensorMovimiento, GestorPermisos...).
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()

    // WEB: la misma App() de Compose compilada a WebAssembly. Lo propio del navegador
    // vive en src/webMain. Necesita un navegador con WebAssembly GC: Chrome y Edge 119+,
    // Firefox 120+ y Safari 18.2+ (también en iPhone).
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("physiogestion")
        browser {
            commonWebpackConfig {
                outputFileName = "physiogestion.js"
                // El backend local ya ocupa el 8080: la web en desarrollo se abre en el 8081.
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply { port = 8081 }
            }
        }
        binaries.executable()
    }

    android {
       namespace = "com.terapid.physiogestion.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    // Jerarquía de carpetas en src/: commonMain (todo lo compartido), androidMain, iosMain,
    // jvmMain (escritorio), webMain (navegador) y sqliteMain, que agrupa a Android, iOS y
    // escritorio porque son los que guardan el historial en SQLite. El navegador no tiene
    // SQLite: su almacenamiento local está en webMain.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val sqliteMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // Base de datos local (SQLite multiplataforma)
                implementation(libs.androidx.sqlite)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
        androidMain.get().dependsOn(sqliteMain)
        jvmMain.get().dependsOn(sqliteMain)
        iosMain.get().dependsOn(sqliteMain)

        webMain.dependencies {
            // Motor HTTP del navegador (fetch)
            implementation(libs.ktor.client.js)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            // Motor HTTP especifico de Android
            implementation(libs.ktor.client.okhttp)
            // Verificacion con camara: CameraX (imagen) + ML Kit (deteccion de postura en el telefono)
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
            implementation(libs.mlkit.pose)
            // Avisos y recordatorios (sin Firebase): WorkManager revisa el servidor en segundo
            // plano y NotificationCompat muestra la notificación del sistema.
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.core.ktx)
            // Push: Firebase Cloud Messaging despierta a la app cuando el servidor crea un aviso.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
        }
        jvmMain.dependencies {
            // Motor HTTP especifico de escritorio (JVM)
            implementation(libs.ktor.client.java)
        }
        iosMain.dependencies {
            // Motor HTTP especifico de iOS
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Codigo compartido: navegacion, API, JSON y corrutinas
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.navigation.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            // API simulada para probar el repositorio sin Internet
            implementation(libs.ktor.client.mock)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// PUBLICAR LA WEB: compila la versión del navegador (Wasm, optimizada) y la copia al backend,
// que la sirve en /web/. Después se hace commit de backend/src/main/resources/web y Railway
// la publica al desplegar. Reemplaza todo lo que haya en esa carpeta.
//   gradlew.bat :shared:publicarWeb
val publicarWeb by tasks.registering(Sync::class) {
    group = "physiogestion"
    description = "Compila la web (Wasm) y la copia a backend/src/main/resources/web."
    dependsOn("wasmJsBrowserDistribution")
    from(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(rootProject.file("../backend/src/main/resources/web"))
}
