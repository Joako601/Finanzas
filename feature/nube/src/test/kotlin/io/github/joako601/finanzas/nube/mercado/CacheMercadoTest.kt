package io.github.joako601.finanzas.nube.mercado

import io.github.joako601.finanzas.nube.catalogo.ProductoFinanciero
import io.github.joako601.finanzas.nube.divisas.TipoCambio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal

internal fun datosDePrueba(uid: String = "u1", recibidoEn: Long = 1_000L) = DatosMercado(
    uid = uid,
    recibidoEn = recibidoEn,
    cotizaciones = listOf(TipoCambio("USD", "MXN", "2026-11-26", BigDecimal("18.4521"), "Banxico \"FIX\"", false)),
    indicadores = listOf(IndicadorEconomico(DatosMercado.CETES_28, "CETES 28 días", BigDecimal("7.25"), "%", "2026-11-26", "Banxico")),
    productos = listOf(
        ProductoFinanciero(1, "Azul", "Tarjeta de crédito", "BBVA", 748, "Anual", "CAT 60 %", "Banco · 2026", null, 56.5, verificado = true),
        ProductoFinanciero(2, "Nu", "Tarjeta de crédito", "Nu\\MX\n", 0, "Anual", "Sin anualidad", "CONDUSEF", "https://x/y.png", null),
    ),
    diasInhabiles = listOf("2026-11-16", "2026-12-25"),
)

class CacheMercadoTest {

    @get:Rule val carpeta = TemporaryFolder()

    private fun cache() = CacheMercado(File(carpeta.root, "mercado"))

    @Test
    fun guarda_y_lee_igual_incluidos_textos_con_escapes() {
        val datos = datosDePrueba()
        cache().guardar(datos)
        assertEquals(datos, cache().leer("u1"))
    }

    @Test
    fun otra_cuenta_no_ve_la_cache_y_la_borra() {
        cache().guardar(datosDePrueba("u1"))
        assertNull(cache().leer("u2"))
        assertNull(cache().leerCualquiera())
    }

    @Test
    fun reemplaza_sin_dejar_temporal() {
        cache().guardar(datosDePrueba(recibidoEn = 1))
        cache().guardar(datosDePrueba(recibidoEn = 2))
        assertEquals(2L, cache().leer("u1")?.recibidoEn)
        assertFalse(File(carpeta.root, "mercado/mercado.json.tmp").exists())
    }

    @Test
    fun archivo_danado_o_de_otra_version_cuenta_como_vacio() {
        val dir = File(carpeta.root, "mercado").apply { mkdirs() }
        File(dir, "mercado.json").writeText("{\"version\":1,\"uid\":")
        assertNull(cache().leerCualquiera())
        File(dir, "mercado.json").writeText(datosDePrueba().aJson().replace("\"version\":1", "\"version\":9"))
        assertNull(cache().leerCualquiera())
    }

    @Test
    fun borrar_deja_vacio() {
        cache().guardar(datosDePrueba())
        cache().borrar()
        assertNull(cache().leerCualquiera())
    }
}
