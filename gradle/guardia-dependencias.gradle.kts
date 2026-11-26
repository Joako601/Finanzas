// Guardia de dependencias (D-020). Cada módulo vigilado la aplica con
//     apply(from = rootProject.file("gradle/guardia-dependencias.gradle.kts"))
// y queda registrada la tarea `verificarDependencias`, que corre con `check`.
//
// Qué vigila, según el módulo:
// - :core:domain, :core:data y las variantes privada* de :app: que su classpath de
//   ejecución (lo que llega al APK) no tenga NINGUNA librería de red o de nube, ni el
//   módulo :feature:nube. La distribución privada debe seguir sin red (D-005).
// - :feature:nube: que no dependa de :core:data, ni directo ni de rebote. Así el código
//   que habla con Firebase no puede abrir la base de Room del usuario.
//
// Por qué una guardia y no solo revisión: un `implementation(...)` en el módulo
// equivocado compila sin quejarse; esto lo convierte en un build rojo.
//
// Compatible con la caché de configuración: la tarea no guarda `Configuration`s (no se
// pueden serializar), sino el resultado de la resolución (`ResolvedComponentResult`),
// que es el patrón que documenta Gradle:
// https://docs.gradle.org/current/userguide/configuration_cache_requirements.html

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

/**
 * Grupos Maven de librerías que hablan con la red o con servicios de Google. Coincide
 * el grupo exacto o cualquier subgrupo (`com.google.firebase` cubre `com.google.firebase.x`).
 */
val gruposDeRed = listOf(
    "com.google.firebase", // Firebase (Auth, Analytics, Crashlytics, Messaging…)
    "com.google.android.gms", // Servicios de Google Play
    "com.google.android.play", // Play Integrity, Play Core
    "com.google.android.recaptcha", // reCAPTCHA (lo trae firebase-auth)
    "com.google.android.datatransport", // Envío de telemetría de Firebase
    "com.google.android.libraries.identity.googleid", // Entrar con Google (solo nube)
    "androidx.credentials", // Credential Manager: Entrar con Google (solo nube)
    "com.google.http-client",
    "com.google.api-client",
    "io.grpc",
    "com.squareup.okhttp", // OkHttp 2
    "com.squareup.okhttp3",
    "com.squareup.retrofit", // Retrofit 1
    "com.squareup.retrofit2",
    "io.ktor",
    "com.android.volley",
    "org.apache.httpcomponents",
    "io.sentry",
)

/** Qué configuraciones revisa este módulo y qué no pueden contener. */
data class Politica(
    val vigila: (String) -> Boolean,
    val gruposProhibidos: List<String>,
    val proyectosProhibidos: List<String>,
)

val sinTest: (String) -> Boolean = { !it.contains("Test") }

val politica: Politica = when (project.path) {
    ":core:domain", ":core:data" -> Politica(
        // jvm/js en el dominio; debug/release en datos. Las de pruebas no llegan al APK.
        vigila = { it.endsWith("RuntimeClasspath") && sinTest(it) },
        gruposProhibidos = gruposDeRed,
        proyectosProhibidos = listOf(":feature:nube"),
    )
    ":app" -> Politica(
        // privadaDebug, privadaRelease, privadaPruebas. La nube no se vigila aquí: ahí la
        // red es esperada y la frontera la cuida la política de :feature:nube.
        vigila = { it.startsWith("privada") && it.endsWith("RuntimeClasspath") && sinTest(it) },
        gruposProhibidos = gruposDeRed,
        proyectosProhibidos = listOf(":feature:nube"),
    )
    ":feature:nube" -> Politica(
        // Todas, también compilación y pruebas: ni siquiera para compilar puede ver core:data.
        vigila = { it.endsWith("CompileClasspath") || it.endsWith("RuntimeClasspath") },
        gruposProhibidos = emptyList(),
        proyectosProhibidos = listOf(":core:data"),
    )
    else -> throw GradleException("guardia-dependencias: no hay política para ${project.path}")
}

abstract class VerificarDependenciasTask : DefaultTask() {
    /** Nombre de cada configuración revisada, en el mismo orden que [raices]. */
    @get:Input
    abstract val nombres: ListProperty<String>

