package io.github.joako601.finanzas.ui.componentes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.joako601.finanzas.ui.tema.TemaFinanzas

/**
 * Tarjeta blanca (gris carbón en modo oscuro) con esquinas redondeadas y una sombra
 * ligera: el bloque básico de la interfaz.
 */
@Composable
fun TarjetaFinanzas(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = contenido,
    )
}

/** Botón de la acción principal: amarillo con texto negro, de ancho completo. */
@Composable
fun BotonPrincipal(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Ícono dentro de un círculo de color suave, como encabezado de una fila. */
@Composable
fun IconoEnCirculo(@DrawableRes icono: Int, tamano: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(tamano)
            .clip(CircleShape)
            .background(TemaFinanzas.colores.fondoIcono),
        contentAlignment = Alignment.Center,
    ) {
        // Decorativo: el nombre de la categoría ya está escrito al lado.
        Icon(painterResource(icono), contentDescription = null, tint = TemaFinanzas.colores.icono, modifier = Modifier.size(tamano * 0.55f))
    }
}
