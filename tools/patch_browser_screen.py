#!/usr/bin/env python3
"""Patches for BrowserScreen.kt — Google OAuth solution 1 + gap fix."""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/BrowserScreen.kt")
text = path.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# 1) WebView container insets:
#    normal mode = navigationBars + ime only (no gap under top app bar)
#    fullscreen  = statusBars + navigationBars + ime
# ---------------------------------------------------------------------------
old_inset = (
    "// Main WebView Container: adapts dynamically to system navigation bar AND on-screen keyboard\n"
    "        Box(\n"
    "            modifier = Modifier\n"
    "                .weight(1f)\n"
    "                .fillMaxWidth()\n"
    "                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))\n"
    "        ) {"
)
new_inset = (
    "// Main WebView Container:\n"
    "        // Normal: no statusBars padding (TopAppBar already handles it) — removes the gap\n"
    "        // Fullscreen: keep statusBars so content is not under the notification bar\n"
    "        Box(\n"
    "            modifier = Modifier\n"
    "                .weight(1f)\n"
    "                .fillMaxWidth()\n"
    "                .windowInsetsPadding(\n"
    "                    if (isFullScreen) {\n"
    "                        WindowInsets.statusBars\n"
    "                            .union(WindowInsets.navigationBars)\n"
    "                            .union(WindowInsets.ime)\n"
    "                    } else {\n"
    "                        WindowInsets.navigationBars.union(WindowInsets.ime)\n"
    "                    }\n"
    "                )\n"
    "        ) {"
)
if old_inset in text:
    text = text.replace(old_inset, new_inset, 1)
    print("OK: inset gap fix")
else:
    print("WARN: inset pattern missing")

# ---------------------------------------------------------------------------
# 2) Imports
# ---------------------------------------------------------------------------
if "detectDragGestures" not in text:
    text = text.replace(
        "import androidx.compose.foundation.background\n",
        "import androidx.compose.foundation.background\nimport androidx.compose.foundation.gestures.detectDragGestures\n",
        1,
    )
if "import androidx.compose.foundation.layout.offset\n" not in text:
    text = text.replace(
        "import androidx.compose.foundation.layout.padding\n",
        "import androidx.compose.foundation.layout.offset\nimport androidx.compose.foundation.layout.padding\n",
        1,
    )
if "pointerInput" not in text:
    text = text.replace(
        "import androidx.compose.ui.Modifier\n",
        "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.Modifier\n",
        1,
    )
if "IntOffset" not in text:
    text = text.replace(
        "import androidx.compose.ui.unit.dp\n",
        "import androidx.compose.ui.unit.IntOffset\nimport androidx.compose.ui.unit.dp\n",
        1,
    )
if "roundToInt" not in text:
    text = text.replace(
        "import java.io.File\n",
        "import java.io.File\nimport kotlin.math.roundToInt\n",
        1,
    )
if "KeyboardArrowRight" not in text:
    text = text.replace(
        "import androidx.compose.material.icons.filled.Home\n",
        "import androidx.compose.material.icons.filled.Home\n"
        "import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft\n"
        "import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight\n",
        1,
    )
if "ExternalLinkMatch" not in text:
    text = text.replace(
        "import com.example.util.UrlSecurityValidator\n",
        "import com.example.util.ExternalLinkMatch\nimport com.example.util.UrlSecurityValidator\n",
        1,
    )
if "import androidx.compose.ui.graphics.Color\n" not in text:
    text = text.replace(
        "import androidx.compose.ui.Alignment\n",
        "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.graphics.Color\n",
        1,
    )
print("OK: imports")

# ---------------------------------------------------------------------------
# 3) FAB state
# ---------------------------------------------------------------------------
if "fabMenuExpanded" not in text:
    old_fs = "var isFullScreen by remember { mutableStateOf(false) }"
    new_fs = (
        "var isFullScreen by remember { mutableStateOf(false) }\n"
        "    var fabOffsetX by remember { mutableStateOf(0f) }\n"
        "    var fabOffsetY by remember { mutableStateOf(0f) }\n"
        "    var fabMenuExpanded by remember { mutableStateOf(true) }"
    )
    if old_fs in text:
        text = text.replace(old_fs, new_fs, 1)
        print("OK: fab state")

