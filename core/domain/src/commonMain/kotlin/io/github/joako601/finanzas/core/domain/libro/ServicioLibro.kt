package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlinx.coroutines.flow.first
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
        require(nombreLimpio.length <= Cuenta.LARGO_MAXIMO_NOMBRE) { "Nombre de cuenta demasiado largo" }
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
     * Renombra una cuenta de dinero (efectivo o débito) y, si el usuario cambió el saldo
     * ([nuevoSaldo] distinto del [saldoActual] que vio al abrir), registra un ajuste contra
     * capital para que el saldo quede en [nuevoSaldo] aunque mientras entrara otro movimiento.
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
        require(nombreLimpio.length <= Cuenta.LARGO_MAXIMO_NOMBRE) { "Nombre de cuenta demasiado largo" }
        require(cuenta.tipo == TipoCuenta.ACTIVO) { "Solo se editan cuentas de dinero" }
        return repositorio.enTransaccion {
            val actualizada = cuenta.copy(nombre = nombreLimpio)
            if (nombreLimpio != cuenta.nombre) {
                repositorio.actualizarNombreCuenta(cuenta.id, nombreLimpio)
            }
            // El saldo se lee dentro de la transacción: un movimiento que entre en medio no descuadra el ajuste.
            if (nuevoSaldo != saldoActual) {
                val diferencia = nuevoSaldo - repositorio.saldoDe(cuenta.id)
                if (!diferencia.esCero) {
                    val capital = repositorio.cuentaDeSaldosIniciales()
                    repositorio.registrar(
                        Registro.saldoInicial(fecha, actualizada, diferencia, capital, descripcion = Registro.DESCRIPCION_AJUSTE),
                    )
                }
            }
            actualizada
        }
    }

    /**
     * Crea ([editando] = null) o edita una categoría de gasto o ingreso con su ícono local.
     * Devuelve los errores sin guardar nada si los hay. Un nombre repetido (sin importar
     * mayúsculas ni espacios) en el mismo tipo confundiría al elegirla, incluso si está archivada.
     */
    public suspend fun guardarCategoria(editando: Cuenta?, nombre: String, tipo: TipoCuenta, icono: String?): Set<ErrorCategoria> {
        require(tipo == TipoCuenta.GASTO || tipo == TipoCuenta.INGRESO) { "Una categoría es de gasto o de ingreso" }
        require(editando == null || editando.tipo == tipo) { "No se cambia el tipo de una categoría" }
        val limpio = nombre.trim()
        if (limpio.isEmpty() || limpio.length > Cuenta.LARGO_MAXIMO_NOMBRE) return setOf(ErrorCategoria.NOMBRE_VACIO)
        val repetido = repositorio.observarCuentas().first()
            .any { it.tipo == tipo && it.id != editando?.id && it.nombre.trim().lowercase() == limpio.lowercase() }
        if (repetido) return setOf(ErrorCategoria.NOMBRE_REPETIDO)
        repositorio.enTransaccion {
            val id = if (editando == null) {
                repositorio.crearCuenta(limpio, tipo).id
            } else {
                if (limpio != editando.nombre) repositorio.actualizarNombreCuenta(editando.id, limpio)
                editando.id
            }
            repositorio.actualizarIcono(id, icono)
        }
        return emptySet()
    }

}


/** Lo que impide guardar una categoría propia. */
public enum class ErrorCategoria { NOMBRE_VACIO, NOMBRE_REPETIDO }
