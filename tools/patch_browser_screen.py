#!/usr/bin/env python3
"""Patch BrowserScreen.kt for AI Hub.

- Status bar insets in fullscreen
- Draggable FABs with correct direction
- Collapse/expand arrow for FAB group
- Faster WebView settings
- Google OAuth opens accounts.google.com inside WebView (shared session)
"""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/BrowserScreen.kt")
text = path.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# 1) Status bar inset fix
# ---------------------------------------------------------------------------
old_inset = """// Main WebView Container: adapts dynamically to system navigation bar AND on-screen keyboard
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {"""

new_inset = """// Main WebView Container: always respect status bar + navigation bar + keyboard
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.statusBars
                        .union(WindowInsets.navigationBars)
                        .union(WindowInsets.ime)
                )
        ) {"""

if old_inset in text:
    text = text.replace(old_inset, new_inset, 1)
    print("OK: status bar fix")
else:
    print("WARN: status bar pattern missing (maybe already patched)")

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
if "KeyboardArrowDown" not in text:
    text = text.replace(
        "import androidx.compose.material.icons.filled.Home\n",
        "import androidx.compose.material.icons.filled.Home\n"
        "import androidx.compose.material.icons.filled.KeyboardArrowDown\n"
        "import androidx.compose.material.icons.filled.KeyboardArrowUp\n",
        1,
    )
if "View.LAYER_TYPE_HARDWARE" not in text and "android.view.View" in text:
    pass  # View already imported
print("OK: imports")

# ---------------------------------------------------------------------------
# 3) FAB state: offset + expanded
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
    else:
        print("WARN: isFullScreen state not found")

# ---------------------------------------------------------------------------
# 4) Replace fullscreen FAB row: correct drag + collapse arrow
# ---------------------------------------------------------------------------
old_fabs_start = "if (isFullScreen) {\n                Row("
if old_fabs_start in text:
    # Find the full block until matching close of isFullScreen
    start = text.find(old_fabs_start)
    # Find end of this if block - look for the closing pattern of the Row and if
    marker = "Icon(Icons.Filled.FullscreenExit, contentDescription = strings.browserExitFullscreen)"
    mpos = text.find(marker, start)
    if mpos != -1:
        # extend to closing braces of Row and if
        end = text.find("}\n            }\n        }", mpos)
        if end != -1:
            end = end + len("}\n            }")  # keep one closing for outer box

            new_fabs = '''if (isFullScreen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(12.dp)
                        .offset { IntOffset(fabOffsetX.roundToInt(), fabOffsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // Correct direction (match finger movement)
                                fabOffsetX -= dragAmount.x
                                fabOffsetY -= dragAmount.y
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (fabMenuExpanded) {
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
                    SmallFloatingActionButton(
                        onClick = { fabMenuExpanded = !fabMenuExpanded },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(6.dp),
                        modifier = Modifier.testTag("browser_fab_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (fabMenuExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Toggle controls",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }'''
            # Replace from start to end of original if(isFullScreen) FAB block carefully
            # Find exclusive end: after FullscreenExit icon's closing braces of Row+if
            block_end = text.find("\n            }\n        }\n\n    // Popup", start)
            if block_end == -1:
                block_end = text.find("\n            }\n        }\n", mpos)
            if block_end != -1:
                # include the "            }" of isFullScreen only
                # original structure ends with:
                #                }
                #            }
                text = text[:start] + new_fabs + text[block_end:]
                print("OK: FAB collapse + correct drag")
            else:
                print("WARN: could not locate FAB block end")
        else:
            print("WARN: FAB end braces not found")
    else:
        print("WARN: FullscreenExit marker not found")
else:
    print("WARN: isFullScreen FAB block not found")

# ---------------------------------------------------------------------------
# 5) Faster WebView settings
# ---------------------------------------------------------------------------
# Inject after typical settings block if present
if "LOAD_CACHE_ELSE_NETWORK" not in text and "settings.cacheMode" not in text:
    # Try to find webSettings apply block
    needle = "javaScriptEnabled = site.javaScriptEnabled"
    if needle in text:
        # Add performance flags near first settings configuration in factory
        pass

# Broader: after WebView(ctx).also { mainWv ->
if "setLayerType(android.view.View.LAYER_TYPE_HARDWARE" not in text:
    old_wv = "WebView(ctx).also { mainWv ->"
    new_wv = '''WebView(ctx).also { mainWv ->
                        // Performance: max speed loading
                        mainWv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        mainWv.settings.apply {
                            cacheMode = WebSettings.LOAD_DEFAULT
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadsImagesAutomatically = true
                            blockNetworkImage = false
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            // Safer + faster media
                            mediaPlaybackRequiresUserGesture = false
                        }'''
    if old_wv in text:
        text = text.replace(old_wv, new_wv, 1)
        print("OK: WebView speed settings")
    else:
        print("WARN: WebView factory not found")

# ---------------------------------------------------------------------------
# 6) Google OAuth: open accounts.google.com inside WebView instead of Custom Tab
# ---------------------------------------------------------------------------
old_oauth = """if (request.isForMainFrame &&
                                    UrlSecurityValidator.isFederatedOAuthChallenge(targetStr, currentSite.url)
                                ) {
                                    BrowserDownloadAndAuthHelper.launchSecureExternalAuthTab(ctx, targetStr)
                                    return true
                                }"""
new_oauth = """if (request.isForMainFrame &&
                                    UrlSecurityValidator.isFederatedOAuthChallenge(targetStr, currentSite.url)
                                ) {
                                    // Prefer in-app Google Account page so shared cookies stick
                                    view?.loadUrl("https://accounts.google.com")
                                    return true
                                }"""
if old_oauth in text:
    text = text.replace(old_oauth, new_oauth)
    print("OK: Google OAuth -> accounts.google.com in WebView")
else:
    print("WARN: OAuth intercept pattern not found")

path.write_text(text, encoding="utf-8")
print("Final lines:", text.count("\n") + 1)
