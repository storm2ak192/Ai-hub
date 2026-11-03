#!/usr/bin/env python3
"""Safe patches for BrowserScreen.kt (status bar, drag direction, speed, OAuth)."""
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

# 2) Imports for drag
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
if "KeyboardArrowDown" not in text:
    text = text.replace(
        "import androidx.compose.material.icons.filled.Home\n",
        "import androidx.compose.material.icons.filled.Home\n"
        "import androidx.compose.material.icons.filled.KeyboardArrowDown\n"
        "import androidx.compose.material.icons.filled.KeyboardArrowUp\n",
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
    else:
        print("WARN: isFullScreen state not found")

# 4) Draggable modifier on FAB row (correct direction for RTL)
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
else:
    print("WARN: FAB modifier block not found")

# 5) Add collapse toggle button before closing of FAB Row (after exit fullscreen FAB)
old_exit = (
    "SmallFloatingActionButton(\n"
    "                        onClick = { isFullScreen = false },\n"
    "                        shape = CircleShape,\n"
    "                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),\n"
    "                        elevation = FloatingActionButtonDefaults.elevation(4.dp),\n"
    "                        modifier = Modifier.testTag(\"browser_exit_fullscreen_btn\")\n"
    "                    ) {\n"
    "                        Icon(Icons.Filled.FullscreenExit, contentDescription = strings.browserExitFullscreen)\n"
    "                    }"
)
new_exit = (
    "if (fabMenuExpanded) {\n"
    "                        SmallFloatingActionButton(\n"
    "                            onClick = onReturnHome,\n"
    "                            shape = CircleShape,\n"
    "                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),\n"
    "                            elevation = FloatingActionButtonDefaults.elevation(4.dp)\n"
    "                        ) {\n"
    "                            Icon(Icons.Filled.Home, contentDescription = strings.browserHome)\n"
    "                        }\n"
    "                        SmallFloatingActionButton(\n"
    "                            onClick = { onToggleDesktopMode(site) },\n"
    "                            shape = CircleShape,\n"
    "                            containerColor = if (site.desktopMode) {\n"
    "                                MaterialTheme.colorScheme.primaryContainer\n"
    "                            } else {\n"
    "                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)\n"
    "                            },\n"
    "                            elevation = FloatingActionButtonDefaults.elevation(4.dp),\n"
    "                            modifier = Modifier.testTag(\"browser_fullscreen_desktop_btn\")\n"
    "                        ) {\n"
    "                            Icon(\n"
    "                                imageVector = Icons.Filled.DesktopWindows,\n"
    "                                contentDescription = strings.browserDesktopMode,\n"
    "                                tint = if (site.desktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface\n"
    "                            )\n"
    "                        }\n"
    "                        SmallFloatingActionButton(\n"
    "                            onClick = { isFullScreen = false },\n"
    "                            shape = CircleShape,\n"
    "                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),\n"
    "                            elevation = FloatingActionButtonDefaults.elevation(4.dp),\n"
    "                            modifier = Modifier.testTag(\"browser_exit_fullscreen_btn\")\n"
    "                        ) {\n"
    "                            Icon(Icons.Filled.FullscreenExit, contentDescription = strings.browserExitFullscreen)\n"
    "                        }\n"
    "                    }\n"
    "                    SmallFloatingActionButton(\n"
    "                        onClick = { fabMenuExpanded = !fabMenuExpanded },\n"
    "                        shape = CircleShape,\n"
    "                        containerColor = MaterialTheme.colorScheme.primaryContainer,\n"
    "                        elevation = FloatingActionButtonDefaults.elevation(6.dp)\n"
    "                    ) {\n"
    "                        Icon(\n"
    "                            imageVector = if (fabMenuExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,\n"
    "                            contentDescription = \"Toggle controls\",\n"
    "                            tint = MaterialTheme.colorScheme.primary\n"
    "                        )\n"
    "                    }"
)

# The original row has three FABs; replace the whole three-FAB content carefully
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
if old_row_body in text:
    text = text.replace(old_row_body, new_exit, 1)
    print("OK: collapse toggle FABs")
else:
    print("WARN: FAB row body not found")

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
    else:
        print("WARN: WebView factory not found")

# 7) Google OAuth -> accounts.google.com in WebView
old_oauth = (
    "if (request.isForMainFrame &&\n"
    "                                    UrlSecurityValidator.isFederatedOAuthChallenge(targetStr, currentSite.url)\n"
    "                                ) {\n"
    "                                    BrowserDownloadAndAuthHelper.launchSecureExternalAuthTab(ctx, targetStr)\n"
    "                                    return true\n"
    "                                }"
)
new_oauth = (
    "if (request.isForMainFrame &&\n"
    "                                    UrlSecurityValidator.isFederatedOAuthChallenge(targetStr, currentSite.url)\n"
    "                                ) {\n"
    "                                    view?.loadUrl(\"https://accounts.google.com\")\n"
    "                                    return true\n"
    "                                }"
)
if old_oauth in text:
    text = text.replace(old_oauth, new_oauth)
    print("OK: OAuth in-app")
else:
    print("WARN: OAuth pattern not found")

path.write_text(text, encoding="utf-8")
print("Final lines:", text.count("\n") + 1)
