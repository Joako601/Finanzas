package io.github.joako601.finanzas.core.domain.exportacion

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.resumir

/**
 * Los movimientos como CSV para abrir en Excel o Google Sheets (D-018). Es para leer,
 * no para restaurar: el respaldo cifrado es el que se puede recuperar.
 *
 * Formato (RFC 4180 más lo que necesita Excel en México):
 * - UTF-8 con BOM: sin él, Excel en Windows muestra "SÃºper" en vez de "Súper".
 * - Coma como separador y punto decimal, sin "$" ni separador de miles: así Excel lo
 *   toma como número.
 * - Una fila por movimiento, del más antiguo al más reciente. El monto siempre es
 *   positivo y el sentido lo da la columna Tipo, como en la app.
 *
 * Los textos de las columnas viven aquí y no en strings.xml porque son parte del formato
 * del archivo, no de la pantalla.
 */
public object ExportacionCsv {

    private val ENCABEZADO = listOf("Fecha", "Tipo", "Descripción", "Cuenta", "Categoría o destino", "Monto")

    public fun generar(movimientos: List<Movimiento>, cuentas: Map<CuentaId, Cuenta>): String = buildString {
        append('\uFEFF')
        appendFila(ENCABEZADO.map(::campo))
        movimientos
            .sortedWith(compareBy({ it.fecha }, { it.id?.valor }))
            .forEach { movimiento ->
                val resumen = movimiento.resumir(cuentas)
                appendFila(
                    listOf(
                        movimiento.fecha.toString(),
                        tipo(resumen.clase, resumen.cuenta),
                        campo(movimiento.descripcion),
                        campo(resumen.cuenta?.nombre.orEmpty()),
                        campo((resumen.categoria ?: resumen.contraparte)?.nombre.orEmpty()),
                        decimal(resumen.monto),
                    ),
                )
            }
    }

    private fun tipo(clase: ClaseMovimiento, cuenta: Cuenta?): String = when (clase) {
        ClaseMovimiento.GASTO -> "Gasto"
        ClaseMovimiento.INGRESO -> "Ingreso"
        ClaseMovimiento.SALDO_INICIAL -> if (cuenta?.tipo == TipoCuenta.PASIVO) "Deuda inicial" else "Saldo inicial"
        ClaseMovimiento.PAGO_TARJETA -> "Pago de tarjeta"
        ClaseMovimiento.TRANSFERENCIA -> "Transferencia"
        ClaseMovimiento.OTRO -> "Otro"
    }

    /** Fecha, tipo y monto los genera la app; los textos del usuario pasan antes por [campo]. */
    private fun StringBuilder.appendFila(campos: List<String>) {
        campos.joinTo(this, separator = ",")
        append("\r\n")
    }

    /**
     * Un texto que empieza con = + - @ (o tabulador/retorno) Excel lo ejecuta como fórmula;
     * con un apóstrofo delante lo muestra como texto (inyección de CSV, OWASP). Importa más
     * cuando las descripciones lleguen de notificaciones del banco (versión 0.2).
     */
    private fun campo(texto: String): String {
        val seguro = if (texto.isNotEmpty() && texto[0] in "=+-@\t\r") "'$texto" else texto
        return if (seguro.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + seguro.replace("\"", "\"\"") + "\""
        } else {
            seguro
        }
    }

    /** 123450 centavos → "1234.50". Un monto negativo sale como número negativo, no como texto. */
    private fun decimal(dinero: Dinero): String {
        val absoluto = dinero.absoluto().centavos
        val signo = if (dinero.esNegativo) "-" else ""
        return "$signo${absoluto / 100}.${(absoluto % 100).toString().padStart(2, '0')}"
    }
}
