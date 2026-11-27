package io.github.joako601.finanzas.nube.catalogo

import io.github.joako601.finanzas.nube.red.ClienteHttpBackend
import io.github.joako601.finanzas.nube.red.JsonFormatoException
import io.github.joako601.finanzas.nube.red.JsonLector
import io.github.joako601.finanzas.nube.red.ObjetoJson
import io.github.joako601.finanzas.nube.red.RespuestaHttp

/**
 * Implementación remota de [RepositorioCatalogo] que consume `GET /api/v1/productos`
 * del backend Spring Boot autenticado con Firebase ID Token.
 */
internal class RepositorioCatalogoRemoto(
    private val clienteHttp: ClienteHttpBackend,
) : RepositorioCatalogo {

    override suspend fun obtenerProductos(): ResultadoCatalogo = when (val respuesta = clienteHttp.get(RUTA_PRODUCTOS)) {
        is RespuestaHttp.Exito -> try {
            val arreglo = JsonLector.analizarArreglo(respuesta.cuerpo)
            val productos = arreglo.elementos.map { elemento ->
                val obj = elemento as? ObjetoJson
                    ?: throw JsonFormatoException("Elemento de producto no es un objeto JSON")
                ProductoFinanciero(
                    id = obj.enteroLargo("id"),
                    nombre = obj.textoObligatorio("nombre"),
                    categoria = obj.textoObligatorio("categoria"),
                    entidad = obj.textoObligatorio("entidad"),
                    costoReferenciaPesos = obj.enteroLargo("costoReferenciaPesos"),
                    periodicidad = obj.textoObligatorio("periodicidad"),
                    detalleReferencia = obj.textoObligatorio("detalleReferencia"),
                    fuenteReferencia = obj.textoObligatorio("fuenteReferencia"),
                    imageUrl = obj.textoOpcional("imageUrl"),
                    tasaAnualReferencia = obj.decimalOpcional("tasaAnualReferencia")?.toDouble(),
                    verificado = obj.booleano("verificado"),
                )
            }
            ResultadoCatalogo.Exito(productos)
        } catch (_: JsonFormatoException) {
            ResultadoCatalogo.ErrorServidor
        }

        RespuestaHttp.SinConexion -> ResultadoCatalogo.SinConexion
        RespuestaHttp.NoAutorizado -> ResultadoCatalogo.SesionExpirada
        is RespuestaHttp.ErrorServidor -> ResultadoCatalogo.ErrorServidor
    }

    private companion object {
        const val RUTA_PRODUCTOS = "/api/v1/productos"
    }
}
