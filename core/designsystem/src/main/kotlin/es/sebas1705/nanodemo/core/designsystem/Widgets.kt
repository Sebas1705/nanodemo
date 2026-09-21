package es.sebas1705.nanodemo.core.designsystem

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.TierAvailability

/**
 * Piezas de UI compartidas entre las pantallas — selector de tier, burbujas
 * de chat, adjuntar imagen.
 */

@Composable
fun TierChannelRow(
    tier: InferenceTier,
    isActive: Boolean,
    availability: TierAvailability,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) tier.signalColor.copy(alpha = 0.14f) else DemoColors.Panel)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(tier.signalColor)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "[${tier.channelLetter()}]",
                style = readoutStyle,
                color = tier.signalColor,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = tier.label(),
                style = MaterialTheme.typography.bodyLarge,
                color = DemoColors.Ink,
                modifier = Modifier.weight(1f),
            )
            StatusIndicator(availability, tier.signalColor)
        }
    }
}

@Composable
fun StatusIndicator(availability: TierAvailability, color: Color) {
    when (availability) {
        TierAvailability.CHECKING ->
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = color)
        TierAvailability.AVAILABLE -> Text("●", style = readoutStyle, color = color)
        TierAvailability.DOWNLOADABLE -> Text("◐", style = readoutStyle, color = color)
        TierAvailability.UNAVAILABLE -> Text("○", style = readoutStyle, color = DemoColors.Muted)
        TierAvailability.UNKNOWN -> Text("·", style = readoutStyle, color = DemoColors.Muted)
    }
}

@Composable
fun MessageBubble(message: ChatMessage, tierColor: Color) {
    val isUser = message.role == Role.USER
    val context = LocalContext.current
    val imageBitmap = remember(message.imageUri) {
        message.imageUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isUser) DemoColors.Background else tierColor.copy(alpha = 0.08f),
                RoundedCornerShape(6.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (isUser) "TÚ" else "MODELO",
            style = readoutStyle,
            color = if (isUser) DemoColors.Muted else tierColor,
        )
        imageBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Imagen adjunta",
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
        Text(message.text, color = DemoColors.Ink)
    }
}

@Composable
fun ImageAttachRow(
    pendingImageUri: Uri?,
    onLaunchPicker: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val imageBitmap = remember(pendingImageUri) {
        pendingImageUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Imagen a adjuntar",
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
            )
            TextButton(onClick = onClear) { Text("Quitar imagen") }
        } else {
            TextButton(onClick = onLaunchPicker) { Text("+ Adjuntar imagen") }
        }
    }
}
