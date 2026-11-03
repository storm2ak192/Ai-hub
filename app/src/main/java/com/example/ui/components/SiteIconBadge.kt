package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

val PresetIconsMap: Map<String, ImageVector> = mapOf(
    "chatgpt" to Icons.Filled.AutoAwesome,
    "gemini" to Icons.Filled.Bolt,
    "grok" to Icons.Filled.Terminal,
    "qwen" to Icons.Filled.Psychology,
    "deepseek" to Icons.Filled.Memory,
    "claude" to Icons.Filled.SmartToy,
    "perplexity" to Icons.Filled.TravelExplore,
    "globe" to Icons.Filled.Language,
    "code" to Icons.Filled.Code,
    "explore" to Icons.Filled.Explore,
    "school" to Icons.Filled.School,
    "hub" to Icons.Filled.Hub
)

@Composable
fun SiteIconBadge(
    name: String,
    iconUrl: String?,
    iconPreset: String,
    accentColorHex: Long,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = Color(accentColorHex)
    val shape = RoundedCornerShape(size * 0.3f)
    var imageLoaded by remember(iconUrl) { mutableStateOf(false) }
    var imageError by remember(iconUrl) { mutableStateOf(false) }

    val imageRequest = remember(iconUrl, context) {
        iconUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(128)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.28f),
                        accent.copy(alpha = 0.10f)
                    )
                )
            )
            .border(1.dp, accent.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center
    ) {
        val presetVector = PresetIconsMap[iconPreset]

        if (imageRequest != null && !imageError) {
            AsyncImage(
                model = imageRequest,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size * 0.64f)
                    .clip(RoundedCornerShape(size * 0.16f)),
                onSuccess = { imageLoaded = true },
                onError = { imageError = true }
            )
        }

        if (imageRequest == null || imageError || !imageLoaded) {
            if (presetVector != null) {
                Icon(
                    imageVector = presetVector,
                    contentDescription = name,
                    tint = accent,
                    modifier = Modifier.size(size * 0.54f)
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }
    }
}
