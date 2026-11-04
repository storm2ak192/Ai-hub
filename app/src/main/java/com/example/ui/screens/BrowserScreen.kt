package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.browser.BrowserDownloadAndAuthHelper
import com.example.browser.IsolatedSessionManager
import com.example.data.local.DownloadEntity
import com.example.data.local.SiteEntity
import com.example.ui.components.SiteIconBadge
import com.example.ui.localization.LocalAppStrings
import com.example.util.UrlSecurityValidator
import kotlinx.coroutines.launch
import java.io.File

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    site: SiteEntity,
    initialUrl: String,
    sessionManager: IsolatedSessionManager,
    onReturnHome: () -> Unit,
    onToggleDesktopMode: (SiteEntity) -> Unit,
    onUpdateZoomPercent: (SiteEntity, Int) -> Unit,
    onEditSitePermissions: (SiteEntity) -> Unit,
    onClearSiteSession: (SiteEntity, WebView?) -> Unit,
    onRecordHistory: (title: String, url: String) -> Unit,
    onDownloadEnqueued: (DownloadEntity) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val currentSite by rememberUpdatedState(site)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var defaultMobileUserAgent by remember { mutableStateOf<String?>(null) }
    var isProfileReady by remember(site.profileKey) { mutableStateOf(false) }

    var pageTitle by remember { mutableStateOf(site.name) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var hasLoadError by remember { mutableStateOf(false) }
    var errorDescription by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Track last applied desktop mode so toggling reloads the page with the new User-Agent
    var appliedDesktopMode by remember(site.id) { mutableStateOf(site.desktopMode) }

    // Custom View for HTML5 Fullscreen Video
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Popup Window Support (OAuth / SSO popups inside same isolated profile)
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    // File Upload State
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var cameraCaptureUri by remember { mutableStateOf<Uri?>(null) }

    // WebRTC Camera / Microphone Runtime Permission State
    var pendingWebPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileUploadCallback
        fileUploadCallback = null
        if (callback == null) return@rememberLauncherForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = mutableListOf<Uri>()
            val clipData = data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                }
            } else if (data?.data != null) {
                uris.add(data.data!!)
            } else if (cameraCaptureUri != null) {
                uris.add(cameraCaptureUri!!)
            }
            callback.onReceiveValue(uris.toTypedArray())
        } else {
            callback.onReceiveValue(null)
        }
        cameraCaptureUri = null
    }

    val webRtcPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val request = pendingWebPermissionRequest
        pendingWebPermissionRequest = null
        if (request == null) return@rememberLauncherForActivityResult

        val grantedResources = mutableListOf<String>()
        if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
            grantResults[Manifest.permission.CAMERA] == true &&
            currentSite.allowCamera
        ) {
            grantedResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        }
        if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
            grantResults[Manifest.permission.RECORD_AUDIO] == true &&
            currentSite.allowMicrophone
        ) {
            grantedResources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        }
        if (request.resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
            grantedResources.add(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        }

        if (grantedResources.isNotEmpty()) {
            request.grant(grantedResources.toTypedArray())
        } else {
            request.deny()
        }
    }

    // Back button behavior: exit HTML5 video -> close popup -> exit fullscreen -> WebView back -> Return Home
    BackHandler {
        when {
            customVideoView != null -> {
                customVideoCallback?.onCustomViewHidden()
                customVideoView = null
            }
            popupWebView != null -> {
                popupWebView?.destroy()
                popupWebView = null
            }
            isFullScreen -> {
                isFullScreen = false
            }
            webViewRef?.canGoBack() == true -> {
                webViewRef?.goBack()
            }
            else -> {
                onReturnHome()
            }
        }
    }

    // Track last applied zoom so initial 100% mobile load uses untouched native viewport
    var appliedZoomPercent by remember(site.id) { mutableIntStateOf(site.zoomPercent) }

    // Apply live JavaScript, Popup, Desktop Mode, and Manual Zoom updates dynamically
    LaunchedEffect(site.javaScriptEnabled, site.allowPopups, site.desktopMode, site.zoomPercent, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        wv.settings.apply {
            javaScriptEnabled = site.javaScriptEnabled
            javaScriptCanOpenWindowsAutomatically = site.allowPopups
            setSupportMultipleWindows(site.allowPopups)
        }
        applyDesktopAndZoomSettings(
            webView = wv,
            desktopMode = site.desktopMode,
            defaultMobileUa = defaultMobileUserAgent
        )
        if (appliedDesktopMode != site.desktopMode) {
            appliedDesktopMode = site.desktopMode
            appliedZoomPercent = site.zoomPercent
            hasLoadError = false
            val targetReloadUrl = currentUrl.ifBlank { wv.url ?: site.url }
            wv.loadUrl(targetReloadUrl, buildRequestHeaders(site.desktopMode))
        } else if (site.javaScriptEnabled && (appliedZoomPercent != site.zoomPercent || site.desktopMode || site.zoomPercent != 100)) {
            val forceSnap = appliedZoomPercent != site.zoomPercent
            appliedZoomPercent = site.zoomPercent
            injectResponsiveViewportAndZoomScript(
                webView = wv,
                desktopMode = site.desktopMode,
                zoomPercent = site.zoomPercent,
                forceScaleSnap = forceSnap
            )
        }
    }

    DisposableEffect(site.id) {
        onDispose {
            fileUploadCallback?.onReceiveValue(null)
            pendingWebPermissionRequest?.deny()
            popupWebView?.destroy()
            popupWebView = null
            webViewRef?.let { wv ->
                wv.stopLoading()
                wv.destroy()
            }
            webViewRef = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("browser_screen")
    ) {
        // Top Browser Bar (Hidden in Fullscreen Mode)
        AnimatedVisibility(visible = !isFullScreen && customVideoView == null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWideBar = maxWidth >= 480.dp
                    val displayHost = remember(currentUrl) {
                        UrlSecurityValidator.extractNormalizedHost(currentUrl) ?: currentUrl
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Return to Home Button
                            IconButton(
                                onClick = onReturnHome,
                                modifier = Modifier.testTag("browser_home_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = strings.browserHome,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (isWideBar) {
                                SiteIconBadge(
                                    name = site.name,
                                    iconUrl = site.iconUrl,
                                    iconPreset = site.iconPreset,
                                    accentColorHex = site.accentColorHex,
                                    size = 32.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pageTitle.ifBlank { site.name },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = if (isWideBar) currentUrl else displayHost,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { webViewRef?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.testTag("browser_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = strings.browserBack
                                )
                            }

                            if (isWideBar || canGoForward) {
                                IconButton(
                                    onClick = { webViewRef?.goForward() },
                                    enabled = canGoForward,
                                    modifier = Modifier.testTag("browser_forward_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = strings.browserForward
                                    )
                                }
                            }

                        IconButton(
                            onClick = {
                                hasLoadError = false
                                webViewRef?.reload()
                            },
                            modifier = Modifier.testTag("browser_reload_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = strings.browserReload
                            )
                        }

                        // Dedicated Desktop Mode Toggle Button inside WebView Toolbar
                        IconButton(
                            onClick = { onToggleDesktopMode(site) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (site.desktopMode) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                                contentColor = if (site.desktopMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ),
                            modifier = Modifier.testTag("browser_desktop_mode_btn")
                        ) {
                            Icon(
                                imageVector = if (site.desktopMode) {
                                    Icons.Filled.DesktopWindows
                                } else {
                                    Icons.Filled.PhoneAndroid
                                },
                                contentDescription = strings.browserDesktopMode
                            )
                        }

                        IconButton(
                            onClick = { isFullScreen = true },
                            modifier = Modifier.testTag("browser_fullscreen_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fullscreen,
                                contentDescription = strings.browserFullscreen
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("browser_overflow_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = strings.edit
                                )
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                // Manual Page Zoom Controls (-  100%  +) inside 3-Dots Menu
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = strings.browserZoomLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        IconButton(
                                            onClick = {
                                                onUpdateZoomPercent(site, (site.zoomPercent - 10).coerceAtLeast(50))
                                            },
                                            enabled = site.zoomPercent > 50,
                                            modifier = Modifier.testTag("menu_zoom_out_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ZoomOut,
                                                contentDescription = strings.browserZoomOut
                                            )
                                        }

                                        Surface(
                                            onClick = { onUpdateZoomPercent(site, 100) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (site.zoomPercent != 100) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            modifier = Modifier.testTag("menu_zoom_reset_btn")
                                        ) {
                                            Text(
                                                text = "${site.zoomPercent}%",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (site.zoomPercent != 100) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                onUpdateZoomPercent(site, (site.zoomPercent + 10).coerceAtMost(200))
                                            },
                                            enabled = site.zoomPercent < 200,
                                            modifier = Modifier.testTag("menu_zoom_in_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ZoomIn,
                                                contentDescription = strings.browserZoomIn
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = { Text(strings.browserDesktopMode) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.DesktopWindows, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        if (site.desktopMode) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onToggleDesktopMode(site)
                                    },
                                    modifier = Modifier.testTag("menu_desktop_mode_item")
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.browserOpenExternalLogin) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        BrowserDownloadAndAuthHelper.launchSecureExternalAuthTab(
                                            context,
                                            currentUrl
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.browserSiteSettings) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Tune, contentDescription = null)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onEditSitePermissions(site)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.browserClearSiteData) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.CleaningServices, contentDescription = null)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onClearSiteSession(site, webViewRef)
                                        webViewRef?.loadUrl(site.url)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.browserCopyLink) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("url", currentUrl))
                                        onShowMessage(strings.browserLinkCopied)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.browserShareLink) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Share, contentDescription = null)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, site.name))
                                    }
                                )
                            }
                        }
                    }

                    if (isLoading && loadProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { loadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .testTag("browser_progress_bar"),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                    }
                }
            }
        }

        // Main WebView Container: adapts dynamically to system navigation bar AND on-screen keyboard
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("browser_webview"),
                factory = { ctx ->
                    WebView(ctx).also { mainWv ->
                        mainWv.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        var lastAppliedWidth = 0
                        mainWv.addOnLayoutChangeListener { v, left, _, right, _, _, _, _, _ ->
                            val newW = right - left
                            if (newW > 0 && newW != lastAppliedWidth) {
                                val hadPreviousWidth = lastAppliedWidth > 0
                                lastAppliedWidth = newW
                                if (currentSite.javaScriptEnabled && (currentSite.desktopMode || currentSite.zoomPercent != 100 || hadPreviousWidth)) {
                                    injectResponsiveViewportAndZoomScript(
                                        webView = v as WebView,
                                        desktopMode = currentSite.desktopMode,
                                        zoomPercent = currentSite.zoomPercent,
                                        forceScaleSnap = currentSite.desktopMode || currentSite.zoomPercent != 100
                                    )
                                }
                            }
                        }
                        val capturedDefaultUa = mainWv.settings.userAgentString
                        defaultMobileUserAgent = capturedDefaultUa
                        configureSecureWebViewSettings(
                            webView = mainWv,
                            site = currentSite,
                            defaultMobileUa = capturedDefaultUa
                        )

                        mainWv.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUri = request?.url ?: return false
                                val targetStr = targetUri.toString()
                                val scheme = targetUri.scheme?.lowercase() ?: ""

                                // Handle external non-web intents gracefully (mailto:, tel:)
                                if (scheme != "https" && scheme != "http") {
                                    return try {
                                        if (scheme in setOf("mailto", "tel")) {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                                        }
                                        true
                                    } catch (_: Exception) {
                                        true
                                    }
                                }

                                // Respect strict federated SSO security (e.g., Google OAuth disallowed_useragent on third-party sites)
                                if (request.isForMainFrame &&
                                    UrlSecurityValidator.isFederatedOAuthChallenge(targetStr, currentSite.url)
                                ) {
                                    BrowserDownloadAndAuthHelper.launchSecureExternalAuthTab(ctx, targetStr)
                                    return true
                                }

                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                hasLoadError = false
                                if (!url.isNullOrBlank()) {
                                    currentUrl = url
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                val finalUrl = url ?: currentUrl
                                currentUrl = finalUrl
                                val resolvedTitle = view?.title ?: currentSite.name
                                pageTitle = resolvedTitle

                                if (view != null && currentSite.javaScriptEnabled) {
                                    injectResponsiveViewportAndZoomScript(
                                        webView = view,
                                        desktopMode = currentSite.desktopMode,
                                        zoomPercent = currentSite.zoomPercent,
                                        forceScaleSnap = currentSite.desktopMode || currentSite.zoomPercent != 100
                                    )
                                }

                                if (!hasLoadError && finalUrl.startsWith("http")) {
                                    onRecordHistory(resolvedTitle, finalUrl)
                                }
                                scope.launch {
                                    sessionManager.persistActiveSession(currentSite)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    hasLoadError = true
                                    errorDescription = error?.description?.toString() ?: ""
                                    isLoading = false
                                }
                            }
                        }

                        mainWv.webChromeClient = createHubWebChromeClient(
                            getSite = { currentSite },
                            onProgress = { progress ->
                                loadProgress = progress
                                isLoading = progress < 100
                            },
                            onTitle = { title ->
                                if (title.isNotBlank()) pageTitle = title
                            },
                            onShowFileChooserRequest = { callback, params ->
                                fileUploadCallback?.onReceiveValue(null)
                                fileUploadCallback = callback
                                val chooserIntent = buildUploadChooserIntent(ctx, params) { uri ->
                                    cameraCaptureUri = uri
                                }
                                fileChooserLauncher.launch(chooserIntent)
                            },
                            onWebRtcPermission = { permReq ->
                                val neededAndroidPerms = mutableListOf<String>()
                                if (permReq.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
                                    currentSite.allowCamera
                                ) {
                                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                        neededAndroidPerms.add(Manifest.permission.CAMERA)
                                    }
                                }
                                if (permReq.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                                    currentSite.allowMicrophone
                                ) {
                                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                        neededAndroidPerms.add(Manifest.permission.RECORD_AUDIO)
                                    }
                                }

                                if (neededAndroidPerms.isEmpty()) {
                                    val allowed = mutableListOf<String>()
                                    if (permReq.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && currentSite.allowCamera) {
                                        allowed.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                                    }
                                    if (permReq.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && currentSite.allowMicrophone) {
                                        allowed.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                                    }
                                    if (permReq.resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                                        allowed.add(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
                                    }
                                    if (allowed.isNotEmpty()) permReq.grant(allowed.toTypedArray()) else permReq.deny()
                                } else {
                                    pendingWebPermissionRequest = permReq
                                    webRtcPermissionLauncher.launch(neededAndroidPerms.toTypedArray())
                                }
                            },
                            onCreatePopup = { resultMsg ->
                                if (!currentSite.allowPopups || resultMsg == null) return@createHubWebChromeClient false
                                val childWv = WebView(ctx)
                                childWv.layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scope.launch {
                                    sessionManager.attachIsolatedProfile(childWv, currentSite)
                                    configureSecureWebViewSettings(
                                        webView = childWv,
                                        site = currentSite,
                                        defaultMobileUa = capturedDefaultUa
                                    )
                                    childWv.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            v: WebView?,
                                            req: WebResourceRequest?
                                        ): Boolean {
                                            val u = req?.url?.toString() ?: return false
                                            if (UrlSecurityValidator.isFederatedOAuthChallenge(u, currentSite.url)) {
                                                BrowserDownloadAndAuthHelper.launchSecureExternalAuthTab(ctx, u)
                                                popupWebView?.destroy()
                                                popupWebView = null
                                                return true
                                            }
                                            return false
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            scope.launch {
                                                sessionManager.persistActiveSession(currentSite)
                                            }
                                        }
                                    }
                                    childWv.webChromeClient = object : WebChromeClient() {
                                        override fun onCloseWindow(window: WebView?) {
                                            popupWebView?.destroy()
                                            popupWebView = null
                                        }
                                    }
                                    val transport = resultMsg.obj as? WebView.WebViewTransport
                                    transport?.webView = childWv
                                    resultMsg.sendToTarget()
                                    popupWebView = childWv
                                }
                                true
                            },
                            onCustomVideoShow = { view, callback ->
                                customVideoView = view
                                customVideoCallback = callback
                            },
                            onCustomVideoHide = {
                                customVideoView = null
                                customVideoCallback = null
                            }
                        )

                        mainWv.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                            if (!currentSite.allowDownloads) return@setDownloadListener
                            val dl = BrowserDownloadAndAuthHelper.enqueueDownload(
                                context = ctx,
                                site = currentSite,
                                url = url,
                                userAgent = userAgent,
                                contentDisposition = contentDisposition,
                                mimeType = mimeType,
                                contentLength = contentLength
                            )
                            if (dl != null) {
                                onDownloadEnqueued(dl)
                            }
                        }

                        webViewRef = mainWv

                        // Attach isolated profile BEFORE loading initial URL
                        scope.launch {
                            sessionManager.attachIsolatedProfile(mainWv, currentSite)
                            isProfileReady = true
                            mainWv.loadUrl(initialUrl, buildRequestHeaders(currentSite.desktopMode))
                        }
                    }
                }
            )

            // Initial Profile Attachment Spinner
            if (!isProfileReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Friendly Connection / Page Load Error Overlay
            if (hasLoadError) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("browser_error_overlay")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = strings.browserConnectionErrorTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = strings.browserConnectionErrorDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (errorDescription.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = errorDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    hasLoadError = false
                                    webViewRef?.reload()
                                },
                                modifier = Modifier.testTag("browser_retry_btn")
                            ) {
                                Text(strings.browserRetry)
                            }
                            Button(onClick = onReturnHome) {
                                Text(strings.browserHome)
                            }
                        }
                    }
                }
            }

            // HTML5 Fullscreen Video Container
            if (customVideoView != null) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            val v = customVideoView
                            (v?.parent as? ViewGroup)?.removeView(v)
                            if (v != null) addView(v)
                        }
                    }
                )
            }

            // Floating Controls when in Fullscreen Mode (including Desktop Mode button)
            if (isFullScreen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = onReturnHome,
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = strings.browserHome)
                    }
                    SmallFloatingActionButton(
                        onClick = { onToggleDesktopMode(site) },
                        shape = CircleShape,
                        containerColor = if (site.desktopMode) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        },
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        modifier = Modifier.testTag("browser_fullscreen_desktop_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DesktopWindows,
                            contentDescription = strings.browserDesktopMode,
                            tint = if (site.desktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = { isFullScreen = false },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        modifier = Modifier.testTag("browser_exit_fullscreen_btn")
                    ) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = strings.browserExitFullscreen)
                    }
                }
            }
        }
    }

    // Popup Window Dialog (Sharing the exact same isolated site profile)
    if (popupWebView != null) {
        Dialog(
            onDismissRequest = {
                popupWebView?.destroy()
                popupWebView = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strings.browserPopupTitle} • ${site.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                popupWebView?.destroy()
                                popupWebView = null
                            }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = strings.browserClosePopup)
                        }
                    }
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                val child = popupWebView
                                (child?.parent as? ViewGroup)?.removeView(child)
                                if (child != null) addView(child)
                            }
                        }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureSecureWebViewSettings(
    webView: WebView,
    site: SiteEntity,
    defaultMobileUa: String?
) {
    webView.settings.apply {
        javaScriptEnabled = site.javaScriptEnabled
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        javaScriptCanOpenWindowsAutomatically = site.allowPopups
        setSupportMultipleWindows(site.allowPopups)
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        allowFileAccess = false
        allowContentAccess = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode = WebSettings.LOAD_DEFAULT
    }
    applyDesktopAndZoomSettings(
        webView = webView,
        desktopMode = site.desktopMode,
        defaultMobileUa = defaultMobileUa
    )
}