# ---------------------------------------------------------------------------
# 4) Key session state by site.id so switching ChatGPT -> Google recreates WebView
# ---------------------------------------------------------------------------
old_profile_ready = "var isProfileReady by remember(site.profileKey) { mutableStateOf(false) }"
new_profile_ready = "var isProfileReady by remember(site.id) { mutableStateOf(false) }"
if old_profile_ready in text:
    text = text.replace(old_profile_ready, new_profile_ready, 1)
    print("OK: isProfileReady keyed by site.id")

old_current_url = "var currentUrl by remember { mutableStateOf(initialUrl) }"
new_current_url = "var currentUrl by remember(site.id, initialUrl) { mutableStateOf(initialUrl) }"
if old_current_url in text:
    text = text.replace(old_current_url, new_current_url, 1)
    print("OK: currentUrl keyed by site.id + initialUrl")

old_title = "var pageTitle by remember { mutableStateOf(site.name) }"
new_title = "var pageTitle by remember(site.id) { mutableStateOf(site.name) }"
if old_title in text:
    text = text.replace(old_title, new_title, 1)
    print("OK: pageTitle keyed by site.id")

# ---------------------------------------------------------------------------
# 5) AndroidView update block: when site/url changes, load the new URL
#    Also key factory recreation via modifier testTag including site.id
# ---------------------------------------------------------------------------
old_av = (
    "AndroidView(\n"
    "                modifier = Modifier\n"
    "                    .fillMaxSize()\n"
    "                    .testTag(\"browser_webview\"),"
)
new_av = (
    "AndroidView(\n"
    "                modifier = Modifier\n"
    "                    .fillMaxSize()\n"
    "                    .testTag(\"browser_webview_${site.id}\"),"
)
if old_av in text:
    text = text.replace(old_av, new_av, 1)
    print("OK: AndroidView tag includes site.id")

# Ensure update lambda reloads when initialUrl changes — find update = { wv ->
# Many codebases use update = { }. We'll inject after factory load if needed.

# ---------------------------------------------------------------------------
# 6) Draggable FAB modifier
# ---------------------------------------------------------------------------
old_mod = (
    ".align(Alignment.TopEnd)\n"
    "                        .windowInsetsPadding(WindowInsets.statusBars)\n"
    "                        .padding(12.dp),\n"
    "                    horizontalArrangement = Arrangement.spacedBy(8.dp)"
)
new_mod = (
    ".align(Alignment.TopEnd)\n"
    "                        .windowInsetsPadding(WindowInsets.statusBars)\n"
    "                        .padding(12.dp)\n"
    "                        .offset { IntOffset((-fabOffsetX).roundToInt(), fabOffsetY.roundToInt()) }\n"
    "                        .pointerInput(Unit) {\n"
    "                            detectDragGestures { change, dragAmount ->\n"
    "                                change.consume()\n"
    "                                fabOffsetX += dragAmount.x\n"
    "                                fabOffsetY += dragAmount.y\n"
    "                            }\n"
    "                        },\n"
    "                    horizontalArrangement = Arrangement.spacedBy(8.dp)"
)
if old_mod in text:
    text = text.replace(old_mod, new_mod, 1)
    print("OK: draggable FABs")