    /** Grafo resuelto de cada configuración. */
    @get:Input
    abstract val raices: ListProperty<ResolvedComponentResult>

    @get:Input
    abstract val gruposProhibidos: ListProperty<String>

    @get:Input
    abstract val proyectosProhibidos: ListProperty<String>

    @get:OutputFile
    abstract val reporte: RegularFileProperty

    @TaskAction
    fun verificar() {
        if (nombres.get().isEmpty()) {
            // Si se renombra un flavor o una variante, la guardia no debe pasar en silencio.
            throw GradleException("Ninguna configuración coincide con la política: revisa gradle/guardia-dependencias.gradle.kts")
        }
        val grupos = gruposProhibidos.get()
        val proyectos = proyectosProhibidos.get().toSet()
        val lineas = mutableListOf<String>()
        val fallas = mutableListOf<String>()

        nombres.get().zip(raices.get()).forEach { (nombre, raiz) ->
            val (componentes, sinResolver) = recorrer(raiz)
            lineas += "## $nombre"
            lineas += componentes.sorted()
            sinResolver.forEach { fallas += "$nombre: dependencia sin resolver $it" }
            componentes.forEach { componente ->
                val prohibido = when {
                    componente.startsWith("project ") -> componente.removePrefix("project ") in proyectos
                    else -> {
                        val grupo = componente.substringBefore(':')
                        grupos.any { grupo == it || grupo.startsWith("$it.") }
                    }
                }
                if (prohibido) fallas += "$nombre contiene $componente"
            }
        }

        reporte.get().asFile.writeText(lineas.joinToString(separator = "\n", postfix = "\n"))
        if (fallas.isNotEmpty()) {
            throw GradleException(
                "Dependencias no permitidas (D-020):\n" + fallas.distinct().joinToString("\n") { "  - $it" } +
                    "\nPara ver de dónde viene: ./gradlew <módulo>:dependencies --configuration <nombre>",
            )
        }
    }

    /** Todos los componentes alcanzables desde la raíz, como "grupo:nombre" o "project :ruta". */
    private fun recorrer(raiz: ResolvedComponentResult): Pair<Set<String>, Set<String>> {
        val vistos = mutableSetOf<ResolvedComponentResult>()
        val componentes = mutableSetOf<String>()
        val sinResolver = mutableSetOf<String>()
        val pendientes = ArrayDeque(listOf(raiz))
        while (pendientes.isNotEmpty()) {
            val actual = pendientes.removeFirst()
            if (!vistos.add(actual)) continue
            if (actual != raiz) {
                when (val id = actual.id) {
                    is ModuleComponentIdentifier -> componentes += "${id.group}:${id.module}"
                    is ProjectComponentIdentifier -> componentes += "project ${id.projectPath}"
                    else -> componentes += id.displayName
                }
            }
            actual.dependencies.forEach { dependencia ->
                when (dependencia) {
                    is ResolvedDependencyResult -> pendientes += dependencia.selected
                    is UnresolvedDependencyResult -> sinResolver += dependencia.requested.displayName
                }
            }
        }
        return componentes to sinResolver
    }
}

val tareaGuardia = tasks.register<VerificarDependenciasTask>("verificarDependencias") {
    group = "verification"
    description = "Falla si este módulo depende de algo que su política prohíbe (D-020)."
    // Este bloque corre al armar el grafo de tareas, cuando el plugin de Android ya creó
    // las configuraciones de cada variante.
    project.configurations
        .filter { it.isCanBeResolved && politica.vigila(it.name) }
        .sortedBy { it.name }
        .forEach { configuracion ->
            nombres.add(configuracion.name)
            raices.add(configuracion.incoming.resolutionResult.rootComponent)
        }
    gruposProhibidos.set(politica.gruposProhibidos)
    proyectosProhibidos.set(politica.proyectosProhibidos)
    reporte.set(layout.buildDirectory.file("reportes/dependencias/verificarDependencias.txt"))
}

tasks.named("check") { dependsOn(tareaGuardia) }
