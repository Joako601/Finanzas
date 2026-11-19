package io.github.joako601.finanzas.core.domain.libro

/**
 * Cuentas y categorías con las que nace la base de datos.
 *
 * Las categorías siguen cómo se gasta en México (súper, gasolina, servicios como CFE y
 * gas, transporte) y cómo se cobra (quincena, aguinaldo, PTU). Son un punto de partida:
 * se ajustarán con tus gastos reales de la fase 0.
 */
public object CatalogoInicial {

    public data class Semilla(val nombre: String, val tipo: TipoCuenta)

    public val CUENTA_EFECTIVO: Semilla = Semilla("Efectivo", TipoCuenta.ACTIVO)
    public val CUENTA_SALDOS_INICIALES: Semilla = Semilla("Saldos iniciales", TipoCuenta.CAPITAL)

    public val categoriasDeGasto: List<String> = listOf(
        "Súper y despensa",
        "Comida fuera",
        "Transporte",
        "Gasolina",
        "Renta o hipoteca",
        "Luz, agua y gas",
        "Internet y teléfono",
        "Salud",
        "Educación",
        "Ropa y calzado",
        "Entretenimiento",
        "Suscripciones",
        "Mascotas",
        "Regalos",
        "Otros gastos",
        // Agregada en la versión 4 de la base; la migración la inserta en bases existentes.
        "Impuestos y predial",
    )

    public val categoriasDeIngreso: List<String> = listOf(
        "Sueldo",
        "Aguinaldo",
        "PTU",
        "Ventas y trabajos extra",
        "Reembolsos",
        "Otros ingresos",
    )

    /** Todo el catálogo, en el orden en que se muestra. */
    public val todas: List<Semilla> =
        listOf(CUENTA_EFECTIVO, CUENTA_SALDOS_INICIALES) +
            categoriasDeGasto.map { Semilla(it, TipoCuenta.GASTO) } +
            categoriasDeIngreso.map { Semilla(it, TipoCuenta.INGRESO) }
}