# ---------------------------------------------------------------------------
# 7) FAB colors + collapse arrow
# ---------------------------------------------------------------------------
old_row_body = (
    "SmallFloatingActionButton(\n"
    "                        onClick = onReturnHome,\n"
    "                        shape = CircleShape,\n"
    "                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),\n"
    "                        elevation = FloatingActionButtonDefaults.elevation(4.dp)\n"
    "                    ) {\n"
    "                        Icon(Icons.Filled.Home, contentDescription = strings.browserHome)\n"
    "                    }\n"
    "                    SmallFloatingActionButton(\n"
    "                        onClick = { onToggleDesktopMode(site) },\n"
    "                        shape = CircleShape,\n"
    "                        containerColor = if (site.desktopMode) {\n"
    "                            MaterialTheme.colorScheme.primaryContainer\n"
    "                        } else {\n"
    "                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)\n"
    "                        },\n"
    "                        elevation = FloatingActionButtonDefaults.elevation(4.dp),\n"
    "                        modifier = Modifier.testTag(\"browser_fullscreen_desktop_btn\")\n"
    "                    ) {\n"
    "                        Icon(\n"
    "                            imageVector = Icons.Filled.DesktopWindows,\n"
    "                            contentDescription = strings.browserDesktopMode,\n"
    "                            tint = if (site.desktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface\n"
    "                        )\n"
    "                    }\n"
    "                    SmallFloatingActionButton(\n"
    "                        onClick = { isFullScreen = false },\n"
    "                        shape = CircleShape,\n"
    "                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),\n"
    "                        elevation = FloatingActionButtonDefaults.elevation(4.dp),\n"
    "                        modifier = Modifier.testTag(\"browser_exit_fullscreen_btn\")\n"
    "                    ) {\n"
    "                        Icon(Icons.Filled.FullscreenExit, contentDescription = strings.browserExitFullscreen)\n"
    "                    }"
)

new_row_body = (
    "if (fabMenuExpanded) {\n"
    "                        SmallFloatingActionButton(\n"
    "                            onClick = onReturnHome,\n"
    "                            shape = CircleShape,\n"
    "                            containerColor = Color(0xFF1E88E5),\n"
    "                            contentColor = Color.White,\n"
    "                            elevation = FloatingActionButtonDefaults.elevation(6.dp)\n"
    "                        ) {\n"
    "                            Icon(Icons.Filled.Home, contentDescription = strings.browserHome, tint = Color.White)\n"
    "                        }\n"
    "                        SmallFloatingActionButton(\n"
    "                            onClick = { onToggleDesktopMode(site) },\n"
    "                            shape = CircleShape,\n"
    "                            containerColor = if (site.desktopMode) Color(0xFF43A047) else Color(0xFF546E7A),\n"
    "                            contentColor = Color.White,\n"
    "                            elevation = FloatingActionButtonDefaults.elevation(6.dp),\n"
    "                            modifier = Modifier.testTag(\"browser_fullscreen_desktop_btn\")\n"
    "                        ) {\n"
    "                            Icon(\n"
    "                                imageVector = Icons.Filled.DesktopWindows,\n"
    "                                contentDescription = strings.browserDesktopMode,\n"
    "                                tint = Color.White\n"
    "                            )\n"
    "                        }\n"
    "                        SmallFloatingActionButton(\n"
    "                            onClick = { isFullScreen = false },\n"
    "                            shape = CircleShape,\n"
    "                            containerColor = Color(0xFFE53935),\n"
    "                            contentColor = Color.White,\n"
    "                            elevation = FloatingActionButtonDefaults.elevation(6.dp),\n"
    "                            modifier = Modifier.testTag(\"browser_exit_fullscreen_btn\")\n"
    "                        ) {\n"
    "                            Icon(Icons.Filled.FullscreenExit, contentDescription = strings.browserExitFullscreen, tint = Color.White)\n"
    "                        }\n"
    "                    }\n"
    "                    SmallFloatingActionButton(\n"
    "                        onClick = { fabMenuExpanded = !fabMenuExpanded },\n"
    "                        shape = CircleShape,\n"
    "                        containerColor = Color(0xFFFFB300),\n"
    "                        contentColor = Color.Black,\n"
    "                        elevation = FloatingActionButtonDefaults.elevation(8.dp)\n"
    "                    ) {\n"
    "                        Icon(\n"
    "                            imageVector = if (fabMenuExpanded) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,\n"
    "                            contentDescription = \"Toggle controls\",\n"
    "                            tint = Color.Black\n"
    "                        )\n"
    "                    }"
)
if old_row_body in text:
    text = text.replace(old_row_body, new_row_body, 1)
    print("OK: FAB colors + arrows")