private fun applyDesktopAndZoomSettings(
    webView: WebView,
    desktopMode: Boolean,
    defaultMobileUa: String?
) {
    webView.settings.apply {
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        // Keep textZoom at 100 so text and UI containers scale proportionally together via viewport zoom
        textZoom = 100

        if (desktopMode) {
            userAgentString = DESKTOP_USER_AGENT
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        } else {
            userAgentString = defaultMobileUa ?: WebSettings.getDefaultUserAgent(webView.context)
            // In Mobile/Tablet Mode: disable overview shrinking so responsive pages render at natural 100% width
            useWideViewPort = true
            loadWithOverviewMode = false
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        }
    }
    webView.setInitialScale(0)
    webView.scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
    webView.isScrollbarFadingEnabled = true
}

private fun buildRequestHeaders(desktopMode: Boolean): Map<String, String> {
    return if (desktopMode) {
        mapOf(
            "Sec-CH-UA" to "\"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            "Sec-CH-UA-Mobile" to "?0",
            "Sec-CH-UA-Platform" to "\"Linux\""
        )
    } else {
        mapOf(
            "Sec-CH-UA-Mobile" to "?1",
            "Sec-CH-UA-Platform" to "\"Android\""
        )
    }
}

/**
 * Dynamically adapts viewport sizing across Phones & Tablets:
 * - Mobile/Tablet Mode at 100%: Preserves native responsive `width=device-width, initial-scale=1.0`
 *   so modern apps fill 100% of screen width & height cleanly just like Chrome Mobile.
 * - Mobile/Tablet Mode with Manual Zoom (50%..200%): Adjusts effective viewport width & scale
 *   proportionally so the entire page zooms in/out smoothly without margins or broken 100vh bars.
 * - Desktop Mode: Uses an adaptive desktop canvas (`1100px` on phones, `1280px` on tablets)
 *   scaled by `zoomPercent` to fit edge-to-edge while preserving full pinch-to-zoom.
 */
