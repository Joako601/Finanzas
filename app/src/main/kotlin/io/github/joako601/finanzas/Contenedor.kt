package io.github.joako601.finanzas

import android.content.Context
import io.github.joako601.finanzas.core.data.Datos
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.ServicioLibro
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.ServicioTarjetas

/**
 * Contenedor de dependencias escrito a mano: el equivalente a un `@Configuration` de
 * Spring con sus `@Bean`, pero sin librería. Cada objeto se crea una sola vez.
 *
 * Por qué todavía no Koin: con pocos objetos, un contenedor manual se lee de un
 * vistazo, no agrega dependencias y un error de cableado falla al compilar (Koin lo
 * detecta en tiempo de ejecución). Se revisa cuando haya varios módulos de funciones (D-007).
 */
class Contenedor(context: Context) {
    private val datos: Datos = Datos.crear(context)

    val repositorioLibro: RepositorioLibro = datos.repositorioLibro
    val servicioLibro: ServicioLibro = ServicioLibro(repositorioLibro)

    val repositorioTarjetas: RepositorioTarjetas = datos.repositorioTarjetas
    val servicioTarjetas: ServicioTarjetas = ServicioTarjetas(repositorioLibro, repositorioTarjetas)
}
