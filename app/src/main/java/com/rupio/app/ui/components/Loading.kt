package com.rupio.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rupio.app.ui.theme.RupioColors

/**
 * Indicador de carga de Rupio: color con buen contraste sobre el fondo donde va, más un "riel" tenue para que
 * siempre se vea la forma (antes, dentro del botón de iniciar sesión, salía azul marino sobre gris oscuro: invisible).
 */
@Composable
fun RupioSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = RupioColors.Primary,
    strokeWidth: Dp = if (size <= 24.dp) 2.5.dp else 3.dp,
    label: String = "Cargando",
) {
    CircularProgressIndicator(
        modifier.size(size).semantics { contentDescription = label },
        color = color, strokeWidth = strokeWidth, trackColor = color.copy(alpha = 0.22f), strokeCap = StrokeCap.Round,
    )
}

/** Carga a pantalla (o sección) completa, centrada y con texto opcional. */
@Composable
fun LoadingBlock(text: String? = null, modifier: Modifier = Modifier, padding: Dp = 40.dp) {
    Column(modifier.fillMaxWidth().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
        RupioSpinner(size = 32.dp)
        if (text != null) {
            Spacer(Modifier.height(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
        }
    }
}

/**
 * Botón que muestra que está trabajando: se queda del color de acento (no se apaga a gris), con el indicador en
 * el color del texto del botón y una palabra que dice qué pasa ("Entrando…"). No acepta un segundo toque.
 */
@Composable
fun LoadingButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loadingText: String = text,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val defaults = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    Button(
        onClick = onClick, enabled = enabled && !loading, modifier = modifier, shape = RoundedCornerShape(16.dp),
        colors = if (loading) ButtonDefaults.buttonColors(containerColor = container, contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.85f), disabledContentColor = content) else defaults,
    ) {
        if (loading) {
            RupioSpinner(size = 20.dp, color = content, strokeWidth = 2.5.dp, label = loadingText)
            Spacer(Modifier.width(10.dp))
            Text(loadingText, style = MaterialTheme.typography.titleMedium)
        } else Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
