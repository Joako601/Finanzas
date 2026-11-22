import java.util.Properties

// feature:nube: la sección opcional de cuenta y catálogo (D-020).
//
// Solo la usa la distribución "nube" de la app (`nubeImplementation`). Reglas que vigila
// la guardia de dependencias (gradle/guardia-dependencias.gradle.kts):
// - NO depende de core:data: desde aquí no se puede abrir la base de Room del usuario.
// - Firebase y cualquier librería de red entran solo aquí, nunca en core:* ni en la
//   distribución privada.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Credenciales de Firebase desde `firebase.properties` en la raíz, que está en .gitignore
 * (plantilla: `firebase.properties.ejemplo`). Si el archivo no existe, los valores quedan
 * vacíos: el módulo compila igual y la sección muestra un aviso en lugar de cerrarse.
 *
 * `providers.fileContents` hace que Gradle trate el archivo como entrada de la caché de
 * configuración: si cambia, vuelve a configurar; si no, la reutiliza.
 */
val credencialesFirebase: Properties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("firebase.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

/** Texto listo para ir entre comillas en BuildConfig, sin permitir que rompa el código Java. */
fun valorFirebase(clave: String): String {
    val valor = credencialesFirebase.getProperty(clave, "").trim()
    require(valor.none { it == '"' || it == '\\' || it.isISOControl() }) {
        "firebase.properties: el valor de $clave tiene caracteres no permitidos"
    }
    return "\"$valor\""
}

android {
    namespace = "io.github.joako601.finanzas.nube"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Estos tres bastan para Firebase Auth con inicialización manual
        // (FirebaseOptions.Builder exige applicationId y apiKey; projectId lo usa Auth).
        // La API key de Firebase no es una contraseña: identifica el proyecto y se protege
        // con restricciones en la consola. Aun así no va al repositorio.
        buildConfigField("String", "FIREBASE_API_KEY", valorFirebase("firebase.apiKey"))
        buildConfigField("String", "FIREBASE_APP_ID", valorFirebase("firebase.appId"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", valorFirebase("firebase.projectId"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Guardia: este módulo no puede depender de core:data (D-020).
apply(from = rootProject.file("gradle/guardia-dependencias.gradle.kts"))

dependencies {
    // Solo Auth, sin Analytics, Crashlytics ni Messaging. La BoM fija versiones compatibles.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.lottie.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Pruebas en la JVM de los ViewModels contra RepositorioSesionFalso.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
