package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.nube.catalogo.ProductoFinanciero
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductosParaCompararTest {

    private fun producto(id: Long, categoria: String, costo: Long, periodicidad: String, fuente: String = "Banxico · 2026") =
        ProductoFinanciero(id, "P$id", categoria, "Banco", costo, periodicidad, "", fuente, null, 50.0)

    @Test
    fun soloCreditoVerificadoYAnualizado() {
        val productos = listOf(
            producto(1, "Tarjeta de crédito", 748, "Anual"),
            producto(2, "Tarjeta de crédito", 100, "Mensual"),
            producto(3, "Tarjeta de crédito", 0, "Sin costo"),
            producto(4, "Tarjeta de crédito", 500, "Anual", fuente = "Ejemplo sin verificar · BBVA"),
            producto(5, "Tarjeta de débito", 0, "Sin costo"),
            producto(6, "Tarjeta de crédito", 300, "Trimestral"),
        )

        val r = productosParaComparar(productos)

        assertEquals(listOf(1L, 2L, 3L), r.map { it.id })
        assertEquals(listOf(Dinero.pesos(748), Dinero.pesos(1_200), Dinero.CERO), r.map { it.anualidad })
    }
}
