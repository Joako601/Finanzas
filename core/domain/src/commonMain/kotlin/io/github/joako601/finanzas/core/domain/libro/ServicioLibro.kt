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

    /**
     * Renombra una cuenta de dinero (efectivo o débito) y, si el [nuevoSaldo] difiere del
     * [saldoActual], registra un movimiento de ajuste contra la cuenta de capital para que
     * el libro de partida doble cuadre al centavo.
     */
    public suspend fun editarCuentaDeDinero(
        cuenta: Cuenta,
        nuevoNombre: String,
        saldoActual: Dinero,
        nuevoSaldo: Dinero,
        fecha: LocalDate,
    ): Cuenta {
        val nombreLimpio = nuevoNombre.trim()
        require(nombreLimpio.isNotEmpty()) { "La cuenta necesita nombre" }
        require(cuenta.tipo == TipoCuenta.ACTIVO) { "Solo se editan cuentas de dinero" }
        return repositorio.enTransaccion {
            val actualizada = cuenta.copy(nombre = nombreLimpio)
            if (nombreLimpio != cuenta.nombre) {
                repositorio.actualizarNombreCuenta(cuenta.id, nombreLimpio)
            }
            val diferencia = nuevoSaldo - saldoActual
            if (!diferencia.esCero) {
                val capital = repositorio.cuentaDeSaldosIniciales()
                repositorio.registrar(Registro.saldoInicial(fecha, actualizada, diferencia, capital))
            }
            actualizada
        }
    }
}
