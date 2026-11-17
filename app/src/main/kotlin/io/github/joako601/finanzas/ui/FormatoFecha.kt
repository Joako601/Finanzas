package io.github.joako601.finanzas.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Fechas en la interfaz. Aquí sí se usa java.time: la app es Android (minSdk 26 trae
 * java.time) y el formateo en español es responsabilidad de la interfaz, no del dominio.
 */

private val localeMexico: Locale = Locale.forLanguageTag("es-MX")
private val formatoDia: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", localeMexico)
private val formatoDiaConAnio: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", localeMexico)
private val formatoCorto: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", localeMexico)

/** La fecha de hoy según la zona horaria del teléfono. */
fun hoy(): LocalDate = java.time.LocalDate.now().toKotlinLocalDate()

/** "lunes 16 de noviembre"; agrega el año si no es el actual. */
fun LocalDate.formatoEncabezado(hoy: LocalDate): String {
    val formato = if (year == hoy.year) formatoDia else formatoDiaConAnio
    return toJavaLocalDate().format(formato).replaceFirstChar { it.titlecase(localeMexico) }
}

/** "16 nov 2026". */
fun LocalDate.formatoCorto(): String = toJavaLocalDate().format(formatoCorto)
