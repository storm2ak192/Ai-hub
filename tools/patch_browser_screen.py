#!/usr/bin/env python3
"""Safe patches for BrowserScreen.kt."""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/BrowserScreen.kt")
text = path.read_text(encoding="utf-8")

# 1) Status bar inset fix
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
    "// Main WebView Container: always respect status bar + navigation bar + keyboard\n"
    "        Box(\n"
    "            modifier = Modifier\n"
    "                .weight(1f)\n"
    "                .fillMaxWidth()\n"
    "                .windowInsetsPadding(\n"
    "                    WindowInsets.statusBars\n"
    "                        .union(WindowInsets.navigationBars)\n"
    "                        .union(WindowInsets.ime)\n"
    "                )\n"
    "        ) {"
)
if old_inset in text:
    text = text.replace(old_inset, new_inset, 1)
    print("OK: status bar fix")
else:
    print("WARN: status bar pattern missing")

# 2) Imports
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
print("OK: imports")

# 3) FAB state
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

# 4) Draggable modifier
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

# 5) FAB colors + arrows
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

if "import androidx.compose.ui.graphics.Color\n" not in text:
    text = text.replace(
        "import androidx.compose.ui.Alignment\n",
        "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.graphics.Color\n",
        1,
    )

# 6) WebView speed
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

# 7) Add routing callbacks to BrowserScreen signature
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

# 8) shouldOverrideUrlLoading: route to matching site / dialog for unknown
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
    "// Route main-frame navigations to the matching hub site (same session),\n"
    "                                // e.g. Google OAuth -> Google Account site, chat link -> ChatGPT site.\n"
    "                                if (request.isForMainFrame) {\n"
    "                                    when (val match = UrlSecurityValidator.resolveExternalLink(targetStr, allSites)) {\n"
    "                                        is ExternalLinkMatch.MatchedSite -> {\n"
    "                                            if (match.site.id != currentSite.id) {\n"
    "                                                onOpenMatchedSite(match.site, match.fullUrl)\n"
    "                                                return true\n"
    "                                            }\n"
    "                                            // Same site: let WebView continue normally\n"
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
    "                                        is ExternalLinkMatch.BlockedInvalidUrl -> {\n"
    "                                            return true\n"
    "                                        }\n"
    "                                    }\n"
    "                                }\n\n"
    "                                return false"
)

if old_override in text:
    text = text.replace(old_override, new_override, 1)
    print("OK: in-app site routing")
else:
    print("WARN: shouldOverride OAuth block not found")

path.write_text(text, encoding="utf-8")
print("Final lines:", text.count("\n") + 1)
print("braces", text.count("{"), text.count("}"))
