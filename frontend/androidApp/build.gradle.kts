import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// ---- VARIABLES DE ENTORNO ----
// Lo que cambia en cada computadora (la IP del backend para un celular físico)
// se lee de local.properties, que NO se sube a GitHub. Si no existe la clave,
// también se acepta como variable de entorno del sistema. Si no hay ninguna,
// queda vacía y la app usa http://10.0.2.2:8080 (el emulador).
val propiedadesLocales = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

fun variable(clave: String): String =
    propiedadesLocales.getProperty(clave) ?: providers.environmentVariable(clave).orNull ?: ""

android {
    namespace = "com.terapid.physiogestion"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.terapid.physiogestion"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.1"

        // Llegan al código como BuildConfig.SERVIDOR_URL (nunca escritas en un .kt).
        buildConfigField("String", "SERVIDOR_URL", "\"${variable("SERVIDOR_URL")}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // genera la clase BuildConfig
    }
}