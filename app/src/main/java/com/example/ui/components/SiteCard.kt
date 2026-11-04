package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.SiteEntity
import com.example.ui.localization.LocalAppStrings
import com.example.util.UrlSecurityValidator

@Composable
fun SiteCard(
    site: SiteEntity,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onAddToHomeScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val accent = Color(site.accentColorHex)
    val starColor by animateColorAsState(
        targetValue = if (site.isFavorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "starColor"
    )
    val hostText = UrlSecurityValidator.extractNormalizedHost(site.url) ?: site.url

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                ),
                shape = MaterialTheme.shapes.large
            )
            .clickable(onClick = onOpen)
            .testTag("site_card_${site.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SiteIconBadge(
                        name = site.name,
                        iconUrl = site.iconUrl,
                        iconPreset = site.iconPreset,
                        accentColorHex = site.accentColorHex,
                        size = 52.dp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = site.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = hostText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("fav_btn_${site.id}")
                    ) {
                        Icon(
                            imageVector = if (site.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = strings.navFavorites,
                            tint = starColor
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_btn_${site.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = strings.edit,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val displayDescription = strings.resolveSiteDescription(
                    siteId = site.id,
                    storedDescription = site.description,
                    seedEnglishDescriptions = com.example.ui.localization.DefaultSeedEnglishDescriptions
                )
                if (displayDescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = displayDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onAddToHomeScreen,
                        modifier = Modifier.testTag("add_to_home_" + site.id)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddToHomeScreen,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = strings.addToHomeScreen,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.openSite,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = strings.openSite,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteQuickChip(
    site: SiteEntity,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(site.accentColorHex)
    Surface(
        modifier = modifier
            .width(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, accent.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .testTag("fav_quick_chip_${site.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SiteIconBadge(
                name = site.name,
                iconUrl = site.iconUrl,
                iconPreset = site.iconPreset,
                accentColorHex = site.accentColorHex,
                size = 44.dp
            )
            Text(
                text = site.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
