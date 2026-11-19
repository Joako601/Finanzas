package io.github.joako601.finanzas.core.data

import android.content.Context
import io.github.joako601.finanzas.core.data.libro.RepositorioLibroRoom
import io.github.joako601.finanzas.core.data.recordatorios.RepositorioRecordatoriosRoom
import io.github.joako601.finanzas.core.data.tarjetas.RepositorioTarjetasRoom
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.recordatorios.RepositorioRecordatorios
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas

/**
 * Única puerta pública de core:data. La base, los DAO y las entidades son `internal`:
 * los demás módulos solo ven los puertos del dominio y no pueden saltarse los
 * repositorios para escribir SQL por su cuenta. El compilador impone esa regla.
 */
public class Datos private constructor(baseDeDatos: FinanzasDatabase) {

    public val repositorioLibro: RepositorioLibro = RepositorioLibroRoom(baseDeDatos)

    /** Comparte la base con el libro: `repositorioLibro.enTransaccion` cubre a ambos. */
    public val repositorioTarjetas: RepositorioTarjetas = RepositorioTarjetasRoom(baseDeDatos.tarjetasDao())

    public val repositorioRecordatorios: RepositorioRecordatorios =
        RepositorioRecordatoriosRoom(baseDeDatos.recordatoriosDao())

    public companion object {
        /** Debe crearse una sola vez por proceso (lo hace el contenedor de la app). */
        public fun crear(context: Context): Datos = Datos(FinanzasDatabase.crear(context))
    }
}
