package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServicioLibroTest {

    private val fecha = LocalDate(2026, 11, 15)

    @Test
    fun crea_cuenta_con_saldo_inicial() = runTest {
        val repositorio = RepositorioEnMemoria()
        val servicio = ServicioLibro(repositorio)

        val cuenta = servicio.crearCuentaDeDinero("  Nu débito ", Dinero(500_000), fecha)

        assertEquals("Nu débito", cuenta.nombre)
        assertEquals(TipoCuenta.ACTIVO, cuenta.tipo)
        assertEquals(Dinero(500_000), repositorio.observarSaldos().first()[cuenta.id])
        val movimientos = repositorio.observarMovimientos().first()
        assertEquals(1, movimientos.size)
        assertEquals(1, repositorio.transacciones)
    }

    @Test
    fun saldo_inicial_cero_no_registra_movimiento() = runTest {
        val repositorio = RepositorioEnMemoria()
        ServicioLibro(repositorio).crearCuentaDeDinero("Cartera", Dinero.CERO, fecha)
        assertTrue(repositorio.observarMovimientos().first().isEmpty())
    }

    @Test
    fun editar_cuenta_renombra_y_ajusta_saldo_con_movimiento_contable() = runTest {
        val repositorio = RepositorioEnMemoria()
        val servicio = ServicioLibro(repositorio)
        val cuenta = servicio.crearCuentaDeDinero("Nu debito", Dinero.pesos(1_000), fecha)

        val actualizada = servicio.editarCuentaDeDinero(
            cuenta = cuenta,
            nuevoNombre = "  Nu débito ",
            saldoActual = Dinero.pesos(1_000),
            nuevoSaldo = Dinero.pesos(1_350),
            fecha = fecha,
        )

        assertEquals("Nu débito", actualizada.nombre)
        assertEquals(Dinero.pesos(1_350), repositorio.observarSaldos().first()[cuenta.id])
        assertEquals(2, repositorio.observarMovimientos().first().size)

        // Ajuste hacia abajo ($1,350 -> $900)
        servicio.editarCuentaDeDinero(
            cuenta = actualizada,
            nuevoNombre = "Nu débito",
            saldoActual = Dinero.pesos(1_350),
            nuevoSaldo = Dinero.pesos(900),
            fecha = fecha,
        )
        assertEquals(Dinero.pesos(900), repositorio.observarSaldos().first()[cuenta.id])
        assertEquals(3, repositorio.observarMovimientos().first().size)

        // Solo cambio de nombre ($900 -> $900): no agrega movimiento nuevo
        val renombrada = servicio.editarCuentaDeDinero(
            cuenta = actualizada,
            nuevoNombre = "Nu principal",
            saldoActual = Dinero.pesos(900),
            nuevoSaldo = Dinero.pesos(900),
            fecha = fecha,
        )
        assertEquals("Nu principal", renombrada.nombre)
        assertEquals(Dinero.pesos(900), repositorio.observarSaldos().first()[cuenta.id])
        assertEquals(3, repositorio.observarMovimientos().first().size)
    }

    @Test
    fun editar_saldo_deja_lo_escrito_aunque_entro_un_gasto_con_el_dialogo_abierto() = runTest {
        val repositorio = RepositorioEnMemoria()
        val servicio = ServicioLibro(repositorio)
        val cuenta = servicio.crearCuentaDeDinero("Efectivo", Dinero.pesos(1_000), fecha)
        val gasto = repositorio.crearCuenta("Súper", TipoCuenta.GASTO)
        // El diálogo se abrió viendo $1,000; mientras, se registró un gasto de $100.
        repositorio.registrar(Registro.gasto(fecha, Dinero.pesos(100), cuenta, gasto))

        servicio.editarCuentaDeDinero(cuenta, "Efectivo", Dinero.pesos(1_000), Dinero.pesos(500), fecha)

        assertEquals(Dinero.pesos(500), repositorio.observarSaldos().first()[cuenta.id])
    }

    @Test
    fun el_ajuste_lee_el_saldo_dentro_de_la_transaccion_y_se_describe_como_ajuste() = runTest {
        val base = RepositorioEnMemoria()
        val servicio0 = ServicioLibro(base)
        val cuenta = servicio0.crearCuentaDeDinero("Efectivo", Dinero.pesos(1_000), fecha)
        val gasto = base.crearCuenta("Súper", TipoCuenta.GASTO)
        // Un gasto de $100 se confirma justo cuando empieza la transacción del ajuste.
        val conCarrera = object : RepositorioLibro by base {
            override suspend fun <T> enTransaccion(bloque: suspend () -> T): T {
                base.registrar(Registro.gasto(fecha, Dinero.pesos(100), cuenta, gasto))
                return base.enTransaccion(bloque)
            }
        }

        ServicioLibro(conCarrera).editarCuentaDeDinero(cuenta, "Efectivo", Dinero.pesos(1_000), Dinero.pesos(500), fecha)

        assertEquals(Dinero.pesos(500), base.observarSaldos().first()[cuenta.id])
        assertEquals("Ajuste de saldo", base.observarMovimientos().first().first().descripcion)
    }

    @Test
    fun una_cuenta_de_dinero_rechaza_nombres_de_mas_de_60() = runTest {
        val repositorio = RepositorioEnMemoria()
        val servicio = ServicioLibro(repositorio)
        assertFailsWith<IllegalArgumentException> { servicio.crearCuentaDeDinero("x".repeat(61), Dinero.CERO, fecha) }
        val cuenta = servicio.crearCuentaDeDinero("Efectivo", Dinero.CERO, fecha)
        assertFailsWith<IllegalArgumentException> {
            servicio.editarCuentaDeDinero(cuenta, "x".repeat(61), Dinero.CERO, Dinero.CERO, fecha)
        }
        assertEquals("Efectivo", repositorio.observarCuentas().first().single { it.id == cuenta.id }.nombre)
    }

    @Test
    fun solo_renombrar_con_el_dialogo_abierto_no_deshace_un_gasto() = runTest {
        val repositorio = RepositorioEnMemoria()
        val servicio = ServicioLibro(repositorio)
        val cuenta = servicio.crearCuentaDeDinero("Efectivo", Dinero.pesos(1_000), fecha)
        val gasto = repositorio.crearCuenta("Súper", TipoCuenta.GASTO)
        repositorio.registrar(Registro.gasto(fecha, Dinero.pesos(100), cuenta, gasto))

        // El saldo no se tocó: sigue el que se vio al abrir.
        servicio.editarCuentaDeDinero(cuenta, "Cartera", Dinero.pesos(1_000), Dinero.pesos(1_000), fecha)

        assertEquals(Dinero.pesos(900), repositorio.observarSaldos().first()[cuenta.id])
    }

    // ---- Categorías propias (Tanda 3) ----

    @Test
    fun crear_categoria_valida_nombre_y_repetidos_y_guarda_icono() = runTest {
        val repositorio = RepositorioEnMemoria()
        val servicio = ServicioLibro(repositorio)
        repositorio.crearCuenta("Súper y despensa", TipoCuenta.GASTO)

        assertEquals(setOf(ErrorCategoria.NOMBRE_VACIO), servicio.guardarCategoria(null, "  ", TipoCuenta.GASTO, null))
        // Repetido sin importar mayúsculas ni espacios; el mismo nombre en ingresos sí se permite.
        assertEquals(setOf(ErrorCategoria.NOMBRE_REPETIDO), servicio.guardarCategoria(null, " súper Y despensa", TipoCuenta.GASTO, null))
        assertEquals(emptySet<ErrorCategoria>(), servicio.guardarCategoria(null, "Súper y despensa", TipoCuenta.INGRESO, null))

        assertEquals(emptySet<ErrorCategoria>(), servicio.guardarCategoria(null, " Gimnasio ", TipoCuenta.GASTO, "salud"))
        val gimnasio = repositorio.observarCuentas().first().single { it.nombre == "Gimnasio" }
        assertEquals(TipoCuenta.GASTO, gimnasio.tipo)
        assertEquals("salud", gimnasio.icono)
    }

    @Test
    fun editar_categoria_renombra_y_cambia_icono_sin_chocar_consigo_misma() = runTest {
        val repositorio = RepositorioEnMemoria()
        val servicio = ServicioLibro(repositorio)
        val cafe = repositorio.crearCuenta("Café", TipoCuenta.GASTO)

        // Guardar con el mismo nombre no es "repetido".
        assertEquals(emptySet<ErrorCategoria>(), servicio.guardarCategoria(cafe, "Café", TipoCuenta.GASTO, "comida"))
        assertEquals(emptySet<ErrorCategoria>(), servicio.guardarCategoria(cafe, "Cafecito", TipoCuenta.GASTO, "comida"))

        val editada = repositorio.observarCuentas().first().single { it.id == cafe.id }
        assertEquals("Cafecito", editada.nombre)
        assertEquals("comida", editada.icono)
    }

}
