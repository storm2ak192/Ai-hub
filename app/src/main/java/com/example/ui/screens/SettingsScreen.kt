package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.browser.BrowserDownloadAndAuthHelper
import com.example.data.local.DownloadEntity
import com.example.data.local.SiteEntity
import com.example.ui.components.SiteIconBadge
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.SettingsSubScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    subScreen: SettingsSubScreen,
    onNavigateSubScreen: (SettingsSubScreen) -> Unit,
    allSites: List<SiteEntity>,
    downloads: List<DownloadEntity>,
    themeMode: ThemeMode,
    language: AppLanguage,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onAddSiteClick: () -> Unit,
    onEditSiteClick: (SiteEntity) -> Unit,
    onToggleSiteEnabled: (SiteEntity) -> Unit,
    onMoveSiteOrder: (String, Boolean) -> Unit,
    onReorderSites: (Int, Int) -> Unit,
    onDeleteSite: (SiteEntity) -> Unit,
    onClearSingleSiteData: (SiteEntity) -> Unit,
    onRestoreDefaults: () -> Unit,
    onExportBackupJson: suspend () -> String,
    onImportBackupJson: (String) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onClearDownloads: () -> Unit,
    onSimulateDeepLink: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (subScreen) {
        SettingsSubScreen.MAIN -> {
            SettingsMainContent(
                allSites = allSites,
                downloadsCount = downloads.size,
                themeMode = themeMode,
                language = language,
                onSetThemeMode = onSetThemeMode,
                onSetLanguage = onSetLanguage,
                onOpenManageSites = { onNavigateSubScreen(SettingsSubScreen.MANAGE_SITES) },
                onOpenDownloads = { onNavigateSubScreen(SettingsSubScreen.DOWNLOADS) },
                onClearSingleSiteData = onClearSingleSiteData,
                onRestoreDefaults = onRestoreDefaults,
                onExportBackupJson = onExportBackupJson,
                onImportBackupJson = onImportBackupJson,
                onSimulateDeepLink = onSimulateDeepLink,
                onShowMessage = onShowMessage,
                modifier = modifier
            )
        }
        SettingsSubScreen.MANAGE_SITES -> {
            BackHandler { onNavigateSubScreen(SettingsSubScreen.MAIN) }
            ManageSitesSubScreen(
                sites = allSites,
                onBack = { onNavigateSubScreen(SettingsSubScreen.MAIN) },
                onAddSite = onAddSiteClick,
                onEditSite = onEditSiteClick,
                onToggleEnabled = onToggleSiteEnabled,
                onMoveOrder = onMoveSiteOrder,
                onReorderSites = onReorderSites,
                onClearSiteSession = onClearSingleSiteData,
                onDeleteSite = onDeleteSite,
                modifier = modifier
            )
        }
        SettingsSubScreen.DOWNLOADS -> {
            BackHandler { onNavigateSubScreen(SettingsSubScreen.MAIN) }
            DownloadsSubScreen(
                downloads = downloads,
                onBack = { onNavigateSubScreen(SettingsSubScreen.MAIN) },
                onDeleteDownload = onDeleteDownload,
                onClearAll = onClearDownloads,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun SettingsMainContent(
    allSites: List<SiteEntity>,
    downloadsCount: Int,
    themeMode: ThemeMode,
    language: AppLanguage,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onOpenManageSites: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClearSingleSiteData: (SiteEntity) -> Unit,
    onRestoreDefaults: () -> Unit,
    onExportBackupJson: suspend () -> String,
    onImportBackupJson: (String) -> Unit,
    onSimulateDeepLink: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sitePendingDataWipe by remember { mutableStateOf<SiteEntity?>(null) }
    var showDeepLinkTester by remember { mutableStateOf(false) }
    var testDeepLinkInput by remember { mutableStateOf("https://chatgpt.com/?q=AI+Hub") }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = writeBackupToUri(context, uri, onExportBackupJson())
                onShowMessage(if (success) strings.backupExportSuccess else strings.backupError)
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = readBackupFromUri(context, uri)
                if (json != null) {
                    onImportBackupJson(json)
                } else {
                    onShowMessage(strings.backupError)
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_main_list"),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = strings.navSettings,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 1. Manage Websites Card
        item {
            SettingsActionCard(
                icon = Icons.Filled.Tune,
                title = "${strings.settingsManageSites} (${allSites.size})",
                description = strings.settingsManageSitesDesc,
                onClick = onOpenManageSites,
                tag = "settings_manage_sites_card"
            )
        }

        // 2. Downloads Manager Card
        item {
            SettingsActionCard(
                icon = Icons.Filled.Download,
                title = "${strings.settingsDownloads} ($downloadsCount)",
                description = strings.settingsDownloadsDesc,
                onClick = onOpenDownloads,
                tag = "settings_downloads_card"
            )
        }

        // 3. Appearance & Language Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = strings.settingsAppearanceTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = strings.themeModeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = { onSetThemeMode(ThemeMode.DARK) },
                            label = { Text(strings.themeDark) },
                            modifier = Modifier.testTag("chip_theme_dark")
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = { onSetThemeMode(ThemeMode.LIGHT) },
                            label = { Text(strings.themeLight) },
                            modifier = Modifier.testTag("chip_theme_light")
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = { onSetThemeMode(ThemeMode.SYSTEM) },
                            label = { Text(strings.themeSystem) },
                            modifier = Modifier.testTag("chip_theme_system")
                        )
                    }

                    HorizontalDivider()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = strings.languageLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = language == AppLanguage.ARABIC,
                            onClick = { onSetLanguage(AppLanguage.ARABIC) },
                            label = { Text(strings.langArabic) },
                            modifier = Modifier.testTag("chip_lang_ar")
                        )
                        FilterChip(
                            selected = language == AppLanguage.ENGLISH,
                            onClick = { onSetLanguage(AppLanguage.ENGLISH) },
                            label = { Text(strings.langEnglish) },
                            modifier = Modifier.testTag("chip_lang_en")
                        )
                    }
                }
            }
        }

        // 4. Per-Site Session & Data Wipe
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = strings.settingsClearSiteDataTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = strings.settingsClearSiteDataDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    allSites.forEach { site ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SiteIconBadge(
                                    name = site.name,
                                    iconUrl = site.iconUrl,
                                    iconPreset = site.iconPreset,
                                    accentColorHex = site.accentColorHex,
                                    size = 36.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = site.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = { sitePendingDataWipe = site },
                                modifier = Modifier.testTag("wipe_site_btn_${site.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CleaningServices,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Backup, Restore & Deep Link Verification
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = strings.settingsBackupRestore,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = strings.settingsBackupDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { exportBackupLauncher.launch("ai_hub_backup.json") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_backup_btn")
                        ) {
                            Text(strings.exportBackupBtn)
                        }
                        OutlinedButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_backup_btn")
                        ) {
                            Text(strings.importBackupBtn)
                        }
                    }

                    OutlinedButton(
                        onClick = onRestoreDefaults,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_defaults_btn")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.resetDefaultsBtn)
                    }

                    OutlinedButton(
                        onClick = { showDeepLinkTester = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_deeplink_tester_btn")
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.testExternalLinkBtn)
                    }
                }
            }
        }
    }

    if (sitePendingDataWipe != null) {
        val target = sitePendingDataWipe!!
        AlertDialog(
            onDismissRequest = { sitePendingDataWipe = null },
            title = { Text("${strings.clearSessionConfirmTitle} (${target.name})") },
            text = { Text(strings.clearSessionConfirmDesc) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearSingleSiteData(target)
                        sitePendingDataWipe = null
                    },
                    modifier = Modifier.testTag("confirm_wipe_site_btn")
                ) {
                    Text(strings.browserClearSiteData)
                }
            },
            dismissButton = {
                TextButton(onClick = { sitePendingDataWipe = null }) {
                    Text(strings.browserCancel)
                }
            }
        )
    }

    if (showDeepLinkTester) {
        AlertDialog(
            onDismissRequest = { showDeepLinkTester = false },
            title = { Text(strings.testExternalLinkTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.testExternalLinkHint,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = testDeepLinkInput,
                        onValueChange = { testDeepLinkInput = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("deeplink_test_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeepLinkTester = false
                        onSimulateDeepLink(testDeepLinkInput)
                    },
                    modifier = Modifier.testTag("simulate_deeplink_btn")
                ) {
                    Text(strings.simulateLinkOpen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeepLinkTester = false }) {
                    Text(strings.browserCancel)
                }
            }
        )
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManageSitesSubScreen(
    sites: List<SiteEntity>,
    onBack: () -> Unit,
    onAddSite: () -> Unit,
    onEditSite: (SiteEntity) -> Unit,
    onToggleEnabled: (SiteEntity) -> Unit,
    onMoveOrder: (String, Boolean) -> Unit,
    onReorderSites: (Int, Int) -> Unit,
    onClearSiteSession: (SiteEntity) -> Unit,
    onDeleteSite: (SiteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("manage_sites_subscreen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("manage_sites_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.browserBack)
                }
                Text(
                    text = strings.settingsManageSites,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(onClick = onAddSite, modifier = Modifier.testTag("manage_add_site_btn")) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(strings.addSiteButton)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(sites, key = { _, s -> s.id }) { index, site ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            if (draggingIndex == index) {
                                translationY = dragOffsetY
                                shadowElevation = 18f
                                alpha = 0.92f
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SiteIconBadge(
                                name = site.name,
                                iconUrl = site.iconUrl,
                                iconPreset = site.iconPreset,
                                accentColorHex = site.accentColorHex,
                                size = 44.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = site.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = site.url,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = site.isEnabled,
                                onCheckedChange = { onToggleEnabled(site) },
                                modifier = Modifier.testTag("switch_enable_${site.id}")
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = "اضغط مطولاً واسحب لإعادة الترتيب",
                                tint = if (draggingIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("drag_handle_${site.id}")
                                    .pointerInput(site.id, index, sites.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingIndex = index
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffsetY += amount.y
                                                val threshold = 76.dp.toPx()
                                                if (dragOffsetY > threshold && index < sites.lastIndex) {
                                                    onReorderSites(index, index + 1)
                                                    dragOffsetY -= threshold
                                                } else if (dragOffsetY < -threshold && index > 0) {
                                                    onReorderSites(index, index - 1)
                                                    dragOffsetY += threshold
                                                }
                                            },
                                            onDragEnd = {
                                                draggingIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingIndex = null
                                                dragOffsetY = 0f
                                            }
                                        )
                                    }
                            )
                            IconButton(
                                onClick = { onClearSiteSession(site) },
                                modifier = Modifier.testTag("manage_clear_data_${site.id}")
                            ) {
                                Icon(Icons.Filled.CleaningServices, contentDescription = strings.browserClearSiteData)
                            }
                            IconButton(
                                onClick = { onEditSite(site) },
                                modifier = Modifier.testTag("manage_edit_${site.id}")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = strings.edit)
                            }
                            IconButton(
                                onClick = { onDeleteSite(site) },
                                modifier = Modifier.testTag("manage_delete_${site.id}")
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = strings.delete,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsSubScreen(
    downloads: List<DownloadEntity>,
    onBack: () -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("downloads_subscreen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("downloads_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.browserBack)
                }
                Text(
                    text = strings.settingsDownloads,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { BrowserDownloadAndAuthHelper.openSystemDownloadsView(context) }
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = strings.openFile)
                }
                if (downloads.isNotEmpty()) {
                    OutlinedButton(onClick = onClearAll) {
                        Text(strings.clearDownloadsBtn)
                    }
                }
            }
        }

        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = strings.emptyDownloadsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.emptyDownloadsDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.id }) { dl ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dl.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${dl.siteName} • ${dl.mimeType}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { BrowserDownloadAndAuthHelper.openSystemDownloadsView(context) }
                            ) {
                                Text(strings.openFile)
                            }
                            IconButton(onClick = { onDeleteDownload(dl.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = strings.delete)
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun writeBackupToUri(context: Context, uri: Uri, content: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

private suspend fun readBackupFromUri(context: Context, uri: Uri): String? {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }
        } catch (_: Exception) {
            null
        }
    }
}
