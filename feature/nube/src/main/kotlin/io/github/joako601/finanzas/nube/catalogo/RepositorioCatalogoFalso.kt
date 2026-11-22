package io.github.joako601.finanzas.nube.catalogo

/**
 * Implementación local con 12 productos financieros públicos de referencia en México
 * mientras se conecta el backend Spring Boot.
 *
 * Los costos y condiciones son únicamente informativos de referencia pública y no reflejan
 * cuentas ni movimientos personales del usuario.
 */
class RepositorioCatalogoFalso : RepositorioCatalogo {

    override suspend fun obtenerProductos(): ResultadoCatalogo = ResultadoCatalogo.Exito(productosReferencia)

    companion object {
        private val productosReferencia: List<ProductoFinanciero> = listOf(
            ProductoFinanciero(
                id = 1L,
                nombre = "BBVA Azul",
                categoria = "Tarjeta de crédito",
                entidad = "BBVA",
                costoReferenciaPesos = 748L,
                periodicidad = "Anual",
                detalleReferencia = "CAT promedio 72.9% · Corte típico día 15",
                fuenteReferencia = "Referencia pública CONDUSEF / BBVA",
            ),
            ProductoFinanciero(
                id = 2L,
                nombre = "Nu Crédito",
                categoria = "Tarjeta de crédito",
                entidad = "Nu México",
                costoReferenciaPesos = 0L,
                periodicidad = "Anual",
                detalleReferencia = "Sin anualidad · Control de límite y fechas desde la app",
                fuenteReferencia = "Referencia pública CONDUSEF / Nu México",
            ),
            ProductoFinanciero(
                id = 3L,
                nombre = "Citibanamex Clásica",
                categoria = "Tarjeta de crédito",
                entidad = "Banamex",
                costoReferenciaPesos = 815L,
                periodicidad = "Anual",
                detalleReferencia = "CAT promedio 76.4% · Meses sin intereses y preventas",
                fuenteReferencia = "Referencia pública CONDUSEF / Banamex",
            ),
            ProductoFinanciero(
                id = 4L,
                nombre = "Santander LikeU",
                categoria = "Tarjeta de crédito",
                entidad = "Santander",
                costoReferenciaPesos = 0L,
                periodicidad = "Anual",
                detalleReferencia = "Sin anualidad con gasto mensual mínimo · Cashback en comercios",
                fuenteReferencia = "Referencia pública CONDUSEF / Santander",
            ),
            ProductoFinanciero(
                id = 5L,
                nombre = "Cuenta Nu Débito",
                categoria = "Tarjeta de débito",
                entidad = "Nu México",
                costoReferenciaPesos = 0L,
                periodicidad = "Sin costo",
                detalleReferencia = "Sin comisión por manejo de cuenta · Cajitas con rendimiento",
                fuenteReferencia = "Referencia pública Nu México",
            ),
            ProductoFinanciero(
                id = 6L,
                nombre = "BBVA Libretón Básico Digital",
                categoria = "Tarjeta de débito",
                entidad = "BBVA",
                costoReferenciaPesos = 0L,
                periodicidad = "Sin costo",
                detalleReferencia = "Sin saldo mínimo · Retiros sin tarjeta en cajeros",
                fuenteReferencia = "Referencia pública CONDUSEF / BBVA",
            ),
            ProductoFinanciero(
                id = 7L,
                nombre = "Mercado Pago Débito",
                categoria = "Tarjeta de débito",
                entidad = "Mercado Pago",
                costoReferenciaPesos = 0L,
                periodicidad = "Sin costo",
                detalleReferencia = "Sin comisión anual · Transferencias SPEI sin costo",
                fuenteReferencia = "Referencia pública Mercado Pago",
            ),
            ProductoFinanciero(
                id = 8L,
                nombre = "CFE Doméstico",
                categoria = "Servicio",
                entidad = "CFE",
                costoReferenciaPesos = 450L,
                periodicidad = "Bimestral",
                detalleReferencia = "Recibo bimestral estimado · Tarifa residencial básica",
                fuenteReferencia = "Referencia pública CFE",
            ),
            ProductoFinanciero(
                id = 9L,
                nombre = "Telmex Infinitum",
                categoria = "Servicio",
                entidad = "Telmex",
                costoReferenciaPesos = 389L,
                periodicidad = "Mensual",
                detalleReferencia = "Internet residencial fibra óptica · Pago mensual",
                fuenteReferencia = "Referencia pública Telmex",
            ),
            ProductoFinanciero(
                id = 10L,
                nombre = "Telcel Amigo Sin Límite",
                categoria = "Servicio",
                entidad = "Telcel",
                costoReferenciaPesos = 200L,
                periodicidad = "Mensual",
                detalleReferencia = "Telefonía móvil prepago · Vigencia típica de 30 días",
                fuenteReferencia = "Referencia pública Telcel",
            ),
            ProductoFinanciero(
                id = 11L,
                nombre = "Spotify Individual",
                categoria = "Suscripción",
                entidad = "Spotify",
                costoReferenciaPesos = 129L,
                periodicidad = "Mensual",
                detalleReferencia = "Música y podcasts sin anuncios · Cobro recurrente",
                fuenteReferencia = "Referencia pública Spotify México",
            ),
            ProductoFinanciero(
                id = 12L,
                nombre = "Netflix Estándar con anuncios",
                categoria = "Suscripción",
                entidad = "Netflix",
                costoReferenciaPesos = 119L,
                periodicidad = "Mensual",
                detalleReferencia = "Streaming Full HD en 2 dispositivos · Cobro recurrente",
                fuenteReferencia = "Referencia pública Netflix México",
            ),
        )
    }
}
