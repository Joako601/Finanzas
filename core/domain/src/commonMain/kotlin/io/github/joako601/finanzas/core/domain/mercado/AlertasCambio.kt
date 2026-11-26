package io.github.joako601.finanzas.core.domain.mercado

public enum class CondicionAlerta { BAJA_DE, SUBE_DE }

/**
 * "Avísame cuando USD/MXN baje de 17.00". Vive solo en el teléfono.
 *
 * [cumplida] es cómo estaba la condición la última vez que se evaluó: solo se avisa cuando
 * pasa de no cumplida a cumplida, así no se repite el aviso con cada actualización. Si la
 * tasa vuelve a salir del umbral, la alerta se rearma sola.
 */
public data class AlertaCambio(
    val id: String,
    val moneda: String,
    val condicion: CondicionAlerta,
    val umbral: Double,
    val cumplida: Boolean = false,
) {
    init {
        require(umbral > 0 && !umbral.isNaN()) { "El umbral debe ser positivo" }
    }

    public fun seCumpleCon(tasa: Double): Boolean = when (condicion) {
        CondicionAlerta.BAJA_DE -> tasa < umbral
        CondicionAlerta.SUBE_DE -> tasa > umbral
    }
}

/** Alertas actualizadas con su nuevo estado y las que hay que avisar ahora. */
public data class EvaluacionAlertas(
    val alertas: List<AlertaCambio>,
    val disparadas: List<Disparo>,
) {
    public data class Disparo(val alerta: AlertaCambio, val tasa: Double)
}

public object EvaluadorAlertas {
    /** [tasas] es pesos por unidad de cada moneda (USD → 17.05). Sin tasa, la alerta no cambia. */
    public fun evaluar(alertas: List<AlertaCambio>, tasas: Map<String, Double>): EvaluacionAlertas {
        val disparadas = mutableListOf<EvaluacionAlertas.Disparo>()
        val nuevas = alertas.map { alerta ->
            val tasa = tasas[alerta.moneda] ?: return@map alerta
            val cumple = alerta.seCumpleCon(tasa)
            if (cumple && !alerta.cumplida) disparadas += EvaluacionAlertas.Disparo(alerta, tasa)
            alerta.copy(cumplida = cumple)
        }
        return EvaluacionAlertas(nuevas, disparadas)
    }
}
