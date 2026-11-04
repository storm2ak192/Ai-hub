package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.SiteEntity
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.SiteColorPresets
import com.example.util.FaviconResolver
import com.example.util.UrlSecurityValidator
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditSiteDialog(
    existingSite: SiteEntity?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        url: String,
        description: String,
        iconUrl: String?,
        iconPreset: String,
        accentColorHex: Long,
        javaScriptEnabled: Boolean,
        allowCamera: Boolean,
        allowMicrophone: Boolean,
        allowPopups: Boolean,
        allowDownloads: Boolean,
        desktopMode: Boolean,
        isEnabled: Boolean
    ) -> Unit,
    onDelete: ((SiteEntity) -> Unit)? = null
) {
    val strings = LocalAppStrings.current

    var name by remember(existingSite) { mutableStateOf(existingSite?.name ?: "") }
    var url by remember(existingSite) { mutableStateOf(existingSite?.url ?: "https://") }
    var description by remember(existingSite) { mutableStateOf(existingSite?.description ?: "") }
    var isDescriptionManuallyEdited by remember(existingSite) { mutableStateOf(existingSite != null) }
    var customIconUrl by remember(existingSite) {
        val stored = existingSite?.iconUrl ?: ""
        mutableStateOf(if (stored.contains("google.com/s2/favicons")) "" else stored)
    }
    var autoDiscoveredIconUrl by remember(existingSite) {
        mutableStateOf(
            existingSite?.iconUrl ?: existingSite?.url?.let { FaviconResolver.buildInstantFaviconUrl(it) }
        )
    }
    var isFetchingOriginalIcon by remember { mutableStateOf(false) }

    var iconPreset by remember(existingSite) { mutableStateOf(existingSite?.iconPreset ?: "globe") }
    var accentColorHex by remember(existingSite) {
        mutableLongStateOf(existingSite?.accentColorHex ?: SiteColorPresets[2])
    }

    var javaScriptEnabled by remember(existingSite) { mutableStateOf(existingSite?.javaScriptEnabled ?: true) }
    var allowCamera by remember(existingSite) { mutableStateOf(existingSite?.allowCamera ?: true) }
    var allowMicrophone by remember(existingSite) { mutableStateOf(existingSite?.allowMicrophone ?: true) }
    var allowPopups by remember(existingSite) { mutableStateOf(existingSite?.allowPopups ?: true) }
    var allowDownloads by remember(existingSite) { mutableStateOf(existingSite?.allowDownloads ?: true) }
    var desktopMode by remember(existingSite) { mutableStateOf(existingSite?.desktopMode ?: false) }
    var isEnabled by remember(existingSite) { mutableStateOf(existingSite?.isEnabled ?: true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Automatically fetch the website's original icon whenever the user types a valid URL
    LaunchedEffect(url) {
        val validUrl = UrlSecurityValidator.normalizeAndValidateInputUrl(url)
        if (validUrl == null) {
            autoDiscoveredIconUrl = null
            isFetchingOriginalIcon = false
            return@LaunchedEffect
        }
        // Set instant domain favicon right away
        autoDiscoveredIconUrl = FaviconResolver.buildInstantFaviconUrl(validUrl)
        // Debounce before inspecting HTML <head> for high-res apple-touch-icon / original icon
        delay(450)
        isFetchingOriginalIcon = true
        val discovered = FaviconResolver.discoverOriginalSiteIconUrl(validUrl)
        if (!discovered.isNullOrBlank()) {
            autoDiscoveredIconUrl = discovered
        }
        if (existingSite == null && !isDescriptionManuallyEdited) {
            val discoveredDescription = FaviconResolver.discoverSiteDescription(validUrl)
            if (!discoveredDescription.isNullOrBlank() && !isDescriptionManuallyEdited) {
                description = discoveredDescription
            }
        }
        isFetchingOriginalIcon = false
    }

    val effectivePreviewIconUrl = customIconUrl.trim().takeIf { it.isNotEmpty() } ?: autoDiscoveredIconUrl

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingSite == null) strings.addSiteTitle else strings.editSiteTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live Card & Original Website Icon Preview
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SiteIconBadge(
                            name = name.ifBlank { "?" },
                            iconUrl = effectivePreviewIconUrl,
                            iconPreset = iconPreset,
                            accentColorHex = accentColorHex,
                            size = 54.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name.ifBlank { strings.fieldSiteName },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = strings.autoFetchedIconLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isFetchingOriginalIcon) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text(strings.fieldSiteName) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("site_name_input")
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        if (existingSite == null) isDescriptionManuallyEdited = false
                        errorMessage = null
                    },
                    label = { Text(strings.fieldSiteUrl) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("site_url_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        isDescriptionManuallyEdited = true
                    },
                    label = { Text(strings.fieldSiteDesc) },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("site_desc_input")
                )

                OutlinedTextField(
                    value = customIconUrl,
                    onValueChange = { customIconUrl = it },
                    label = { Text(strings.fieldIconUrl) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("site_icon_url_input")
                )

                Text(
                    text = strings.fieldChooseIcon,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetIconsMap.forEach { (key, vector) ->
                        val isSelected = iconPreset == key
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { iconPreset = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = key,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = strings.fieldChooseColor,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SiteColorPresets.forEach { colorHex ->
                        val isSelected = accentColorHex == colorHex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { accentColorHex = colorHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = strings.fieldChooseColor,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                PermissionToggleRow(
                    label = strings.siteEnabled,
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it },
                    tag = "toggle_site_enabled"
                )
                PermissionToggleRow(
                    label = strings.permJavaScript,
                    checked = javaScriptEnabled,
                    onCheckedChange = { javaScriptEnabled = it },
                    tag = "toggle_site_js"
                )
                PermissionToggleRow(
                    label = strings.permCamera,
                    checked = allowCamera,
                    onCheckedChange = { allowCamera = it },
                    tag = "toggle_site_camera"
                )
                PermissionToggleRow(
                    label = strings.permMicrophone,
                    checked = allowMicrophone,
                    onCheckedChange = { allowMicrophone = it },
                    tag = "toggle_site_mic"
                )
                PermissionToggleRow(
                    label = strings.permPopups,
                    checked = allowPopups,
                    onCheckedChange = { allowPopups = it },
                    tag = "toggle_site_popups"
                )
                PermissionToggleRow(
                    label = strings.permDownloads,
                    checked = allowDownloads,
                    onCheckedChange = { allowDownloads = it },
                    tag = "toggle_site_downloads"
                )
                PermissionToggleRow(
                    label = strings.permDesktopMode,
                    checked = desktopMode,
                    onCheckedChange = { desktopMode = it },
                    tag = "toggle_site_desktop_mode"
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (existingSite != null && onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            onDelete(existingSite)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_site_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = strings.delete
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.delete)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || url.isBlank()) {
                        errorMessage = strings.requiredFieldError
                        return@Button
                    }
                    val validUrl = UrlSecurityValidator.normalizeAndValidateInputUrl(url)
                    if (validUrl == null) {
                        errorMessage = strings.invalidUrlError
                        return@Button
                    }
                    val finalIconToSave = customIconUrl.trim().takeIf { it.isNotEmpty() }
                        ?: autoDiscoveredIconUrl
                        ?: FaviconResolver.buildInstantFaviconUrl(validUrl)

                    onSave(
                        name.trim(),
                        validUrl,
                        description.trim(),
                        finalIconToSave,
                        iconPreset,
                        accentColorHex,
                        javaScriptEnabled,
                        allowCamera,
                        allowMicrophone,
                        allowPopups,
                        allowDownloads,
                        desktopMode,
                        isEnabled
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("save_site_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_site_button")
            ) {
                Text(strings.browserCancel)
            }
        }
    )
}

@Composable
private fun PermissionToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
