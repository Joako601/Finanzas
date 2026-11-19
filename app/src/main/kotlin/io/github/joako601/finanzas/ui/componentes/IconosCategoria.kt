package io.github.joako601.finanzas.ui.componentes

import androidx.annotation.DrawableRes
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoResumido
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta

/**
 * Ícono de cada categoría del catálogo inicial, por nombre.
 *
 * Limitación conocida: se busca por nombre porque la tabla `cuenta` no guarda ícono.
 * Una categoría renombrada o creada por el usuario cae en el ícono genérico. Cuando
 * exista la edición de categorías, el ícono pasa a ser una columna (con su migración).
 * IconosCategoriaTest falla si este mapa y CatalogoInicial dejan de coincidir.
 */
internal val iconosPorNombre: Map<String, Int> = mapOf(
    "Súper y despensa" to R.drawable.ic_cat_super,
    "Comida fuera" to R.drawable.ic_cat_comida,
    "Transporte" to R.drawable.ic_cat_transporte,
    "Gasolina" to R.drawable.ic_cat_gasolina,
    "Renta o hipoteca" to R.drawable.ic_cat_casa,
    "Luz, agua y gas" to R.drawable.ic_cat_servicios,
    "Internet y teléfono" to R.drawable.ic_cat_internet,
    "Salud" to R.drawable.ic_cat_salud,
    "Educación" to R.drawable.ic_cat_educacion,
    "Ropa y calzado" to R.drawable.ic_cat_ropa,
    "Entretenimiento" to R.drawable.ic_cat_entretenimiento,
    "Suscripciones" to R.drawable.ic_cat_suscripciones,
    "Mascotas" to R.drawable.ic_cat_mascotas,
    "Regalos" to R.drawable.ic_cat_regalos,
    "Otros gastos" to R.drawable.ic_cat_otros,
    "Sueldo" to R.drawable.ic_cat_sueldo,
    "Aguinaldo" to R.drawable.ic_cat_aguinaldo,
    "PTU" to R.drawable.ic_cat_ptu,
    "Ventas y trabajos extra" to R.drawable.ic_cat_ventas,
    "Reembolsos" to R.drawable.ic_cat_reembolsos,
    "Otros ingresos" to R.drawable.ic_cat_otros_ingresos,
)

@DrawableRes
fun iconoDe(movimiento: MovimientoResumido): Int = when (movimiento.clase) {
    ClaseMovimiento.GASTO, ClaseMovimiento.INGRESO ->
        iconosPorNombre[movimiento.categoria?.nombre] ?: R.drawable.ic_cat_otros
    ClaseMovimiento.SALDO_INICIAL ->
        if (movimiento.cuenta?.tipo == TipoCuenta.PASIVO) R.drawable.ic_cat_tarjeta else R.drawable.ic_cat_cartera
    ClaseMovimiento.PAGO_TARJETA -> R.drawable.ic_cat_tarjeta
    ClaseMovimiento.TRANSFERENCIA, ClaseMovimiento.OTRO -> R.drawable.ic_cat_transferencia
}
