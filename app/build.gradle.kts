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
        versionCode = 5
        versionName = "0.1.0-dev.5"
    }

    buildTypes {
        debug {
            // Permite tener instaladas la versión de desarrollo y la de release a la vez.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8: elimina código no usado y ofusca. Las librerías traen sus propias reglas.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Vista previa de Compose en Android Studio; solo en debug, no llega al APK de release.
    debugImplementation(libs.androidx.compose.ui.tooling)

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
            permitidos.set(
                variant.applicationId.map {
                    listOf(
                        "$it.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                        "android.permission.POST_NOTIFICATIONS",
                        "android.permission.RECEIVE_BOOT_COMPLETED",
                    )
                },
            )
            reporte.set(layout.buildDirectory.file("reportes/permisos/${variant.name}.txt"))
        }
        tasks.named("check") { dependsOn(tarea) }
    }
}
