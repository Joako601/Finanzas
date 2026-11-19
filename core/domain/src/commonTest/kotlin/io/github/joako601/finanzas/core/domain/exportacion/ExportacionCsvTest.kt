package io.github.joako601.finanzas.core.domain.exportacion

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Asiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportacionCsvTest {

    private val efectivo = Cuenta(CuentaId(1), "Efectivo", TipoCuenta.ACTIVO)
    private val nu = Cuenta(CuentaId(2), "Nu", TipoCuenta.PASIVO)
    private val capital = Cuenta(CuentaId(3), "Saldos iniciales", TipoCuenta.CAPITAL)
    private val super_ = Cuenta(CuentaId(4), "Súper", TipoCuenta.GASTO)
    private val sueldo = Cuenta(CuentaId(5), "Sueldo", TipoCuenta.INGRESO)
    private val debito = Cuenta(CuentaId(6), "Débito", TipoCuenta.ACTIVO)
    private val cuentas = listOf(efectivo, nu, capital, super_, sueldo, debito).associateBy { it.id }

    private fun mov(id: Long, dia: Int, descripcion: String, vararg asientos: Pair<Cuenta, Long>) = Movimiento(
        MovimientoId(id),
        LocalDate(2026, 11, dia),
        descripcion,
        asientos.map { (cuenta, centavos) -> Asiento(cuenta.id, Dinero(centavos)) },
    )

    private fun filas(csv: String) = csv.removePrefix("\uFEFF").removeSuffix("\r\n").split("\r\n")

    @Test
    fun empieza_con_bom_y_encabezado_y_usa_crlf() {
        val csv = ExportacionCsv.generar(emptyList(), cuentas)
        assertTrue(csv.startsWith("\uFEFF"), "Excel necesita el BOM para leer acentos en UTF-8")
        assertEquals("\uFEFFFecha,Tipo,Descripción,Cuenta,Categoría o destino,Monto\r\n", csv)
    }

    @Test
    fun una_fila_por_movimiento_con_monto_sin_formato_de_moneda() {
        val csv = ExportacionCsv.generar(
            listOf(
                mov(1, 5, "Despensa", super_ to 123_450, efectivo to -123_450),
                mov(2, 15, "Quincena", efectivo to 5, sueldo to -5),
            ),
            cuentas,
        )
        assertEquals(
            listOf(
                "Fecha,Tipo,Descripción,Cuenta,Categoría o destino,Monto",
                "2026-11-05,Gasto,Despensa,Efectivo,Súper,1234.50",
                "2026-11-15,Ingreso,Quincena,Efectivo,Sueldo,0.05",
            ),
            filas(csv),
        )
    }

    @Test
    fun ordena_por_fecha_y_luego_por_orden_de_registro() {
        val csv = ExportacionCsv.generar(
            listOf(
                mov(3, 10, "c", super_ to 100, efectivo to -100),
                mov(2, 1, "b", super_ to 100, efectivo to -100),
                mov(1, 10, "a", super_ to 100, efectivo to -100),
            ),
            cuentas,
        )
        assertEquals(listOf("b", "a", "c"), filas(csv).drop(1).map { it.split(",")[2] })
    }

    @Test
    fun pago_de_tarjeta_transferencia_saldo_inicial_y_deuda_inicial() {
        val csv = ExportacionCsv.generar(
            listOf(
                mov(1, 1, "", efectivo to 100_000, capital to -100_000),
                mov(2, 1, "", nu to -50_000, capital to 50_000),
                mov(3, 2, "", nu to 20_000, efectivo to -20_000),
                mov(4, 3, "cajero", efectivo to 50_000, debito to -50_000),
            ),
            cuentas,
        )
        assertEquals(
            listOf(
                "2026-11-01,Saldo inicial,,Efectivo,,1000.00",
                "2026-11-01,Deuda inicial,,Nu,,500.00",
                "2026-11-02,Pago de tarjeta,,Efectivo,Nu,200.00",
                "2026-11-03,Transferencia,cajero,Débito,Efectivo,500.00",
            ),
            filas(csv).drop(1),
        )
    }

    @Test
    fun escapa_comas_comillas_y_saltos_de_linea() {
        val csv = ExportacionCsv.generar(
            listOf(mov(1, 1, "Tacos, \"los buenos\"\ny refresco", super_ to 100, efectivo to -100)),
            cuentas,
        )
        assertEquals(
            "2026-11-01,Gasto,\"Tacos, \"\"los buenos\"\"\ny refresco\",Efectivo,Súper,1.00",
            csv.removePrefix("\uFEFF").removeSuffix("\r\n").substringAfter("\r\n"),
        )
    }

    @Test
    fun un_texto_que_parece_formula_no_se_ejecuta_en_la_hoja_de_calculo() {
        listOf("=1+1", "+52 55", "-10", "@SUMA(A1)").forEach { peligroso ->
            val csv = ExportacionCsv.generar(listOf(mov(1, 1, peligroso, super_ to 100, efectivo to -100)), cuentas)
            assertEquals("'$peligroso", filas(csv)[1].split(",")[2], "sin escapar: $peligroso")
        }
    }

    @Test
    fun tabulador_y_retorno_al_inicio_tambien_se_neutralizan() {
        listOf("\t=1+1" to "'\t=1+1", "\r=1+1" to "\"'\r=1+1\"").forEach { (texto, esperado) ->
            val csv = ExportacionCsv.generar(listOf(mov(1, 1, texto, super_ to 100, efectivo to -100)), cuentas)
            assertEquals(esperado, csv.removePrefix("\uFEFF").removeSuffix("\r\n").substringAfter("\r\n").split(",")[2], "sin neutralizar: $texto")
        }
    }

    @Test
    fun un_retorno_en_medio_del_texto_va_entre_comillas() {
        val csv = ExportacionCsv.generar(listOf(mov(1, 1, "a\rb", super_ to 100, efectivo to -100)), cuentas)
        assertEquals("\"a\rb\"", csv.removePrefix("\uFEFF").removeSuffix("\r\n").substringAfter("\r\n").split(",")[2])
    }
}