private fun injectResponsiveViewportAndZoomScript(
    webView: WebView,
    desktopMode: Boolean,
    zoomPercent: Int,
    forceScaleSnap: Boolean = false
) {
    val density = webView.resources.displayMetrics.density.coerceAtLeast(1f)
    val viewWidthDp = if (webView.width > 0) {
        (webView.width / density).toInt()
    } else {
        (webView.resources.displayMetrics.widthPixels / density).toInt()
    }
    val safeCssWidth = viewWidthDp.coerceAtLeast(320)
    val clampedZoom = zoomPercent.coerceIn(50, 200)
    val zoomFactor = clampedZoom / 100f

    val js = if (desktopMode) {
        val baseDesktopWidth = if (safeCssWidth < 600) 1100f else 1280f
        val effectiveDesktopWidth = (baseDesktopWidth / zoomFactor).toInt().coerceIn(520, 2400)
        val fitScale = (safeCssWidth.toFloat() / effectiveDesktopWidth.toFloat()).coerceIn(0.20f, 3.0f)
        val scaleStr = String.format(java.util.Locale.US, "%.4f", fitScale)
        """
        (function() {
            try {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    if (document.head) document.head.appendChild(meta);
                }
                if (meta && !meta.hasAttribute('data-aihub-orig')) {
                    meta.setAttribute('data-aihub-orig', meta.getAttribute('content') || '');
                }
                if (meta) {
                    meta.setAttribute('content', 'width=$effectiveDesktopWidth, initial-scale=$scaleStr, minimum-scale=$scaleStr, maximum-scale=$scaleStr, user-scalable=yes');
                    setTimeout(function() {
                        if (meta) {
                            meta.setAttribute('content', 'width=$effectiveDesktopWidth, initial-scale=$scaleStr, minimum-scale=0.20, maximum-scale=5.0, user-scalable=yes');
                        }
                    }, 60);
                }
            } catch (e) {}
        })();
        """.trimIndent()
    } else if (clampedZoom != 100) {
        val effectiveMobileWidth = (safeCssWidth.toFloat() / zoomFactor).toInt().coerceIn(240, 1800)
        val targetScale = (safeCssWidth.toFloat() / effectiveMobileWidth.toFloat()).coerceIn(0.25f, 3.0f)
        val scaleStr = String.format(java.util.Locale.US, "%.4f", targetScale)
        """
        (function() {
            try {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    if (document.head) document.head.appendChild(meta);
                }
                if (meta && !meta.hasAttribute('data-aihub-orig')) {
                    meta.setAttribute('data-aihub-orig', meta.getAttribute('content') || '');
                }
                if (meta) {
                    meta.setAttribute('content', 'width=$effectiveMobileWidth, initial-scale=$scaleStr, minimum-scale=$scaleStr, maximum-scale=$scaleStr, user-scalable=yes');
                    setTimeout(function() {
                        if (meta) {
                            meta.setAttribute('content', 'width=$effectiveMobileWidth, initial-scale=$scaleStr, minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes');
                        }
                    }, 60);
                }
            } catch (e) {}
        })();
        """.trimIndent()
    } else {
        // Default 100% Mobile/Tablet Mode
        val snapResetBlock = if (forceScaleSnap) {
            """
            meta.setAttribute('content', 'width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=yes');
            setTimeout(function() {
                if (meta) {
                    meta.setAttribute('content', restoredContent);
                }
            }, 60);
            """.trimIndent()
        } else {
            "meta.setAttribute('content', restoredContent);"
        }
        """
        (function() {
            try {
                var meta = document.querySelector('meta[name="viewport"]');
                var defaultMobileViewport = 'width=device-width, initial-scale=1.0, minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes';
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    meta.setAttribute('content', defaultMobileViewport);
                    if (document.head) document.head.appendChild(meta);
                } else if (meta.hasAttribute('data-aihub-orig')) {
                    var orig = meta.getAttribute('data-aihub-orig');
                    meta.removeAttribute('data-aihub-orig');
                    var restoredContent = (orig && orig.trim().length > 0) ? orig : defaultMobileViewport;
                    $snapResetBlock
                }
            } catch (e) {}
        })();
        """.trimIndent()
    }
    webView.evaluateJavascript(js, null)
}

