package io.github.joako601.finanzas

import android.app.Application

/**
 * Punto de entrada del proceso. Android crea esta clase antes que cualquier pantalla
 * (se declara en el manifiesto con android:name) y vive mientras viva el proceso.
 */
class FinanzasApp : Application() {

    /**
     * Inyección de dependencias manual. `lazy` crea la base solo cuando la primera
     * pantalla la pide, no al arrancar el proceso.
     */
    val contenedor: Contenedor by lazy { Contenedor(this) }
}
