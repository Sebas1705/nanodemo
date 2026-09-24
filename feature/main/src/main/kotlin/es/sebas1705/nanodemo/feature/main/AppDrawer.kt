package es.sebas1705.nanodemo.feature.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TierAvatar
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.domain.models.Conversation
import es.sebas1705.nanodemo.domain.models.InferenceTier

/**
 * Contenido del menú lateral (como el de ChatGPT/Claude): reemplaza a la barra inferior, que le comía
 * altura a cada pantalla. Arriba la identidad y "Nueva conversación", después las 4 secciones, las
 * conversaciones recientes (para retomar una sin pasar por Historial) y, al pie, Ajustes.
 * Lo comparten el drawer modal (teléfono) y el permanente (tablet); cada acción la cierra el llamador.
 */
@Composable
internal fun DrawerContent(
    currentDestination: Destination?,
    activeTier: InferenceTier,
    recents: List<Conversation>,
    onNavigate: (Destination) -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TierAvatar(activeTier, 40.dp)
            Column {
                Text("AIMobiDemo", style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
                Text("Motor: ${activeTier.label()}", style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onNewConversation)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.EditNote, contentDescription = null, tint = activeTier.signalColor)
            Text(
                "Nueva conversación",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = DemoColors.Ink,
            )
        }

        Destination.entries.forEach { destination ->
            NavigationDrawerItem(
                label = { Text(destination.label) },
                icon = { Icon(destination.icon, contentDescription = null) },
                selected = destination == currentDestination,
                onClick = { onNavigate(destination) },
                colors = drawerItemColors(),
                shape = RoundedCornerShape(50),
            )
        }

        if (recents.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DemoColors.Outline)
            SectionLabel("Recientes", Modifier.padding(start = 16.dp, bottom = 4.dp))
        }
        // Recientes ocupa el espacio que sobre (y scrollea si no entra): así Ajustes queda siempre al pie.
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(recents, key = { it.id }) { conversation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .clickable { onOpenConversation(conversation.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TierAvatar(conversation.tier, 24.dp)
                    Text(
                        conversation.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DemoColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DemoColors.Outline)
        NavigationDrawerItem(
            label = { Text("Ajustes") },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            selected = false,
            onClick = onOpenSettings,
            colors = drawerItemColors(),
            shape = RoundedCornerShape(50),
        )
    }
}

@Composable
private fun drawerItemColors() = NavigationDrawerItemDefaults.colors(
    selectedContainerColor = DemoColors.TierA.copy(alpha = 0.16f),
    unselectedContainerColor = Color.Transparent,
    selectedIconColor = DemoColors.TierA,
    unselectedIconColor = DemoColors.Muted,
    selectedTextColor = DemoColors.TierA,
    unselectedTextColor = DemoColors.Ink,
)
