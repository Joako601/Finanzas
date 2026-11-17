// Build raíz: solo declara los plugins con `apply false`.
//
// Declararlos aquí hace que todos los módulos compartan el mismo classloader de plugins.
// Es obligatorio cuando conviven el plugin de Android (que desde AGP 9 trae Kotlin
// integrado) y el plugin de Kotlin Multiplatform; si no, Gradle carga dos copias del
// plugin de Kotlin y falla. También fija la versión del compilador de Kotlin: AGP 9
// trae una versión mínima propia, y declarar el plugin aquí la sube a la del catálogo.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
