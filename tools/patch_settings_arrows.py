#!/usr/bin/env python3
"""Manage Sites: keep up/down arrows + smooth long-press drag on whole card."""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/SettingsScreen.kt")
text = path.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# Imports needed for smooth drag
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
# 1) Ensure arrow buttons exist (convert old DragHandle-only UI if needed)
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
    print("WARN: could not ensure arrows")

# ---------------------------------------------------------------------------
# 2) Drag state in ManageSitesSubScreen (by site id for stable tracking)
# ---------------------------------------------------------------------------
if "var draggingSiteId" not in text:
    # Remove any old index-based drag state first
    text = text.replace(
        "    val strings = LocalAppStrings.current\n"
        "    var draggingIndex by remember { mutableStateOf<Int?>(null) }\n"
        "    var dragOffsetY by remember { mutableFloatStateOf(0f) }\n\n"
        "    Column(",
        "    val strings = LocalAppStrings.current\n\n"
        "    Column(",
    )
    old = "    val strings = LocalAppStrings.current\n\n    Column("
    # Only inject inside ManageSitesSubScreen
    idx = text.find("fun ManageSitesSubScreen")
    if idx != -1 and old in text[idx:]:
        new = (
            "    val strings = LocalAppStrings.current\n"
            "    var draggingSiteId by remember { mutableStateOf<String?>(null) }\n"
            "    var dragOffsetY by remember { mutableFloatStateOf(0f) }\n\n"
            "    Column("
        )
        text = text[:idx] + text[idx:].replace(old, new, 1)
        print("OK: drag state by site id")
    else:
        print("WARN: manage sites state inject point missing")
else:
    print("OK: drag state already")

# ---------------------------------------------------------------------------
# 3) Full-card smooth long-press drag modifier (anywhere on the card)
# ---------------------------------------------------------------------------
# Prefer replacing a plain fillMaxWidth card inside ManageSites
manage_idx = text.find("fun ManageSitesSubScreen")
if manage_idx < 0:
    print("ERROR: ManageSitesSubScreen not found")
    raise SystemExit(1)

# Find the sites LazyColumn card modifier after ManageSites
section = text[manage_idx:]
old_simple = (
    "                Card(\n"
    "                    shape = MaterialTheme.shapes.medium,\n"
    "                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),\n"
    "                    modifier = Modifier.fillMaxWidth()\n"
    "                ) {"
)

# Also handle leftover graphicsLayer drag variants
old_layered = (
    "                Card(\n"
    "                    shape = MaterialTheme.shapes.medium,\n"
    "                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),\n"
    "                    modifier = Modifier\n"
    "                        .fillMaxWidth()\n"
    "                        .graphicsLayer {\n"
)

new_card = (
    "                Card(\n"
    "                    shape = MaterialTheme.shapes.medium,\n"
    "                    colors = CardDefaults.cardColors(\n"
    "                        containerColor = if (draggingSiteId == site.id) {\n"
    "                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)\n"
    "                        } else {\n"
    "                            MaterialTheme.colorScheme.surface\n"
    "                        }\n"
    "                    ),\n"
    "                    modifier = Modifier\n"
    "                        .fillMaxWidth()\n"
    "                        .graphicsLayer {\n"
    "                            if (draggingSiteId == site.id) {\n"
    "                                translationY = dragOffsetY\n"
    "                                shadowElevation = 28f\n"
    "                                scaleX = 1.03f\n"
    "                                scaleY = 1.03f\n"
    "                                alpha = 0.97f\n"
    "                            }\n"
    "                        }\n"
    "                        .pointerInput(site.id, sites.size) {\n"
    "                            detectDragGesturesAfterLongPress(\n"
    "                                onDragStart = {\n"
    "                                    draggingSiteId = site.id\n"
    "                                    dragOffsetY = 0f\n"
    "                                },\n"
    "                                onDrag = { change, amount ->\n"
    "                                    change.consume()\n"
    "                                    dragOffsetY += amount.y\n"
    "                                    val from = sites.indexOfFirst { it.id == draggingSiteId }\n"
    "                                    if (from < 0) return@detectDragGesturesAfterLongPress\n"
    "                                    // ~card height + spacing; swap when crossing half item\n"
    "                                    val step = 96.dp.toPx()\n"
    "                                    if (dragOffsetY > step * 0.45f && from < sites.lastIndex) {\n"
    "                                        onReorderSites(from, from + 1)\n"
    "                                        dragOffsetY -= step * 0.45f\n"
    "                                    } else if (dragOffsetY < -step * 0.45f && from > 0) {\n"
    "                                        onReorderSites(from, from - 1)\n"
    "                                        dragOffsetY += step * 0.45f\n"
    "                                    }\n"
    "                                },\n"
    "                                onDragEnd = {\n"
    "                                    draggingSiteId = null\n"
    "                                    dragOffsetY = 0f\n"
    "                                },\n"
    "                                onDragCancel = {\n"
    "                                    draggingSiteId = null\n"
    "                                    dragOffsetY = 0f\n"
    "                                }\n"
    "                            )\n"
    "                        }\n"
    "                ) {"
)

if old_simple in section:
    text = text[:manage_idx] + section.replace(old_simple, new_card, 1)
    print("OK: smooth full-card drag on simple card")
elif "draggingSiteId == site.id" in section and "detectDragGesturesAfterLongPress" in section:
    print("OK: smooth drag already applied")
elif old_layered in section:
    # strip old layered block until ") {" of Card - harder; fall through with warning
    print("WARN: layered card present — trying generic replace of first ManageSites Card opener")
    # Replace first occurrence of Card with fillMaxWidth after manage_idx using simple pattern search
    abs_pos = text.find(old_simple, manage_idx)
    if abs_pos < 0:
        # try to find fillMaxWidth-only card
        print("WARN: could not locate card for drag enhance")
    else:
        text = text[:abs_pos] + new_card + text[abs_pos + len(old_simple):]
        print("OK: smooth drag applied (fallback)")
else:
    print("WARN: ManageSites card pattern not found for drag enhance")

path.write_text(text, encoding="utf-8")
print("has arrows", "move_up_" in text and "ArrowUpward" in text)
print("has full-card drag", "draggingSiteId" in text and "detectDragGesturesAfterLongPress" in text)
print("braces", text.count("{"), text.count("}"))
