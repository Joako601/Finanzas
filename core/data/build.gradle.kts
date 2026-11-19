// core:data: persistencia con Room. Implementa los puertos que define core:domain.
//
// Desde AGP 9 el plugin de Android compila Kotlin por sí mismo ("Kotlin integrado"),
// así que ya no se aplica `org.jetbrains.kotlin.android`.
plugins {
    alias(libs.plugins.android.library)
    // KSP genera en tiempo de compilación el código de Room (DAO, esquema).
    alias(libs.plugins.ksp)
    // Exporta el esquema de la base a JSON en el repositorio, para revisar y probar migraciones.
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.joako601.finanzas.core.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:domain"))

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Pruebas locales en la JVM (sin teléfono) para el cifrado del respaldo.
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))

    // Crea la base en una versión anterior desde su esquema exportado y valida la migración.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(kotlin("test"))
}
