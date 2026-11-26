package io.github.joako601.finanzas.core.data.libro

import androidx.room.withTransaction
import io.github.joako601.finanzas.core.data.FinanzasDatabase
import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Implementación del puerto del dominio sobre Room. */
internal class RepositorioLibroRoom(
    private val baseDeDatos: FinanzasDatabase,
    private val relojMillis: () -> Long = System::currentTimeMillis,
) : RepositorioLibro {

    private val dao = baseDeDatos.libroDao()

    override fun observarCuentas(): Flow<List<Cuenta>> =
        dao.observarCuentas().map { filas -> filas.map { it.aDominio() } }

    override fun observarSaldos(): Flow<Map<CuentaId, Dinero>> =
        dao.observarSaldos().map { filas -> filas.associate { CuentaId(it.cuentaId) to Dinero(it.centavos) } }

    override suspend fun saldoDe(id: CuentaId): Dinero = Dinero(dao.saldoDe(id.valor))

    override fun observarMovimientos(): Flow<List<Movimiento>> =
        dao.observarMovimientos().map { filas -> filas.map { it.aDominio() } }

    override suspend fun registrar(movimiento: Movimiento): MovimientoId {
        require(movimiento.id == null) { "El movimiento ya tiene id; para editarlo usa actualizar" }
        val id = dao.insertarCompleto(movimiento.aEntidad(relojMillis()), movimiento.asientosAEntidades())
        return MovimientoId(id)
    }

    override suspend fun eliminar(id: MovimientoId) {
        dao.eliminarMovimiento(id.valor)
    }

    override suspend fun actualizar(movimiento: Movimiento) {
        val id = requireNotNull(movimiento.id) { "Solo se edita un movimiento guardado" }
        dao.reemplazarCompleto(
            id.valor, movimiento.fecha.toEpochDays(), movimiento.descripcion, movimiento.meses, movimiento.asientosAEntidades(),
        )
    }

    override suspend fun crearCuenta(nombre: String, tipo: TipoCuenta): Cuenta = enTransaccion {
        val entidad = CuentaEntity(nombre = nombre, tipo = tipo.name, orden = dao.ordenMaximo() + 1)
        entidad.copy(id = dao.insertarCuenta(entidad)).aDominio()
    }

    override suspend fun actualizarNombreCuenta(id: CuentaId, nombre: String) {
        // Un nombre vacío o largo en la base rompería observarCuentas() (Cuenta lo rechaza al leer).
        val limpio = nombre.trim()
        require(limpio.isNotEmpty() && limpio.length <= Cuenta.LARGO_MAXIMO_NOMBRE) { "Nombre de cuenta inválido" }
        dao.actualizarNombreCuenta(id.valor, limpio)
    }

    override suspend fun cambiarArchivada(id: CuentaId, archivada: Boolean) {
        dao.cambiarArchivada(id.valor, archivada)
    }

    override suspend fun actualizarIcono(id: CuentaId, icono: String?) {
        dao.actualizarIcono(id.valor, icono)
    }

    override suspend fun cuentaDeSaldosIniciales(): Cuenta =
        checkNotNull(dao.primeraDeTipo(TipoCuenta.CAPITAL.name)) {
            "Falta la cuenta de capital del catálogo inicial"
        }.aDominio()

    // Room admite transacciones anidadas: si el bloque llama a crearCuenta, esta no abre otra.
    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T = baseDeDatos.withTransaction { bloque() }
}
