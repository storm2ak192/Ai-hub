#!/usr/bin/env python3
"""Manage Sites: keep up/down arrows + smooth long-press drag on whole card."""
from pathlib import Path
import re

path = Path("app/src/main/java/com/example/ui/screens/SettingsScreen.kt")
text = path.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# Imports
# ---------------------------------------------------------------------------
if "detectDragGesturesAfterLongPress" not in text:
    text = text.replace(
        "import androidx.compose.foundation.clickable\n",
        "import androidx.compose.foundation.clickable\n"
        "import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\n",
        1,
    )
if "graphicsLayer" not in text:
    text = text.replace(
        "import androidx.compose.ui.Modifier\n",
        "import androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.ui.Modifier\n",
        1,
    )
if "pointerInput" not in text:
    text = text.replace(
        "import androidx.compose.ui.Modifier\n",
        "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.Modifier\n",
        1,
    )
if "mutableFloatStateOf" not in text:
    text = text.replace(
        "import androidx.compose.runtime.mutableStateOf\n",
        "import androidx.compose.runtime.mutableFloatStateOf\nimport androidx.compose.runtime.mutableStateOf\n",
        1,
    )
print("OK: imports")

# ---------------------------------------------------------------------------
# Ensure arrows (convert DragHandle block if needed)
# ---------------------------------------------------------------------------
if "Icons.Filled.DragHandle" in text and "move_up_" not in text:
    start = text.find("imageVector = Icons.Filled.DragHandle")
    if start != -1:
        block_start = text.rfind("Icon(", 0, start)
        line_start = text.rfind("\n", 0, block_start) + 1
        clear_pos = text.find("onClick = { onClearSiteSession(site) }", start)
        btn_start = text.rfind("IconButton(", 0, clear_pos)
        line_btn = text.rfind("\n", 0, btn_start) + 1
        arrows = (
            "                            IconButton(\n"
            "                                onClick = { onMoveOrder(site.id, true) },\n"
            "                                enabled = index > 0,\n"
            "                                modifier = Modifier.testTag(\"move_up_${site.id}\")\n"
            "                            ) {\n"
            "                                Icon(Icons.Filled.ArrowUpward, contentDescription = strings.moveUp)\n"
            "                            }\n"
            "                            IconButton(\n"
            "                                onClick = { onMoveOrder(site.id, false) },\n"
            "                                enabled = index < sites.lastIndex,\n"
            "                                modifier = Modifier.testTag(\"move_down_${site.id}\")\n"
            "                            ) {\n"
            "                                Icon(Icons.Filled.ArrowDownward, contentDescription = strings.moveDown)\n"
            "                            }\n"
        )
        text = text[:line_start] + arrows + text[line_btn:]
        print("OK: converted DragHandle -> arrows")
elif "move_up_" in text:
    print("OK: arrows present")
else:
    print("WARN: arrows missing")

# ---------------------------------------------------------------------------
# Drag state by site id inside ManageSitesSubScreen only
# ---------------------------------------------------------------------------
manage_idx = text.find("fun ManageSitesSubScreen")
if manage_idx < 0:
    raise SystemExit("ManageSitesSubScreen not found")

# Strip old index-based drag state anywhere in ManageSites
section = text[manage_idx:]
section = section.replace(
    "    var draggingIndex by remember { mutableStateOf<Int?>(null) }\n",
    "",
)
section = re.sub(
    r"    var dragOffsetY by remember \{ mutableFloatStateOf\(0f\) \}\n",
    "",
    section,
    count=1,
)

if "var draggingSiteId" not in section:
    section = section.replace(
        "    val strings = LocalAppStrings.current\n",
        "    val strings = LocalAppStrings.current\n"
        "    var draggingSiteId by remember { mutableStateOf<String?>(null) }\n"
        "    var dragOffsetY by remember { mutableFloatStateOf(0f) }\n",
        1,
    )
    print("OK: drag state by site id")
else:
    print("OK: drag state already")

text = text[:manage_idx] + section

# ---------------------------------------------------------------------------
# Replace ManageSites Card opener with smooth full-card drag version
# ---------------------------------------------------------------------------
NEW_CARD = '''                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (draggingSiteId == site.id) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            if (draggingSiteId == site.id) {
                                translationY = dragOffsetY
                                shadowElevation = 28f
                                scaleX = 1.03f
                                scaleY = 1.03f
                                alpha = 0.97f
                            }
                        }
                        .pointerInput(site.id, sites.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingSiteId = site.id
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffsetY += amount.y
                                    val from = sites.indexOfFirst { it.id == draggingSiteId }
                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                    val step = 96.dp.toPx()
                                    if (dragOffsetY > step * 0.45f && from < sites.lastIndex) {
                                        onReorderSites(from, from + 1)
                                        dragOffsetY -= step * 0.45f
                                    } else if (dragOffsetY < -step * 0.45f && from > 0) {
                                        onReorderSites(from, from - 1)
                                        dragOffsetY += step * 0.45f
                                    }
                                },
                                onDragEnd = {
                                    draggingSiteId = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingSiteId = null
                                    dragOffsetY = 0f
                                }
                            )
                        }
                ) {'''

if "draggingSiteId == site.id" in text and "scaleX = 1.03f" in text:
    print("OK: smooth full-card drag already applied")
else:
    manage_idx = text.find("fun ManageSitesSubScreen")
    # Match either simple or graphicsLayer card inside ManageSites
    patterns = [
        re.compile(
            r"                Card\(\n"
            r"                    shape = MaterialTheme\.shapes\.medium,\n"
            r"                    colors = CardDefaults\.cardColors\(containerColor = MaterialTheme\.colorScheme\.surface\),\n"
            r"                    modifier = Modifier\n"
            r"                        \.fillMaxWidth\(\)\n"
            r"                        \.graphicsLayer \{[\s\S]*?\n"
            r"                        \}\n"
            r"                \) \{",
            re.M,
        ),
        re.compile(
            r"                Card\(\n"
            r"                    shape = MaterialTheme\.shapes\.medium,\n"
            r"                    colors = CardDefaults\.cardColors\(containerColor = MaterialTheme\.colorScheme\.surface\),\n"
            r"                    modifier = Modifier\.fillMaxWidth\(\)\n"
            r"                \) \{",
            re.M,
        ),
    ]
    applied = False
    for pat in patterns:
        m = pat.search(text, manage_idx)
        if m:
            text = text[: m.start()] + NEW_CARD + text[m.end() :]
            applied = True
            print("OK: smooth full-card drag applied")
            break
    if not applied:
        print("ERROR: could not locate ManageSites Card to enhance")
        raise SystemExit(1)

path.write_text(text, encoding="utf-8")
print("has arrows", "move_up_" in text and "ArrowUpward" in text)
print("has smooth drag", "draggingSiteId" in text and "scaleX = 1.03f" in text)
print("has draggingIndex leftover", "draggingIndex" in text)
print("braces", text.count("{"), text.count("}"))
