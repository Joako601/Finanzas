package io.github.joako601.finanzas.nube.catalogo

import java.text.NumberFormat
import java.util.Locale

/**
 * Producto financiero de referencia dentro del catálogo público.
 *
 * Contrato desacoplado del origen de datos para conectarse después al backend Spring Boot
 * y extenderse en el Hito 2 con imágenes ([imageUrl]) sin romper consumidores.
 *
 * @property id Identificador único del producto.
 * @property nombre Nombre comercial del producto (ej. "BBVA Azul", "CFE Doméstico").
 * @property categoria Categoría del producto (ej. "Tarjeta de crédito", "Servicio").
 * @property entidad Banco o proveedor que emite u ofrece el producto.
 * @property costoReferenciaPesos Anualidad o costo típico en pesos enteros (MXN).
 *   Usa `0L` cuando el producto no tiene anualidad ni costo fijo.
 * @property periodicidad Frecuencia del costo (ej. "Anual", "Mensual", "Bimestral", "Sin costo").
 * @property detalleReferencia Resumen corto de condiciones típicas (ej. CAT promedio o ciclo).
 * @property verificado `true` si un administrador revisó los datos contra la fuente oficial; solo
 *   estos entran al comparador. Una respuesta o caché sin el campo cuenta como no verificado.
 * @property fuenteReferencia De dónde salen las cifras; mientras el catálogo sea de ejemplo, lo dice (ej. "Ejemplo sin verificar · BBVA").
 * @property imageUrl URL opcional de imagen del producto, reservada para Hito 2.
 */
data class ProductoFinanciero(
    val id: Long,
    val nombre: String,
    val categoria: String,
    val entidad: String,
    val costoReferenciaPesos: Long,
    val periodicidad: String,
    val detalleReferencia: String,
    val fuenteReferencia: String,
    val imageUrl: String? = null,
    val tasaAnualReferencia: Double? = null,
    val verificado: Boolean = false,
)

private val formatoEnteroMx: ThreadLocal<NumberFormat> = object : ThreadLocal<NumberFormat>() {
    override fun initialValue(): NumberFormat = NumberFormat.getIntegerInstance(Locale.US)
}

/**
 * Formatea [costoReferenciaPesos] y [periodicidad] en español de México para la tarjeta del catálogo.
 *
 * Ejemplos:
 * - `(0, "Anual")` -> `"Sin anualidad"`
 * - `(0, "Sin costo")` -> `"Sin costo"`
 * - `(748, "Anual")` -> `"$748 MXN / Anual"`
 * - `(1250, "Anual")` -> `"$1,250 MXN / Anual"`
 */
fun formatearCostoReferencia(costoReferenciaPesos: Long, periodicidad: String): String {
    val periodicidadLimpia = periodicidad.trim()
    if (costoReferenciaPesos <= 0L) {
        return if (periodicidadLimpia.equals("Anual", ignoreCase = true)) {
            "Sin anualidad"
        } else {
            "Sin costo"
        }
    }
    val formateador = formatoEnteroMx.get() ?: NumberFormat.getIntegerInstance(Locale.US)
    val montoFormateado = formateador.format(costoReferenciaPesos)
    return if (periodicidadLimpia.isEmpty() || periodicidadLimpia.equals("Sin costo", ignoreCase = true)) {
        "$$montoFormateado MXN"
    } else {
        "$$montoFormateado MXN / $periodicidadLimpia"
    }
}
