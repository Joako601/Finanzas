package io.github.joako601.finanzas.ui.perfil

import android.content.Context
import androidx.core.content.edit
import io.github.joako601.finanzas.core.domain.recordatorios.HorasAviso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalTime

/** Tema de la app: el del sistema o uno fijo (Perfil → Apariencia). */
enum class ModoTema { SISTEMA, CLARO, OSCURO }

/** Horas que se ofrecen en Perfil → Recordatorios: pocas opciones claras en lugar de un reloj. */
val HORAS_DIA = listOf(7, 8, 9, 10, 12)
val HORAS_NOCHE = listOf(18, 19, 20, 21, 22)

/**
 * Apariencia y horas de los avisos, en las preferencias privadas de la app. Solo ajustes:
 * ningún monto ni nombre. Las horas van en el archivo de avisos para que "Borrar datos"
 * las reinicie con lo demás; el tema se conserva.
 */
class PreferenciasApp(context: Context) {
    private val apariencia = context.getSharedPreferences("apariencia", Context.MODE_PRIVATE)
    private val avisos = context.getSharedPreferences("finanzas_avisos", Context.MODE_PRIVATE)

    var tema: ModoTema
        get() = apariencia.getString("tema", null)?.let { runCatching { ModoTema.valueOf(it) }.getOrNull() } ?: ModoTema.SISTEMA
        set(valor) {
            apariencia.edit { putString("tema", valor.name) }
            temaActual.value = valor
        }

    var horaDia: Int
        get() = avisos.getInt("hora_dia", 9).takeIf { it in HORAS_DIA } ?: 9
        set(valor) = avisos.edit { putInt("hora_dia", valor) }

    var horaNoche: Int
        get() = avisos.getInt("hora_noche", 19).takeIf { it in HORAS_NOCHE } ?: 19
        set(valor) = avisos.edit { putInt("hora_noche", valor) }

    fun horasAviso(): HorasAviso = HorasAviso(dia = LocalTime(horaDia, 0), noche = LocalTime(horaNoche, 0))

    companion object {
        // shortcut: estado de proceso para que MainActivity cambie el tema al instante; se carga en onCreate.
        private val temaActual = MutableStateFlow(ModoTema.SISTEMA)
        val tema: StateFlow<ModoTema> = temaActual.asStateFlow()

        fun cargarTema(context: Context) {
            temaActual.value = PreferenciasApp(context).tema
        }
    }
}
