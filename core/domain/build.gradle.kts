import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// core:domain: reglas de negocio puras.
//
// Módulo Kotlin Multiplatform. El código vive en commonMain y no puede usar `java.*`
// ni `android.*`: el compilador lo rechaza.
//
// Por qué hay DOS objetivos (JVM y JS) aunque solo se use el de JVM:
// con un único objetivo, Kotlin compila commonMain directamente como código JVM y
// `java.math.BigDecimal` compila sin error (comprobado en este proyecto el 2026-11-16).
// Con dos objetivos, commonMain se compila además contra la librería estándar común
// (tarea compileCommonMainKotlinMetadata) y ahí cualquier `java.*` falla con
// "Unresolved reference 'java'". El objetivo JS existe solo para eso.
//
// El objetivo JS no declara entorno (ni nodejs() ni browser()) a propósito: declararlo
// hace que Kotlin descargue Node.js y paquetes de npm para ejecutar pruebas, y no
// queremos esa cadena de suministro. Por eso Gradle muestra la advertencia
// "JS Environment Not Selected", que es esperada. Las pruebas corren solo en la JVM.
//
// El objetivo JVM sirve para las pruebas y para que el módulo de Android lo consuma
// (Gradle acepta una variante `jvm` donde se pide `androidJvm`).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions {
            // Mismo bytecode que los módulos de Android, aunque Gradle corra en JDK 21.
            jvmTarget.set(JvmTarget.JVM_17)
        }
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    js()

    // Las API públicas del dominio deben declarar visibilidad y tipos de forma explícita.
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            // Flow para exponer datos que cambian (puertos de repositorio).
            api(libs.kotlinx.coroutines.core)
            // LocalDate sin java.time.
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.property)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Sin esta línea, `check` NO detecta `java.*` en commonMain: el objetivo JS sin entorno
// no compila nada durante `check` (comprobado). Con ella, la guardia corre siempre.
tasks.named("check") {
    dependsOn("compileCommonMainKotlinMetadata")
}

// Guardia: el dominio nunca puede depender de librerías de red ni de :feature:nube (D-020).
apply(from = rootProject.file("gradle/guardia-dependencias.gradle.kts"))
