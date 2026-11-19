package io.github.joako601.finanzas

import android.content.Context
import io.github.joako601.finanzas.core.data.Datos
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.ServicioLibro
import io.github.joako601.finanzas.core.domain.recordatorios.RepositorioRecordatorios
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.ServicioTarjetas
import io.github.joako601.finanzas.recordatorios.Avisos
import io.github.joako601.finanzas.recordatorios.Programador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

    /** Corrutinas que viven lo que vive el proceso (no una pantalla). */
    val alcance: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repositorioLibro: RepositorioLibro = datos.repositorioLibro
    val servicioLibro: ServicioLibro = ServicioLibro(repositorioLibro)

    val repositorioTarjetas: RepositorioTarjetas = datos.repositorioTarjetas
    val servicioTarjetas: ServicioTarjetas = ServicioTarjetas(repositorioLibro, repositorioTarjetas)

    val repositorioRecordatorios: RepositorioRecordatorios = datos.repositorioRecordatorios
    val avisos: Avisos = Avisos(context.applicationContext)
    val programador: Programador = Programador(
        context.applicationContext,
        repositorioLibro,
        repositorioTarjetas,
        repositorioRecordatorios,
        avisos,
    )

    init {
        avisos.crearCanal()
        // Cada vez que cambian tarjetas, estados de cuenta o movimientos, se reprograma.
        // collectLatest + espera de 1 s: una ráfaga de cambios produce una sola ejecución.
        alcance.launch {
            combine(
                repositorioTarjetas.observarTarjetas(),
                repositorioTarjetas.observarEstadosDeCuenta(),
                repositorioLibro.observarMovimientos(),
            ) { _, _, _ -> }.collectLatest {
                delay(1_000)
                programador.ejecutar()
            }
        }
    }
}
