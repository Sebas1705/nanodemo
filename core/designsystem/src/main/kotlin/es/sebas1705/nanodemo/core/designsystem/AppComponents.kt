package es.sebas1705.nanodemo.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier

/**
 * Piezas del lenguaje visual de "chat de IA" que comparten TODAS las pantallas (nacieron en el Chat):
 * superficies redondeadas con borde sutil, avatares de canal, píldoras, barra de entrada. Así Comparar,
 * Historial, Estadísticas y Ajustes se ven de la misma familia que la conversación.
 */

/** Círculo con la letra del canal: la "identidad" del modelo, como el logo en otros chats de IA. */
@Composable
fun TierAvatar(tier: InferenceTier, size: Dp, modifier: Modifier = Modifier) {
    val color = tier.signalColor
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tier.channelLetter(),
            style = readoutStyle,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/**
 * Tarjeta estándar: esquinas de 20dp, fondo `Panel` y borde blanco al 10%. Con `accent` (p.ej. el
 * color del tier activo) se tiñe el fondo y el borde — para marcar "seleccionado", no como decoración.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(accent?.copy(alpha = 0.10f)?.compositeOver(DemoColors.Panel) ?: DemoColors.Panel)
            .border(1.dp, accent?.copy(alpha = 0.45f) ?: DemoColors.Outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Título de sección: en minúscula normal y apagado (antes eran etiquetas en mayúsculas con tracking). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = DemoColors.Muted, modifier = modifier)
}

/** Pantalla vacía/informativa centrada: ícono en círculo, título y mensaje (+ acción opcional). */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(DemoColors.Panel).border(1.dp, DemoColors.Outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = DemoColors.Muted, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.size(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted, textAlign = TextAlign.Center)
        action?.let {
            Spacer(Modifier.size(4.dp))
            it()
        }
    }
}

@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DemoColors.Danger.copy(alpha = 0.12f))
            .border(1.dp, DemoColors.Danger.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = DemoColors.Danger)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
    }
}

/** Selector de tier en píldora (avatar + nombre), para elegir uno entre varios en fila. */
@Composable
fun TierChip(tier: InferenceTier, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = tier.signalColor
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.16f).compositeOver(DemoColors.Panel) else DemoColors.Panel)
            .border(1.dp, if (selected) color.copy(alpha = 0.6f) else DemoColors.Outline, shape)
            .clickable(onClick = onClick)
            .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TierAvatar(tier, 24.dp)
        Text(
            tier.label(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) DemoColors.Ink else DemoColors.Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * Barra de entrada en píldora (la del Chat, ahora compartida con Comparar): campo que crece hasta 6
 * líneas y botón redondo de enviar. `topContent` y `leading` son ranuras para lo que cada pantalla
 * agrega (chips de adjuntos arriba; botón "+" a la izquierda del botón de enviar).
 */
@Composable
fun PromptBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    accent: Color,
    isBusy: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
    topContent: (@Composable ColumnScope.() -> Unit)? = null,
    leading: @Composable RowScope.() -> Unit = {},
) {
    val canSend = value.isNotBlank() && !isBusy
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(DemoColors.Panel)
            .border(1.dp, DemoColors.Outline, RoundedCornerShape(28.dp))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        topContent?.invoke(this)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = DemoColors.Ink),
            cursorBrush = SolidColor(accent),
            maxLines = 6,
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = DemoColors.Muted)
                    }
                    inner()
                }
            },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isBusy -> accent.copy(alpha = 0.25f)
                            canSend -> accent
                            else -> Color.White.copy(alpha = 0.10f)
                        },
                    )
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = "Enviar",
                        tint = if (canSend) DemoColors.Background else DemoColors.Muted,
                    )
                }
            }
        }
    }
}

@Composable
fun TypingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, delayMillis = index * 160), RepeatMode.Reverse),
                label = "dot$index",
            )
            Box(Modifier.size(8.dp).alpha(alpha).background(color, CircleShape))
        }
    }
}
