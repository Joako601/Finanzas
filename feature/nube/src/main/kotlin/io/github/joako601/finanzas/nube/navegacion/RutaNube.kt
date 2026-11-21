package io.github.joako601.finanzas.nube.navegacion

/**
 * Destinos de la sección nube (D-021).
 *
 * Se maneja con un `enum` + `when` en lugar de Navigation Compose porque solo hay tres
 * pantallas (más un diálogo de recuperación desde Login) y evita agregar dependencias y
 * hashes nuevos en `verification-metadata.xml`.
 */
enum class RutaNube {
    LOGIN,
    REGISTRO,
    CATALOGO,
}
