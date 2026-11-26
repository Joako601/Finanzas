package io.github.joako601.finanzas.ui.perfil

/**
 * Lo que la distribución nube le da al Perfil. En la distribución privada no hay cuentas y
 * el Perfil recibe `null` (D-020): `app/main` no conoce `:feature:nube`.
 *
 * @param correo correo de la sesión; `null` si no hay sesión iniciada.
 * @param nombre nombre visible de la cuenta (Google lo trae); `null` si no se conoce.
 * @param onEntrar abre el login de la sección nube.
 * @param onCerrarSesion pide confirmación y cierra la sesión (el diálogo lo pone la nube).
 */
class CuentaPerfil(
    val correo: String?,
    val nombre: String?,
    val onEntrar: () -> Unit,
    val onAbrirCatalogo: () -> Unit,
    val onCerrarSesion: () -> Unit,
) {
    val conSesion: Boolean get() = correo != null

    /** Nombre a mostrar: el de la cuenta o, si no hay, lo que va antes de la @ del correo. */
    val nombreVisible: String
        get() = nombre?.trim()?.takeIf { it.isNotEmpty() } ?: correo.orEmpty().substringBefore('@')

    /** Inicial para el avatar, en mayúscula. */
    val inicial: String
        get() = nombreVisible.firstOrNull()?.uppercase().orEmpty()
}
