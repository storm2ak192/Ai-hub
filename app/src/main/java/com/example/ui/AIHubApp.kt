package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SiteEntity
import com.example.ui.components.AddEditSiteDialog
import com.example.ui.localization.LocalAppStrings
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.BottomNavTab
import com.example.ui.viewmodel.HubViewModel
import com.example.util.UrlSecurityValidator
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

@Composable
fun AIHubApp(
    viewModel: HubViewModel
) {
    val strings = LocalAppStrings.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val settingsSubScreen by viewModel.settingsSubScreen.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val allSites by viewModel.allSites.collectAsStateWithLifecycle()
    val filteredEnabledSites by viewModel.filteredEnabledSites.collectAsStateWithLifecycle()
    val favoriteSites by viewModel.favoriteSites.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val downloadsList by viewModel.downloadsList.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val activeBrowserSession by viewModel.activeBrowserSession.collectAsStateWithLifecycle()
    val pendingUntrustedLink by viewModel.pendingUntrustedLink.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<SiteEntity?>(null) }
    var pendingSecurePopupUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    // Back handler on non-home tabs when browser is closed
    if (activeBrowserSession == null && selectedTab != BottomNavTab.HOME) {
        BackHandler {
            viewModel.selectTab(BottomNavTab.HOME)
        }
    }

    val isBrowserOpen = activeBrowserSession != null
    LaunchedEffect(isBrowserOpen) {
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val currentSession = activeBrowserSession
        if (currentSession != null) {
            // Lazy Single-Site Instantiation: Only the currently active website creates a WebView!
            Box(modifier = Modifier.fillMaxSize()) {
                BrowserScreen(
                    site = currentSession.site,
                    initialUrl = currentSession.initialUrl,
                    sessionManager = viewModel.sessionManager,
                    allSites = allSites,
                    onOpenMatchedSite = { matchedSite, url -> viewModel.openSite(matchedSite, overrideUrl = url) },
                    onUnknownExternalUrl = { url -> viewModel.handleExternalUrl(url) },
                    onReturnHome = { viewModel.closeBrowserToHome() },
                    onToggleDesktopMode = { siteToToggle ->
                        viewModel.toggleSiteDesktopMode(siteToToggle)
                    },
                    onUpdateZoomPercent = { siteToZoom, newZoom ->
                        viewModel.updateSiteZoomPercent(siteToZoom, newZoom)
                    },
                    onEditSitePermissions = { siteToEdit ->
                        editingSite = siteToEdit
                        showAddEditDialog = true
                    },
                    onClearSiteSession = { siteToClear, activeWv ->
                        viewModel.clearSingleSiteData(
                            site = siteToClear,
                            activeWebView = activeWv,
                            successMessage = strings.clearSessionSuccess
                        )
                    },
                    onRecordHistory = { title, url ->
                        viewModel.recordPageVisit(currentSession.site, title, url)
                    },
                    onDownloadEnqueued = { dl ->
                        viewModel.recordDownload(dl, strings.downloadStarted)
                    },
                    onShowMessage = { msg -> viewModel.showMessage(msg) }
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                )
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 700.dp

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (!useNavigationRail) {
                            HubBottomNavigationBar(
                                selectedTab = selectedTab,
                                onSelectTab = { viewModel.selectTab(it) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail) {
                            HubSideNavigationRail(
                                selectedTab = selectedTab,
                                onSelectTab = { viewModel.selectTab(it) }
                            )
                        }

                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                            },
                            label = "mainTabTransition",
                            modifier = Modifier.weight(1f)
                        ) { tab ->
                            when (tab) {
                                BottomNavTab.HOME -> {
                                    HomeScreen(
                                        sites = filteredEnabledSites,
                                        favorites = favoriteSites,
                                        searchQuery = searchQuery,
                                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                                        onOpenSite = { viewModel.openSite(it) },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onEditSite = { site ->
                                            editingSite = site
                                            showAddEditDialog = true
                                        },
                                        onAddToHomeScreen = { viewModel.pinSiteShortcut(it) },
                                        onAddSiteClick = {
                                            editingSite = null
                                            showAddEditDialog = true
                                        }
                                    )
                                }
                                // Keep the home-screen shortcut action available on favorite cards too.
                                BottomNavTab.FAVORITES -> {
                                    FavoritesScreen(
                                        favorites = favoriteSites,
                                        onOpenSite = { viewModel.openSite(it) },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onEditSite = { site ->
                                            editingSite = site
                                            showAddEditDialog = true
                                        },
                                        onAddToHomeScreen = { viewModel.pinSiteShortcut(it) }
                                    )
                                }
                                BottomNavTab.HISTORY -> {
                                    HistoryScreen(
                                        historyList = historyList,
                                        onOpenHistoryEntry = { viewModel.openHistoryItem(it) },
                                        onDeleteEntry = { viewModel.deleteHistoryEntry(it) },
                                        onClearAll = { viewModel.clearAllHistory() }
                                    )
                                }
                                BottomNavTab.SETTINGS -> {
                                    SettingsScreen(
                                        subScreen = settingsSubScreen,
                                        onNavigateSubScreen = { viewModel.openSettingsSubScreen(it) },
                                        allSites = allSites,
                                        downloads = downloadsList,
                                        themeMode = themeMode,
                                        language = language,
                                        onSetThemeMode = { viewModel.setThemeMode(it) },
                                        onSetLanguage = { viewModel.setLanguage(it) },
                                        onAddSiteClick = {
                                            editingSite = null
                                            showAddEditDialog = true
                                        },
                                        onEditSiteClick = { site ->
                                            editingSite = site
                                            showAddEditDialog = true
                                        },
                                        onToggleSiteEnabled = { viewModel.toggleSiteEnabled(it) },
                                        onMoveSiteOrder = { id, up -> viewModel.moveSiteOrder(id, up) },
                                        onReorderSites = { from, to -> viewModel.reorderSites(from, to) },
                                        onDeleteSite = { viewModel.deleteSiteAndSession(it) },
                                        onClearSingleSiteData = { site ->
                                            viewModel.clearSingleSiteData(
                                                site = site,
                                                activeWebView = null,
                                                successMessage = strings.clearSessionSuccess
                                            )
                                        },
                                        onRestoreDefaults = {
                                            viewModel.restoreDefaultSites(strings.defaultsRestoredSuccess)
                                        },
                                        onExportBackupJson = { viewModel.exportBackupJson() },
                                        onImportBackupJson = { json ->
                                            viewModel.importBackupJson(
                                                json = json,
                                                successMsg = strings.backupImportSuccess,
                                                errorMsg = strings.backupError
                                            )
                                        },
                                        onDeleteDownload = { viewModel.deleteDownloadEntry(it) },
                                        onClearDownloads = { viewModel.clearAllDownloads() },
                                        onSimulateDeepLink = { url -> viewModel.handleExternalUrl(url) },
                                        onShowMessage = { viewModel.showMessage(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Site Modal
    if (showAddEditDialog) {
        AddEditSiteDialog(
            existingSite = editingSite,
            onDismiss = {
                showAddEditDialog = false
                editingSite = null
            },
            onSave = { name, url, desc, iconUrl, iconPreset, colorHex, js, cam, mic, popups, downloads, desktopMode, enabled ->
                viewModel.addOrUpdateSite(
                    existing = editingSite,
                    name = name,
                    url = url,
                    description = desc,
                    iconUrl = iconUrl,
                    iconPreset = iconPreset,
                    accentColorHex = colorHex,
                    javaScriptEnabled = js,
                    allowCamera = cam,
                    allowMicrophone = mic,
                    allowPopups = popups,
                    allowDownloads = downloads,
                    desktopMode = desktopMode,
                    isEnabled = enabled
                )
            },
            onDelete = { siteToDelete ->
                viewModel.deleteSiteAndSession(siteToDelete)
            }
        )
    }

    // Untrusted External Domain Safety Verification Modal
    if (pendingUntrustedLink != null) {
        val pending = pendingUntrustedLink!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingUntrustedLink() },
            title = { Text(strings.browserUntrustedExternalTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.browserUntrustedExternalDesc)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pending.fullUrl,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.addUntrustedLinkAsSiteAndOpen(pending) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("untrusted_add_and_open_btn")
                    ) {
                        Text(strings.browserAddAndOpen)
                    }
                    OutlinedButton(
                        onClick = {
                            pendingSecurePopupUrl = pending.fullUrl
                            viewModel.dismissPendingUntrustedLink()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("untrusted_open_once_btn")
                    ) {
                        Text(strings.browserOpenInSecurePopup)
                    }
                    TextButton(
                        onClick = { viewModel.dismissPendingUntrustedLink() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.browserCancel)
                    }
                }
            }
        )
    }

    pendingSecurePopupUrl?.let { popupUrl ->
        SecureExternalLinkDialog(
            url = popupUrl,
            onDismiss = { pendingSecurePopupUrl = null }
        )
    }
}

@Composable
private fun SecureExternalLinkDialog(
    url: String,
    onDismiss: () -> Unit
) {
    var popupWebView by remember(url) { mutableStateOf<WebView?>(null) }

    DisposableEffect(url) {
        onDispose {
            popupWebView?.stopLoading()
            popupWebView?.destroy()
            popupWebView = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.84f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = url,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            popupWebView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.setSupportMultipleWindows(false)
                            if (android.os.Build.VERSION.SDK_INT >= 26) {
                                settings.safeBrowsingEnabled = true
                            }
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val target = request?.url?.toString() ?: return true
                                    return !target.startsWith("https://", ignoreCase = true) ||
                                        !UrlSecurityValidator.isSafeWebUrl(target)
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: android.webkit.SslErrorHandler?,
                                    error: android.net.http.SslError?
                                ) {
                                    handler?.cancel()
                                }
                            }
                            if (url.startsWith("https://", ignoreCase = true) && UrlSecurityValidator.isSafeWebUrl(url)) {
                                loadUrl(url)
                            } else {
                                onDismiss()
                            }
                        }
                    },
                    update = { webView ->
                        if (webView.url != url && webView.url.isNullOrBlank()) webView.loadUrl(url)
                    }
                )
            }
        }
    }
}

