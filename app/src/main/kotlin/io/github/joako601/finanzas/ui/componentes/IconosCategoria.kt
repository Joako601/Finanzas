package io.github.joako601.finanzas.ui.componentes

import androidx.annotation.DrawableRes
import io.github.joako601.finanzas.R
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.MovimientoResumido
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta

/**
 * Ícono de cada categoría del catálogo inicial, por nombre.
 *
 * Las del catálogo se reconocen por nombre; una categoría propia o renombrada usa el ícono
 * que el usuario eligió (`cuenta.icono`, v7). IconosCategoriaTest falla si este mapa y
 * CatalogoInicial dejan de coincidir.
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
    "Impuestos y predial" to R.drawable.ic_cat_impuestos,
    "Sueldo" to R.drawable.ic_cat_sueldo,
    "Aguinaldo" to R.drawable.ic_cat_aguinaldo,
    "PTU" to R.drawable.ic_cat_ptu,
    "Ventas y trabajos extra" to R.drawable.ic_cat_ventas,
    "Reembolsos" to R.drawable.ic_cat_reembolsos,
    "Otros ingresos" to R.drawable.ic_cat_otros_ingresos,
)

/**
 * Íconos que se pueden elegir para una categoría propia. La clave es lo que se guarda en la
 * base: un id de recurso cambia entre compilaciones, la clave no.
 */
val iconosElegibles: Map<String, Int> = linkedMapOf(
    "super" to R.drawable.ic_cat_super,
    "comida" to R.drawable.ic_cat_comida,
    "transporte" to R.drawable.ic_cat_transporte,
    "gasolina" to R.drawable.ic_cat_gasolina,
    "casa" to R.drawable.ic_cat_casa,
    "servicios" to R.drawable.ic_cat_servicios,
    "internet" to R.drawable.ic_cat_internet,
    "salud" to R.drawable.ic_cat_salud,
    "educacion" to R.drawable.ic_cat_educacion,
    "ropa" to R.drawable.ic_cat_ropa,
    "entretenimiento" to R.drawable.ic_cat_entretenimiento,
    "suscripciones" to R.drawable.ic_cat_suscripciones,
    "mascotas" to R.drawable.ic_cat_mascotas,
    "regalos" to R.drawable.ic_cat_regalos,
    "impuestos" to R.drawable.ic_cat_impuestos,
    "sueldo" to R.drawable.ic_cat_sueldo,
    "ventas" to R.drawable.ic_cat_ventas,
    "reembolsos" to R.drawable.ic_cat_reembolsos,
    "otros" to R.drawable.ic_cat_otros,
)

/** El ícono elegido si lo hay; si no, el del catálogo por nombre o el genérico. */
@DrawableRes
fun iconoDeCategoria(categoria: Cuenta?): Int = iconoDeCategoria(categoria?.nombre, categoria?.icono)

/** Para quien solo guarda nombre y clave de ícono (p. ej. las filas de Análisis). */
@DrawableRes
fun iconoDeCategoria(nombre: String?, icono: String?): Int = icono?.let(iconosElegibles::get) ?: iconoDeCategoria(nombre)

/** Ícono de una categoría por su nombre; el genérico si no está en el catálogo. */
@DrawableRes
fun iconoDeCategoria(nombre: String?): Int = iconosPorNombre[nombre] ?: R.drawable.ic_cat_otros

@DrawableRes
fun iconoDe(movimiento: MovimientoResumido): Int = when (movimiento.clase) {
    ClaseMovimiento.GASTO, ClaseMovimiento.INGRESO -> iconoDeCategoria(movimiento.categoria)
    ClaseMovimiento.SALDO_INICIAL ->
        if (movimiento.cuenta?.tipo == TipoCuenta.PASIVO) R.drawable.ic_cat_tarjeta else R.drawable.ic_cat_cartera
    ClaseMovimiento.PAGO_TARJETA -> R.drawable.ic_cat_tarjeta
    ClaseMovimiento.TRANSFERENCIA, ClaseMovimiento.OTRO -> R.drawable.ic_cat_transferencia
}
