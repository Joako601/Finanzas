// Registrador de notificaciones: herramienta de la fase 0 para juntar el corpus.
//
// Solo existe la variante debug. Eso tiene dos efectos buscados:
// - nunca se puede generar por accidente un APK de release para publicar;
// - la app es "debuggable", así que `adb shell run-as` puede leer su almacenamiento
//   privado para exportar el registro sin pedir permisos de almacenamiento.
//
// No depende de ningún módulo de la app: es desechable.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.joako601.finanzas.registrador"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.joako601.finanzas.registrador"
        // Solo para teléfonos recientes: permite abrir directo el interruptor de acceso
        // a notificaciones de esta app (API 30) sin ramas por versión.
        minSdk = 30
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variante ->
        variante.enable = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
