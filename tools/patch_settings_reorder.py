#!/usr/bin/env python3
"""Add long-press drag reorder to ManageSitesSubScreen."""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/SettingsScreen.kt")
text = path.read_text(encoding="utf-8")

# Imports
if "detectDragGesturesAfterLongPress" not in text:
    text = text.replace(
        "import androidx.compose.foundation.clickable\n",
        "import androidx.compose.foundation.clickable\n"
        "import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\n",
        1,
    )
if "pointerInput" not in text:
    text = text.replace(
        "import androidx.compose.ui.Modifier\n",
        "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.Modifier\n",
        1,
    )
if "DragHandle" not in text:
    text = text.replace(
        "import androidx.compose.material.icons.filled.DeleteOutline\n",
        "import androidx.compose.material.icons.filled.DeleteOutline\n"
        "import androidx.compose.material.icons.filled.DragHandle\n",
        1,
    )
if "graphics.graphicsLayer" not in text and "graphicsLayer" not in text:
    text = text.replace(
        "import androidx.compose.ui.Modifier\n",
        "import androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.ui.Modifier\n",
        1,
    )
if "mutableFloatStateOf" not in text:
    text = text.replace(
        "import androidx.compose.runtime.mutableStateOf\n",
        "import androidx.compose.runtime.mutableFloatStateOf\nimport androidx.compose.runtime.mutableStateOf\n",
        1,
    )
print("OK: imports")

# Signature: add onReorderSites
old_sig = (
    "    onMoveOrder: (String, Boolean) -> Unit,\n"
    "    onClearSiteSession: (SiteEntity) -> Unit,\n"
    "    onDeleteSite: (SiteEntity) -> Unit,\n"
    "    modifier: Modifier = Modifier\n"
    ") {"
)
new_sig = (
    "    onMoveOrder: (String, Boolean) -> Unit,\n"
    "    onReorderSites: (Int, Int) -> Unit,\n"
    "    onClearSiteSession: (SiteEntity) -> Unit,\n"
    "    onDeleteSite: (SiteEntity) -> Unit,\n"
    "    modifier: Modifier = Modifier\n"
    ") {"
)
if "onReorderSites" not in text and old_sig in text:
    text = text.replace(old_sig, new_sig, 1)
    print("OK: signature")
elif "onReorderSites" in text:
    print("OK: signature already")
else:
    print("WARN: signature pattern missing")

# Local drag state after strings =
if "var dragFromIndex" not in text:
    old_col = (
        "    val strings = LocalAppStrings.current\n\n"
        "    Column(\n"
    )
    new_col = (
        "    val strings = LocalAppStrings.current\n"
        "    var dragFromIndex by remember { mutableStateOf<Int?>(null) }\n"
        "    var dragOffsetY by remember { mutableFloatStateOf(0f) }\n\n"
        "    Column(\n"
    )
    if old_col in text:
        text = text.replace(old_col, new_col, 1)
        print("OK: drag state")
    else:
        print("WARN: drag state insert point missing")

# Card modifier with drag
old_card = (
    "                Card(\n"
    "                    shape = MaterialTheme.shapes.medium,\n"
    "                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),\n"
    "                    modifier = Modifier.fillMaxWidth()\n"
    "                ) {"
)
new_card = (
    "                Card(\n"
    "                    shape = MaterialTheme.shapes.medium,\n"
    "                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),\n"
    "                    modifier = Modifier\n"
    "                        .fillMaxWidth()\n"
    "                        .graphicsLayer {\n"
    "                            if (dragFromIndex == index) {\n"
    "                                translationY = dragOffsetY\n"
    "                                shadowElevation = 12f\n"
    "                                alpha = 0.95f\n"
    "                            }\n"
    "                        }\n"
    "                        .pointerInput(index, sites.size) {\n"
    "                            detectDragGesturesAfterLongPress(\n"
    "                                onDragStart = {\n"
    "                                    dragFromIndex = index\n"
    "                                    dragOffsetY = 0f\n"
    "                                },\n"
    "                                onDrag = { change, dragAmount ->\n"
    "                                    change.consume()\n"
    "                                    dragOffsetY += dragAmount.y\n"
    "                                },\n"
    "                                onDragEnd = {\n"
    "                                    val from = dragFromIndex\n"
    "                                    if (from != null) {\n"
    "                                        // ~1 card ≈ 120px; convert drag distance to index delta\n"
    "                                        val delta = (dragOffsetY / 120f).toInt()\n"
    "                                        val to = (from + delta).coerceIn(0, sites.lastIndex)\n"
    "                                        if (to != from) {\n"
    "                                            onReorderSites(from, to)\n"
    "                                        }\n"
    "                                    }\n"
    "                                    dragFromIndex = null\n"
    "                                    dragOffsetY = 0f\n"
    "                                },\n"
    "                                onDragCancel = {\n"
    "                                    dragFromIndex = null\n"
    "                                    dragOffsetY = 0f\n"
    "                                }\n"
    "                            )\n"
    "                        }\n"
    "                ) {"
)
if old_card in text:
    text = text.replace(old_card, new_card, 1)
    print("OK: card drag")
else:
    print("WARN: card pattern missing")

# Replace up/down buttons with drag handle hint (keep optional arrows? user wants long press drag - replace with handle)
old_btns = (
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
    "                            }"
)
new_btns = (
    "                            Icon(\n"
    "                                imageVector = Icons.Filled.DragHandle,\n"
    "                                contentDescription = \"Drag to reorder\",\n"
    "                                tint = MaterialTheme.colorScheme.onSurfaceVariant,\n"
    "                                modifier = Modifier\n"
    "                                    .padding(end = 4.dp)\n"
    "                                    .testTag(\"drag_handle_${site.id}\")\n"
    "                            )"
)
if old_btns in text:
    text = text.replace(old_btns, new_btns, 1)
    print("OK: drag handle icon")
else:
    print("WARN: move buttons not found")

path.write_text(text, encoding="utf-8")
print("braces", text.count("{"), text.count("}"))