@Composable
private fun HubBottomNavigationBar(
    selectedTab: BottomNavTab,
    onSelectTab: (BottomNavTab) -> Unit
) {
    val strings = LocalAppStrings.current

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.testTag("bottom_navigation_bar")
    ) {
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.HOME,
            onClick = { onSelectTab(BottomNavTab.HOME) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomNavTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = strings.navHome
                )
            },
            label = { Text(strings.navHome) },
            modifier = Modifier.testTag("nav_tab_home")
        )
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.FAVORITES,
            onClick = { onSelectTab(BottomNavTab.FAVORITES) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomNavTab.FAVORITES) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = strings.navFavorites
                )
            },
            label = { Text(strings.navFavorites) },
            modifier = Modifier.testTag("nav_tab_favorites")
        )
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.HISTORY,
            onClick = { onSelectTab(BottomNavTab.HISTORY) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomNavTab.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                    contentDescription = strings.navHistory
                )
            },
            label = { Text(strings.navHistory) },
            modifier = Modifier.testTag("nav_tab_history")
        )
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.SETTINGS,
            onClick = { onSelectTab(BottomNavTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomNavTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = strings.navSettings
                )
            },
            label = { Text(strings.navSettings) },
            modifier = Modifier.testTag("nav_tab_settings")
        )
    }
}