# ---------------------------------------------------------------------------
# 8) WebView speed
# ---------------------------------------------------------------------------
if "LAYER_TYPE_HARDWARE" not in text:
    old_wv = "WebView(ctx).also { mainWv ->"
    new_wv = (
        "WebView(ctx).also { mainWv ->\n"
        "                        mainWv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)\n"
        "                        mainWv.settings.domStorageEnabled = true\n"
        "                        mainWv.settings.loadsImagesAutomatically = true\n"
        "                        mainWv.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT"
    )
    if old_wv in text:
        text = text.replace(old_wv, new_wv, 1)
        print("OK: WebView speed")

# ---------------------------------------------------------------------------
# 9) Signature: allSites + routing callbacks
# ---------------------------------------------------------------------------
old_sig = (
    "fun BrowserScreen(\n"
    "    site: SiteEntity,\n"
    "    initialUrl: String,\n"
    "    sessionManager: IsolatedSessionManager,\n"
    "    onReturnHome: () -> Unit,\n"
    "    onToggleDesktopMode: (SiteEntity) -> Unit,\n"
    "    onUpdateZoomPercent: (SiteEntity, Int) -> Unit,\n"
    "    onEditSitePermissions: (SiteEntity) -> Unit,\n"
    "    onClearSiteSession: (SiteEntity, WebView?) -> Unit,\n"
    "    onRecordHistory: (title: String, url: String) -> Unit,\n"
    "    onDownloadEnqueued: (DownloadEntity) -> Unit,\n"
    "    onShowMessage: (String) -> Unit,\n"
    "    modifier: Modifier = Modifier\n"
    ") {"
)
new_sig = (
    "fun BrowserScreen(\n"
    "    site: SiteEntity,\n"
    "    initialUrl: String,\n"
    "    sessionManager: IsolatedSessionManager,\n"
    "    allSites: List<SiteEntity>,\n"
    "    onOpenMatchedSite: (SiteEntity, String) -> Unit,\n"
    "    onUnknownExternalUrl: (String) -> Unit,\n"
    "    onReturnHome: () -> Unit,\n"
    "    onToggleDesktopMode: (SiteEntity) -> Unit,\n"
    "    onUpdateZoomPercent: (SiteEntity, Int) -> Unit,\n"
    "    onEditSitePermissions: (SiteEntity) -> Unit,\n"
    "    onClearSiteSession: (SiteEntity, WebView?) -> Unit,\n"
    "    onRecordHistory: (title: String, url: String) -> Unit,\n"
    "    onDownloadEnqueued: (DownloadEntity) -> Unit,\n"
    "    onShowMessage: (String) -> Unit,\n"
    "    modifier: Modifier = Modifier\n"
    ") {"
)
if old_sig in text:
    text = text.replace(old_sig, new_sig, 1)
    print("OK: signature")
else:
    print("WARN: signature not found")

# ---------------------------------------------------------------------------
# 10) Solution 1: main-frame routing to matching hub site (full OAuth URL)
# ---------------------------------------------------------------------------
old_override = (
    "// Respect strict federated SSO security (e.g., Google OAuth disallowed_useragent on third-party sites)\n"
    "                                if (request.isForMainFrame &&\n"
    "                                    UrlSecurityValidator.isFederatedOAuthChallenge(targetStr, currentSite.url)\n"
    "                                ) {\n"
    "                                    BrowserDownloadAndAuthHelper.launchSecureExternalAuthTab(ctx, targetStr)\n"
    "                                    return true\n"
    "                                }\n\n"
    "                                return false"
)

