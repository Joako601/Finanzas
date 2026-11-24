import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    // Compilador de Compose (va con la versión de Kotlin).
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.joako601.finanzas"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.joako601.finanzas"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 11
        versionName = "0.1.0-dev.11"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Las pruebas instrumentadas instalan la app y al terminar la DESINSTALAN. Con este tipo
    // aparte corren sobre "io.github.joako601.finanzas.pruebas" y nunca tocan la app debug
    // que el autor usa con sus datos.
    testBuildType = "pruebas"

    buildTypes {
        debug {
            // Permite tener instaladas la versión de desarrollo y la de release a la vez.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("pruebas") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".pruebas"
            versionNameSuffix = "-pruebas"
            // core:data no tiene tipo "pruebas": usa su variante debug.
            matchingFallbacks += "debug"
        }
        release {
            // R8: elimina código no usado y ofusca. Las librerías traen sus propias reglas.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Dos distribuciones de la misma app (D-020):
    // - privada: la de siempre, sin red. Conserva EXACTAMENTE los paquetes de antes
    //   (io.github.joako601.finanzas, .debug y .pruebas) para que Android la trate como la
    //   misma app y el autor no pierda los datos de la que ya tiene instalada.
    // - nube: agrega la sección opcional de cuenta y catálogo. Es otro paquete
    //   (….nube, ….nube.debug), así que se instala junto a la privada sin tocar su base.
    // La dimensión se aplica solo a :app; core:data y core:domain no tienen flavors y
    // Gradle los consume igual desde las dos variantes (no hace falta missingDimensionStrategy,
    // que es para el caso contrario: una librería con una dimensión que la app no tiene).
    flavorDimensions += "distribucion"
    productFlavors {
        create("privada") {
            dimension = "distribucion"
            // Variante por omisión en Android Studio y en `lint`: la que usa el autor.
            isDefault = true
        }
        create("nube") {
            dimension = "distribucion"
            applicationIdSuffix = ".nube"
            versionNameSuffix = "-nube"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    // La sección nube solo entra en el flavor "nube"; nunca en "privada" (D-020).
    "nubeImplementation"(project(":feature:nube"))

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.lottie.compose)

    // Pruebas de la interfaz en el teléfono (accesibilidad). ui-test-manifest declara la
    // Activity vacía donde corre la prueba; solo va en el APK de pruebas, nunca en debug ni release.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    "pruebasImplementation"(libs.androidx.compose.ui.test.manifest)

    // Pruebas en la JVM (sin teléfono) de la lógica de la interfaz.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

/**
 * Guardia de permisos: lee el manifiesto FUSIONADO (el nuestro más el de cada librería)
 * y falla si aparece un permiso fuera de la lista permitida. Así, si una dependencia
 * agrega INTERNET, el build lo dice en lugar de enterarnos en la auditoría.
 *
 * Es una guardia, no la prueba final: el oráculo es el volcado de permisos del APK de
 * release con aapt2 (ver docs/verificacion.md).
 */
abstract class VerificarPermisosTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifiesto: RegularFileProperty

    @get:Input
    abstract val permitidos: ListProperty<String>

    @get:OutputFile
    abstract val reporte: RegularFileProperty

    @TaskAction
    fun verificar() {
        val documento = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifiesto.get().asFile)
        val androidNs = "http://schemas.android.com/apk/res/android"
        val encontrados = listOf("uses-permission", "uses-permission-sdk-23").flatMap { etiqueta ->
            val nodos = documento.getElementsByTagName(etiqueta)
            (0 until nodos.length).map { (nodos.item(it) as org.w3c.dom.Element).getAttributeNS(androidNs, "name") }
        }.sorted()

        reporte.get().asFile.writeText(encontrados.joinToString(separator = "\n", postfix = "\n"))

        val noAutorizados = encontrados - permitidos.get().toSet()
        if (noAutorizados.isNotEmpty()) {
            throw GradleException(
                "Permisos no autorizados en el manifiesto fusionado: $noAutorizados. " +
                    "Revisa qué dependencia los agrega con el reporte de fusión " +
                    "(build/outputs/logs/manifest-merger-*-report.txt).",
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        val nombre = variant.name.replaceFirstChar { it.uppercase() }
        val tarea = tasks.register<VerificarPermisosTask>("verificarPermisos$nombre") {
            group = "verification"
            description = "Falla si el manifiesto fusionado de $nombre pide permisos no autorizados."
            manifiesto.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            // androidx.core declara un permiso propio con nivel "signature" para que solo
            // nuestra app pueda enviar broadcasts a sus receptores no exportados. No da
            // acceso a nada del sistema.
            // Los otros dos son los de los recordatorios (D-015): mostrar avisos y
            // reprogramarlos al reiniciar el teléfono. Ninguno da acceso a red.
            // Solo la distribución nube (D-020) suma red: INTERNET para hablar con Firebase
            // y el backend, y ACCESS_NETWORK_STATE, que firebase-auth declara para saber si
            // hay conexión antes de intentar. La privada sigue sin ninguno de los dos.
            val esNube = variant.flavorName == "nube"
            permitidos.set(
                variant.applicationId.map {
                    listOf(
                        "$it.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                        "android.permission.POST_NOTIFICATIONS",
                        "android.permission.RECEIVE_BOOT_COMPLETED",
                    ) + if (esNube) {
                        listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
                    } else {
                        emptyList()
                    }
                },
            )
            reporte.set(layout.buildDirectory.file("reportes/permisos/${variant.name}.txt"))
        }
        tasks.named("check") { dependsOn(tarea) }
    }
}

// Guardia: las variantes privada* no pueden arrastrar librerías de red ni :feature:nube (D-020).
apply(from = rootProject.file("gradle/guardia-dependencias.gradle.kts"))