@Composable
private fun HubSideNavigationRail(
    selectedTab: BottomNavTab,
    onSelectTab: (BottomNavTab) -> Unit
) {
    val strings = LocalAppStrings.current

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("side_navigation_rail")
    ) {
        NavigationRailItem(
            selected = selectedTab == BottomNavTab.HOME,
            onClick = { onSelectTab(BottomNavTab.HOME) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomNavTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = strings.navHome
                )
            },
            label = { Text(strings.navHome) }
        )
        NavigationRailItem(
            selected = selectedTab == BottomNavTab.FAVORITES,
            onClick = { onSelectTab(BottomNavTab.FAVORITES) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomNavTab.FAVORITES) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = strings.navFavorites
                )
            },
            label = { Text(strings.navFavorites) }
        )
        NavigationRailItem(
            selected = selectedTab == BottomNavTab.HISTORY,
            onClick = { onSelectTab(BottomNavTab.HISTORY) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomNavTab.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                    contentDescription = strings.navHistory
                )
            },
            label = { Text(strings.navHistory) }
        )
        NavigationRailItem(
            selected = selectedTab == BottomNavTab.SETTINGS,
            onClick = { onSelectTab(BottomNavTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomNavTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = strings.navSettings
                )
            },
            label = { Text(strings.navSettings) }
        )
    }
}