new_override = (
    "// Solution 1: Google OAuth / known sites open inside their hub card with the FULL url\n"
    "                                if (request.isForMainFrame) {\n"
    "                                    when (val match = UrlSecurityValidator.resolveExternalLink(targetStr, allSites)) {\n"
    "                                        is ExternalLinkMatch.MatchedSite -> {\n"
    "                                            if (match.site.id != currentSite.id) {\n"
    "                                                onOpenMatchedSite(match.site, match.fullUrl)\n"
    "                                                return true\n"
    "                                            }\n"
    "                                        }\n"
    "                                        is ExternalLinkMatch.UnmatchedValidUrl -> {\n"
    "                                            val currentHost = UrlSecurityValidator.extractNormalizedHost(currentSite.url)\n"
    "                                            if (currentHost != null && match.host != currentHost &&\n"
    "                                                !match.host.endsWith(\".\" + currentHost) &&\n"
    "                                                !currentHost.endsWith(\".\" + match.host)\n"
    "                                            ) {\n"
    "                                                onUnknownExternalUrl(match.fullUrl)\n"
    "                                                return true\n"
    "                                            }\n"
    "                                        }\n"
    "                                        is ExternalLinkMatch.BlockedInvalidUrl -> return true\n"
    "                                    }\n"
    "                                }\n\n"
    "                                return false"
)

if old_override in text:
    text = text.replace(old_override, new_override, 1)
    print("OK: solution-1 routing")
else:
    print("WARN: shouldOverride block not found")

# ---------------------------------------------------------------------------
# 11) Popup OAuth: also route to Google Account site (not Custom Tab / blank)
# ---------------------------------------------------------------------------
old_popup = (
    "if (UrlSecurityValidator.isFederatedOAuthChallenge(u, currentSite.url)) {\n"
    "                                                BrowserDownloadAndAuthHelper.launchSecureExternalAuthTab(ctx, u)\n"
    "                                                popupWebView?.destroy()\n"
    "                                                popupWebView = null\n"
    "                                                return true\n"
    "                                            }"
)
new_popup = (
    "when (val match = UrlSecurityValidator.resolveExternalLink(u, allSites)) {\n"
    "                                                is ExternalLinkMatch.MatchedSite -> {\n"
    "                                                    if (match.site.id != currentSite.id) {\n"
    "                                                        popupWebView?.destroy()\n"
    "                                                        popupWebView = null\n"
    "                                                        onOpenMatchedSite(match.site, match.fullUrl)\n"
    "                                                        return true\n"
    "                                                    }\n"
    "                                                }\n"
    "                                                else -> { /* continue in popup */ }\n"
    "                                            }"
)
if old_popup in text:
    text = text.replace(old_popup, new_popup, 1)
    print("OK: popup routing to hub site")
else:
    print("WARN: popup OAuth block not found")

# ---------------------------------------------------------------------------
# 12) Force AndroidView to recreate when site.id changes using key()
# ---------------------------------------------------------------------------
if "key(site.id)" not in text and "androidx.compose.runtime.key" not in text:
    # Wrap AndroidView with key(site.id) { ... } — careful with braces
    # Prefer adding import and key around AndroidView
    if "import androidx.compose.runtime.key\n" not in text:
        text = text.replace(
            "import androidx.compose.runtime.getValue\n",
            "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.key\n",
            1,
        )
    marker = "AndroidView(\n                modifier = Modifier\n                    .fillMaxSize()\n                    .testTag(\"browser_webview_${site.id}\"),"
    if marker in text:
        text = text.replace(
            marker,
            "key(site.id, initialUrl) {\n            AndroidView(\n                modifier = Modifier\n                    .fillMaxSize()\n                    .testTag(\"browser_webview_${site.id}\"),",
            1,
        )
        # Close the key block after AndroidView ends - find the spinner that follows
        old_spinner = (
            "// Initial Profile Attachment Spinner\n"
            "            if (!isProfileReady) {"
        )
        new_spinner = (
            "} // end key(site.id)\n\n"
            "            // Initial Profile Attachment Spinner\n"
            "            if (!isProfileReady) {"
        )
        if old_spinner in text:
            text = text.replace(old_spinner, new_spinner, 1)
            print("OK: key(site.id, initialUrl) around AndroidView")
        else:
            print("WARN: could not close key() block")
    else:
        print("WARN: AndroidView marker for key() not found")

path.write_text(text, encoding="utf-8")
print("Final lines:", text.count("\n") + 1)
print("braces", text.count("{"), text.count("}"))
