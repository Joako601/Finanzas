package io.github.joako601.finanzas.ui

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

/*
 * Fechas en la interfaz. Aquí sí se usa java.time: la app es Android (minSdk 26 trae
 * java.time) y el formateo en español es responsabilidad de la interfaz, no del dominio.
 */

private val localeMexico: Locale = Locale.forLanguageTag("es-MX")
private val formatoDia: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", localeMexico)
private val formatoDiaConAnio: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", localeMexico)
private val formatoCorto: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", localeMexico)
private val patronDiaCorto: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM", localeMexico)
private val patronDiaCortoOtroAnio: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM yyyy", localeMexico)
private val formatoMes: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", localeMexico)

/** La fecha de hoy según la zona horaria del teléfono. */
fun hoy(): LocalDate = java.time.LocalDate.now().toKotlinLocalDate()

/** La fecha de hoy, revisada cada minuto: si la app queda abierta pasada la medianoche, emite el día nuevo. */
fun diasDesde(fechaDeHoy: () -> LocalDate): Flow<LocalDate> = flow {
    while (true) {
        emit(fechaDeHoy())
        delay(60_000)
    }
}.distinctUntilChanged()

/** "Noviembre": nombre del mes con inicial mayúscula en español de México. */
fun LocalDate.formatoMes(): String =
    toJavaLocalDate().format(formatoMes).replaceFirstChar { it.titlecase(localeMexico) }

/** "noviembre": nombre del mes en minúscula para usar en medio de una frase. */
fun LocalDate.formatoMesEnFrase(): String =
    toJavaLocalDate().format(formatoMes).lowercase(localeMexico)

/** "lunes 16 de noviembre"; agrega el año si no es el actual. */
fun LocalDate.formatoEncabezado(hoy: LocalDate): String {
    val formato = if (year == hoy.year) formatoDia else formatoDiaConAnio
    return toJavaLocalDate().format(formato).replaceFirstChar { it.titlecase(localeMexico) }
}

/** "16 nov 2026". */
fun LocalDate.formatoCorto(): String = toJavaLocalDate().format(formatoCorto)

/** "viernes 5 dic"; agrega el año si no es el de [hoy]. Día completo: "mar" sería martes o marzo. */
fun LocalDate.formatoDiaCorto(hoy: LocalDate): String =
    toJavaLocalDate().format(if (year == hoy.year) patronDiaCorto else patronDiaCortoOtroAnio)
