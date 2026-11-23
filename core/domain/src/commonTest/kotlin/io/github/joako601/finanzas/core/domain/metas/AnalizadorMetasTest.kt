package io.github.joako601.finanzas.core.domain.metas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnalizadorMetasTest {

    private val efectivo = Cuenta(CuentaId(1), "Efectivo", TipoCuenta.ACTIVO)
    private val debito = Cuenta(CuentaId(2), "Débito", TipoCuenta.ACTIVO)
    private val cuentas = listOf(efectivo, debito)

    @Test
    fun calcula_progreso_cuota_quincenal_y_mensual_hacia_arriba() {
        val hoy = LocalDate(2026, 11, 1)
        val meta = MetaAhorro(
            id = MetaAhorroId(1),
            nombre = "Seguro del auto",
            objetivo = Dinero.pesos(6_000),
            apartado = Dinero.pesos(1_000),
            fechaLimite = LocalDate(2027, 1, 30), // 90 días -> 6 quincenas, 3 meses
        )

        val analisis = AnalizadorMetas.analizarUna(meta, hoy)

        assertEquals(EstadoMetaAhorro.EN_CURSO, analisis.estado)
        assertEquals(Dinero.pesos(5_000), analisis.faltante)
        assertEquals(90, analisis.diasRestantes)
        // 500,000 centavos / 6 = 83,333.33 -> redondeo hacia arriba = 83,334 centavos ($833.34)
        assertEquals(Dinero(83_334), analisis.cuotaQuincenal)
        // 500,000 centavos / 3 = 166,666.66 -> redondeo hacia arriba = 166,667 centavos ($1,666.67)
        assertEquals(Dinero(166_667), analisis.cuotaMensual)
    }

    @Test
    fun fecha_vencida_no_divide_por_cero_y_se_marca_fecha_alcanzada() {
        val hoy = LocalDate(2026, 11, 22)
        val vencida = MetaAhorro(
            id = MetaAhorroId(1),
            nombre = "Predial",
            objetivo = Dinero.pesos(3_000),
            apartado = Dinero.pesos(1_200),
            fechaLimite = LocalDate(2026, 11, 20),
        )

        val analisis = AnalizadorMetas.analizarUna(vencida, hoy)

        assertEquals(EstadoMetaAhorro.FECHA_ALCANZADA, analisis.estado)
        assertEquals(-2, analisis.diasRestantes)
        assertEquals(Dinero.pesos(1_800), analisis.faltante)
        assertNull(analisis.cuotaQuincenal)
        assertNull(analisis.cuotaMensual)
    }

    @Test
    fun meta_completada_con_sobre_aportacion_acota_barra_y_conserva_excedente() {
        val meta = MetaAhorro(
            id = MetaAhorroId(2),
            nombre = "Fondo",
            objetivo = Dinero.pesos(10_000),
            apartado = Dinero.pesos(11_500),
            fechaLimite = null,
        )

        val analisis = AnalizadorMetas.analizarUna(meta, LocalDate(2026, 11, 22))

        assertEquals(EstadoMetaAhorro.COMPLETADA, analisis.estado)
        assertEquals(Dinero.CERO, analisis.faltante)
        assertEquals(Dinero.pesos(1_500), analisis.excedente)
        assertEquals(1f, analisis.fraccionAvance)
        assertEquals(115, analisis.porcentajeEntero)
    }

    @Test
    fun detecta_sobre_reserva_solo_sobre_metas_activas_con_reserva_activa() {
        val hoy = LocalDate(2026, 11, 22)
        val metas = listOf(
            MetaAhorro(MetaAhorroId(1), "Emergencia", Dinero.pesos(10_000), Dinero.pesos(3_000), reservarDeDisponible = true),
            MetaAhorro(MetaAhorroId(2), "Viaje", Dinero.pesos(5_000), Dinero.pesos(2_000), reservarDeDisponible = true),
            MetaAhorro(MetaAhorroId(3), "Informativa", Dinero.pesos(4_000), Dinero.pesos(1_500), reservarDeDisponible = false),
            MetaAhorro(MetaAhorroId(4), "Archivada", Dinero.pesos(4_000), Dinero.pesos(4_000), reservarDeDisponible = true, archivada = true),
        )
        val saldos = mapOf(efectivo.id to Dinero.pesos(1_000), debito.id to Dinero.pesos(2_500)) // total $3,500

        val resumen = AnalizadorMetas.analizar(metas, cuentas, saldos, hoy)

        assertEquals(3, resumen.cantidadActivas)
        assertEquals(1, resumen.metasArchivadas.size)
        assertEquals(Dinero.pesos(6_500), resumen.totalApartado)
        assertEquals(Dinero.pesos(5_000), resumen.totalReservadoDeDisponible)
        // $5,000 reservados - $3,500 en cuentas = $1,500 de sobre-reserva
        assertEquals(Dinero.pesos(1_500), resumen.sobreReserva)
    }

    @Test
    fun saldo_negativo_en_cuentas_trata_liquidez_positiva_como_cero_para_sobre_reserva() {
        val metas = listOf(
            MetaAhorro(MetaAhorroId(1), "Emergencia", Dinero.pesos(5_000), Dinero.pesos(800), reservarDeDisponible = true),
        )
        val saldos = mapOf(efectivo.id to -Dinero.pesos(200))

        val resumen = AnalizadorMetas.analizar(metas, cuentas, saldos, LocalDate(2026, 11, 22))

        assertEquals(Dinero.pesos(800), resumen.sobreReserva)
    }

    @Test
    fun casi_completa_nunca_redondea_porcentaje_a_100_si_aun_falta_dinero() {
        val meta = MetaAhorro(
            id = MetaAhorroId(1),
            nombre = "Laptop",
            objetivo = Dinero.pesos(10_000),
            apartado = Dinero.pesos(9_995),
        )
        val analisis = AnalizadorMetas.analizarUna(meta, LocalDate(2026, 11, 22))
        assertEquals(EstadoMetaAhorro.EN_CURSO, analisis.estado)
        assertEquals(Dinero.pesos(5), analisis.faltante)
        assertEquals(99, analisis.porcentajeEntero)
    }

    @Test
    fun desglose_disponible_con_metas_reservadas_cubre_todos_los_escenarios() {
        val metas = listOf(
            MetaAhorro(MetaAhorroId(1), "Emergencia", Dinero.pesos(10_000), Dinero.pesos(1_000), reservarDeDisponible = true),
            MetaAhorro(MetaAhorroId(2), "Seguro", Dinero.pesos(3_000), Dinero.pesos(500), reservarDeDisponible = true),
            MetaAhorro(MetaAhorroId(3), "Sin reserva", Dinero.pesos(5_000), Dinero.pesos(2_000), reservarDeDisponible = false),
            MetaAhorro(MetaAhorroId(4), "Archivada", Dinero.pesos(5_000), Dinero.pesos(1_000), reservarDeDisponible = true, archivada = true),
            MetaAhorro(MetaAhorroId(5), "En cero", Dinero.pesos(5_000), Dinero.CERO, reservarDeDisponible = true),
        )

        // Caso 1: disponible ($5,000) alcanza de sobra para $1,500 reservados -> libre $3,500
        val sobrado = AnalizadorMetas.calcularDesgloseDisponible(Dinero.pesos(5_000), metas)
        assertEquals(true, sobrado.hayApartados)
        assertEquals(listOf("Emergencia", "Seguro"), sobrado.metasReservadas.map { it.nombre })
        assertEquals(Dinero.pesos(1_500), sobrado.apartadoEnMetas)
        assertEquals(Dinero.pesos(3_500), sobrado.disponibleLibre)
        assertEquals(Dinero.CERO, sobrado.excesoApartados)

        // Caso 2: disponible exacto ($1,500) igual a apartados ($1,500) -> libre $0, exceso $0
        val exacto = AnalizadorMetas.calcularDesgloseDisponible(Dinero.pesos(1_500), metas)
        assertEquals(Dinero.CERO, exacto.disponibleLibre)
        assertEquals(Dinero.CERO, exacto.excesoApartados)

        // Caso 3: disponible ($1,000) menor que apartados ($1,500) -> libre $0, exceso $500
        val parcial = AnalizadorMetas.calcularDesgloseDisponible(Dinero.pesos(1_000), metas)
        assertEquals(Dinero.CERO, parcial.disponibleLibre)
        assertEquals(Dinero.pesos(500), parcial.excesoApartados)

        // Caso 4: disponible cero ($0) -> libre $0, exceso $1,500
        val enCero = AnalizadorMetas.calcularDesgloseDisponible(Dinero.CERO, metas)
        assertEquals(Dinero.CERO, enCero.disponibleLibre)
        assertEquals(Dinero.pesos(1_500), enCero.excesoApartados)

        // Caso 5: disponible negativo (-$400) -> libre $0, exceso acotado a los $1,500 (no suma deuda bancaria)
        val negativo = AnalizadorMetas.calcularDesgloseDisponible(-Dinero.pesos(400), metas)
        assertEquals(Dinero.CERO, negativo.disponibleLibre)
        assertEquals(Dinero.pesos(1_500), negativo.excesoApartados)

        // Caso 6: sin metas reservadas -> hayApartados = false, libre = disponible
        val sinMetas = AnalizadorMetas.calcularDesgloseDisponible(Dinero.pesos(2_000), emptyList())
        assertEquals(false, sinMetas.hayApartados)
        assertEquals(emptyList(), sinMetas.metasReservadas)
        assertEquals(Dinero.pesos(2_000), sinMetas.disponibleLibre)
        assertEquals(Dinero.CERO, sinMetas.excesoApartados)
    }
}
