#!/usr/bin/env python3
"""Patch BrowserScreen.kt: status-bar insets + draggable fullscreen FABs."""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/BrowserScreen.kt")
text = path.read_text(encoding="utf-8")

# 1) Status bar inset fix
old_inset = """// Main WebView Container: adapts dynamically to system navigation bar AND on-screen keyboard
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {"""

new_inset = """// Main WebView Container: always respect status bar (top) + navigation bar (bottom) + keyboard
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
print("OK: drag imports")

# 3) FAB drag offset state
if "fabOffsetX" not in text:
    old_fs = "var isFullScreen by remember { mutableStateOf(false) }"
    new_fs = (
        "var isFullScreen by remember { mutableStateOf(false) }\n"
        "    var fabOffsetX by remember { mutableStateOf(0f) }\n"
        "    var fabOffsetY by remember { mutableStateOf(0f) }"
    )
    if old_fs in text:
        text = text.replace(old_fs, new_fs, 1)
        print("OK: fab offset state")
    else:
        print("WARN: isFullScreen state not found")

# 4) Make FAB row draggable
old_mod = """.align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)"""

new_mod = """.align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(12.dp)
                        .offset { IntOffset(fabOffsetX.roundToInt(), fabOffsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                fabOffsetX += dragAmount.x
                                fabOffsetY += dragAmount.y
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)"""

if old_mod in text:
    text = text.replace(old_mod, new_mod, 1)
    print("OK: draggable FABs")
else:
    print("WARN: FAB modifier block not found")

path.write_text(text, encoding="utf-8")
print("Final lines:", text.count("\n") + 1)
