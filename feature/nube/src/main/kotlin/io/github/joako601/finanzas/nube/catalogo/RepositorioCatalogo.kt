package io.github.joako601.finanzas.nube.catalogo

/** Resultado tipado de consultar el catálogo público de productos financieros. */
sealed interface ResultadoCatalogo {
    data class Exito(val productos: List<ProductoFinanciero>) : ResultadoCatalogo
    data object SinConexion : ResultadoCatalogo
    data object SesionExpirada : ResultadoCatalogo
    data object ErrorServidor : ResultadoCatalogo
}

/**
 * Contrato de acceso al catálogo financiero.
 *
 * Permite intercambiar el proveedor local ([RepositorioCatalogoFalso]) por un cliente HTTP
 * hacia el backend Spring Boot sin modificar el ViewModel ni la interfaz.
 */
interface RepositorioCatalogo {
    suspend fun obtenerProductos(): ResultadoCatalogo
}
