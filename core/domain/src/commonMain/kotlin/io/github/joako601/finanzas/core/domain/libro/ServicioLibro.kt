package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlinx.datetime.LocalDate

/**
 * Casos de uso que necesitan más de una operación del repositorio.
 * Equivale a una clase `@Service` con `@Transactional` en Spring.
 */
public class ServicioLibro(private val repositorio: RepositorioLibro) {

    /**
     * Crea una cuenta de dinero (efectivo o débito) y, si se indica, registra su saldo
     * inicial. Las dos cosas pasan en la misma transacción para no dejar una cuenta sin
     * el saldo que el usuario capturó.
     */
    public suspend fun crearCuentaDeDinero(nombre: String, saldoInicial: Dinero, fecha: LocalDate): Cuenta {
        val nombreLimpio = nombre.trim()
        require(nombreLimpio.isNotEmpty()) { "La cuenta necesita nombre" }
        return repositorio.enTransaccion {
            val cuenta = repositorio.crearCuenta(nombreLimpio, TipoCuenta.ACTIVO)
            if (!saldoInicial.esCero) {
                val capital = repositorio.cuentaDeSaldosIniciales()
                repositorio.registrar(Registro.saldoInicial(fecha, cuenta, saldoInicial, capital))
            }
            cuenta
        }
    }
}
