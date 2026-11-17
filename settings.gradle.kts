pluginManagement {
    repositories {
        // El repositorio de Google solo puede servir artefactos de Google/AndroidX.
        // Filtrar por grupo evita que un paquete con el mismo nombre se resuelva desde
        // un repositorio que no corresponde (confusión de dependencias).
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Ningún módulo puede declarar sus propios repositorios: todos salen de aquí.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Finanzas"

include(":app")
include(":core:domain")
include(":core:data")
