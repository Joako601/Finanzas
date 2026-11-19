package io.github.joako601.finanzas.ui.componentes

import io.github.joako601.finanzas.core.domain.libro.CatalogoInicial
import org.junit.Assert.assertEquals
import org.junit.Test

class IconosCategoriaTest {

    /** El ícono se busca por nombre: un nombre que cambia en un lado y no en el otro cae en el ícono genérico. */
    @Test
    fun cada_categoria_del_catalogo_tiene_icono_y_no_sobran_iconos() {
        val catalogo = (CatalogoInicial.categoriasDeGasto + CatalogoInicial.categoriasDeIngreso).toSet()
        assertEquals(catalogo, iconosPorNombre.keys)
    }
}