private fun createHubWebChromeClient(
    getSite: () -> SiteEntity,
    onProgress: (Int) -> Unit,
    onTitle: (String) -> Unit,
    onShowFileChooserRequest: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams?) -> Unit,
    onWebRtcPermission: (PermissionRequest) -> Unit,
    onCreatePopup: (Message?) -> Boolean,
    onCustomVideoShow: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onCustomVideoHide: () -> Unit
): WebChromeClient {
    return object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            onProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (!title.isNullOrBlank()) onTitle(title)
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            if (filePathCallback == null) return false
            onShowFileChooserRequest(filePathCallback, fileChooserParams)
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            if (request == null) return
            onWebRtcPermission(request)
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            if (!getSite().allowPopups) return false
            return onCreatePopup(resultMsg)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view != null && callback != null) {
                onCustomVideoShow(view, callback)
            }
        }

        override fun onHideCustomView() {
            onCustomVideoHide()
        }
    }
}

private fun buildUploadChooserIntent(
    context: Context,
    params: WebChromeClient.FileChooserParams?,
    onCameraUriCreated: (Uri) -> Unit
): Intent {
    val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        val validMimeTypes = normalizeAcceptMimeTypes(params?.acceptTypes)
        if (validMimeTypes.isNotEmpty()) {
            putExtra(Intent.EXTRA_MIME_TYPES, validMimeTypes.toTypedArray())
        }
        if (params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }

    val initialIntents = mutableListOf<Intent>()
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        try {
            val captureDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
            val photoFile = File(captureDir, "capture_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            onCameraUriCreated(photoUri)
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            initialIntents.add(cameraIntent)
        } catch (_: Exception) {
        }
    }

    return Intent(Intent.ACTION_CHOOSER).apply {
        putExtra(Intent.EXTRA_INTENT, getContentIntent)
        if (initialIntents.isNotEmpty()) {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray())
        }
    }
}

private fun normalizeAcceptMimeTypes(rawAcceptTypes: Array<String>?): List<String> {
    if (rawAcceptTypes.isNullOrEmpty()) return emptyList()
    val tokens = rawAcceptTypes
        .flatMap { it.split(",") }
        .map { it.trim().lowercase(java.util.Locale.US) }
        .filter { it.isNotEmpty() }

    if (tokens.isEmpty() || tokens.contains("*/*")) return emptyList()

    val resolved = mutableSetOf<String>()
    for (token in tokens) {
        if (token.startsWith(".")) {
            val ext = token.removePrefix(".")
            val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!mapped.isNullOrBlank()) {
                resolved.add(mapped)
            } else {
                // Unknown custom extension (e.g., .tsx, .ipynb, .md) — allow "*/*" so picker never greys it out!
                return emptyList()
            }
        } else if (token.contains("/")) {
            resolved.add(token)
        }
    }
    return resolved.toList()
}

