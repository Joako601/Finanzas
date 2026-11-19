package io.github.joako601.finanzas.core.data.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.EstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

internal class RepositorioTarjetasRoom(
    private val dao: TarjetasDao,
    private val relojMillis: () -> Long = System::currentTimeMillis,
) : RepositorioTarjetas {

    override fun observarTarjetas(): Flow<List<Tarjeta>> = dao.observarTarjetas().map { filas ->
        filas.map {
            Tarjeta(
                cuenta = Cuenta(CuentaId(it.cuentaId), it.nombre, TipoCuenta.valueOf(it.tipo), it.archivada),
                lineaDeCredito = Dinero(it.lineaCentavos),
                diaDeCorte = it.diaCorte,
                diaLimite = it.diaLimite,
            )
        }
    }

    override fun observarEstadosDeCuenta(): Flow<List<EstadoDeCuenta>> = dao.observarEstadosDeCuenta().map { filas ->
        filas.map {
            EstadoDeCuenta(
                id = it.id,
                tarjeta = CuentaId(it.tarjetaId),
                fechaCorte = LocalDate.fromEpochDays(it.fechaCorte),
                fechaLimite = LocalDate.fromEpochDays(it.fechaLimite),
                pagoSinIntereses = Dinero(it.pagoSinIntereses),
                pagoMinimo = Dinero(it.pagoMinimo),
                cortePredicho = it.cortePredicho?.let(LocalDate::fromEpochDays),
                limitePredicho = it.limitePredicho?.let(LocalDate::fromEpochDays),
            )
        }
    }

    override suspend fun guardarTarjeta(cuenta: Cuenta, lineaDeCredito: Dinero, diaDeCorte: Int, diaLimite: Int): Tarjeta {
        // Validar con el constructor del dominio ANTES de escribir.
        val tarjeta = Tarjeta(cuenta, lineaDeCredito, diaDeCorte, diaLimite)
        dao.insertarTarjeta(TarjetaEntity(cuenta.id.valor, lineaDeCredito.centavos, diaDeCorte, diaLimite))
        return tarjeta
    }

    override suspend fun guardarEstadoDeCuenta(estado: EstadoDeCuenta) {
        dao.guardarEstadoDeCuenta(
            EstadoDeCuentaEntity(
                tarjetaId = estado.tarjeta.valor,
                fechaCorte = estado.fechaCorte.toEpochDays(),
                fechaLimite = estado.fechaLimite.toEpochDays(),
                pagoSinIntereses = estado.pagoSinIntereses.centavos,
                pagoMinimo = estado.pagoMinimo.centavos,
                cortePredicho = estado.cortePredicho?.toEpochDays(),
                limitePredicho = estado.limitePredicho?.toEpochDays(),
                capturadoEnMillis = relojMillis(),
            ),
        )
    }
}
