package io.github.joako601.finanzas.core.domain.recordatorios

/**
 * Registro de avisos entregados. Cumple dos funciones:
 * - no repetir un aviso ya mostrado (por su clave);
 * - medir el retraso real de entrega contra la hora programada, que es el oráculo de
 *   "los recordatorios no fallan" (hora programada contra hora entregada, por teléfono).
 *
 * Los tiempos son milisegundos desde 1970 en UTC, tal como los da el reloj del sistema.
 */
public interface RepositorioRecordatorios {

    public suspend fun clavesEntregadas(): Set<String>

    public suspend fun registrarEntrega(
        clave: String,
        tipo: TipoRecordatorio,
        programadoMillis: Long,
        entregadoMillis: Long,
    )
}
