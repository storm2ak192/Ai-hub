#!/usr/bin/env python3
"""BrowserScreen patches: solution 2 Google login + gap + FABs + non-Google routing."""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/BrowserScreen.kt")
text = path.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# 1) Gap fix: no statusBars in normal mode; keep in fullscreen
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
    "// Main WebView Container: no extra gap under top bar in normal mode\n"
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
# 4-5) Keep the platform WebView user agent unchanged; do not spoof Chrome for Google OAuth.

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
# 7) FAB colors + arrows
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
    print("OK: FAB colors")

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
# 9) Signature
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
# 10) Solution 2 routing:
#     - accounts.google.com / Google OAuth -> stay in SAME WebView (return false)
#     - other known hub sites -> open their card
#     - unknown hosts -> dialog
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
    "// Solution 2: Google login stays in the SAME WebView (shared cookies).\n"
    "                                // Other known AI sites still open in their hub card.\n"
    "                                if (request.isForMainFrame) {\n"
    "                                    val targetHost = UrlSecurityValidator.extractNormalizedHost(targetStr)\n"
    "                                    val isGoogleLogin = targetHost == \"accounts.google.com\" ||\n"
    "                                        (targetHost != null && targetHost.endsWith(\"google.com\") &&\n"
    "                                            (targetStr.contains(\"oauth\", true) ||\n"
    "                                                targetStr.contains(\"ServiceLogin\", true) ||\n"
    "                                                targetStr.contains(\"signin\", true)))\n"
    "                                    if (isGoogleLogin) {\n"
    "                                        // Let this WebView navigate — do not switch site / blank screen\n"
    "                                        return false\n"
    "                                    }\n"
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
    print("OK: solution-2 Google stays in WebView")
else:
    print("WARN: shouldOverride block not found")

# ---------------------------------------------------------------------------
# 10b) Every cross-site link tapped inside a website uses the same chooser.
# Known sites are intentionally not auto-routed here: the user explicitly
# chooses an isolated in-app popup or a Chrome Custom Tab.
# ---------------------------------------------------------------------------
route_start = text.find("                                    when (val match = UrlSecurityValidator.resolveExternalLink(targetStr, allSites)) {")
if route_start >= 0 and "pendingLinkUrl = targetStr" not in text:
    route_end_marker = "\n                                    }\n                                }\n\n                                return false"
    route_end = text.find(route_end_marker, route_start)
    if route_end < 0:
        print("ERROR: could not bound cross-site routing block")
        raise SystemExit(1)
    chooser_route = (
        "                                    val currentHost = UrlSecurityValidator.extractNormalizedHost(currentSite.url)\n"
        "                                    val targetHost = UrlSecurityValidator.extractNormalizedHost(targetStr)\n"
        "                                    if (currentHost != null && targetHost != null &&\n"
        "                                        targetHost != currentHost && !targetHost.endsWith(\\".\\" + currentHost)\n"
        "                                    ) {\n"
        "                                        if (targetStr.startsWith(\\"https://\\", ignoreCase = true) &&\n"
        "                                            UrlSecurityValidator.isSafeWebUrl(targetStr)\n"
        "                                        ) {\n"
        "                                            pendingLinkUrl = targetStr\n"
        "                                        } else {\n"
        "                                            onShowMessage(strings.browserSecurityBlockedTitle)\n"
        "                                        }\n"
        "                                        return true\n"
        "                                    }\n"
        "                                    if (!UrlSecurityValidator.isSafeWebUrl(targetStr)) return true"
    )
    text = text[:route_start] + chooser_route + text[route_end + len("\\n                                    }"):]
    print("OK: same-window cross-site links use the link chooser")
elif "pendingLinkUrl = targetStr" in text:
    print("OK: cross-site link chooser already present")
else:
    print("WARN: route block was not found; no cross-site chooser change applied")

# ---------------------------------------------------------------------------
# 11) Popup: Google login also stays / loads in main flow via parent routing
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
    "val popupHost = UrlSecurityValidator.extractNormalizedHost(u)\n"
    "                                            val popupIsGoogle = popupHost == \"accounts.google.com\" ||\n"
    "                                                (popupHost != null && popupHost.endsWith(\"google.com\") &&\n"
    "                                                    (u.contains(\"oauth\", true) || u.contains(\"signin\", true)))\n"
    "                                            if (popupIsGoogle) {\n"
    "                                                // Load Google login in the MAIN WebView (same session), close popup\n"
    "                                                popupWebView?.destroy()\n"
    "                                                popupWebView = null\n"
    "                                                view?.loadUrl(u)\n"
    "                                                return true\n"
    "                                            }\n"
    "                                            when (val match = UrlSecurityValidator.resolveExternalLink(u, allSites)) {\n"
    "                                                is ExternalLinkMatch.MatchedSite -> {\n"
    "                                                    if (match.site.id != currentSite.id) {\n"
    "                                                        popupWebView?.destroy()\n"
    "                                                        popupWebView = null\n"
    "                                                        onOpenMatchedSite(match.site, match.fullUrl)\n"
    "                                                        return true\n"
    "                                                    }\n"
    "                                                }\n"
    "                                                else -> { }\n"
    "                                            }"
)
if old_popup in text:
    text = text.replace(old_popup, new_popup, 1)
    print("OK: popup Google -> main WebView")
else:
    print("WARN: popup OAuth block not found")

# ---------------------------------------------------------------------------
# 12) Persist cookies after page load (best-effort)
# ---------------------------------------------------------------------------
if "persistActiveSession(currentSite)" not in text:
    old_finished = (
        "override fun onPageFinished(view: WebView?, url: String?) {\n"
        "                                super.onPageFinished(view, url)\n"
        "                                isLoading = false"
    )
    new_finished = (
        "override fun onPageFinished(view: WebView?, url: String?) {\n"
        "                                super.onPageFinished(view, url)\n"
        "                                isLoading = false\n"
        "                                scope.launch {\n"
        "                                    try { sessionManager.persistActiveSession(currentSite) } catch (_: Throwable) {}\n"
        "                                }"
    )
    if old_finished in text:
        text = text.replace(old_finished, new_finished, 1)
        print("OK: persist session on page finished")

path.write_text(text, encoding="utf-8")
print("Final lines:", text.count("\n") + 1)
print("braces", text.count("{"), text.count("}"))
