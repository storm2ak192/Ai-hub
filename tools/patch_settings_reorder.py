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
if "graphicsLayer" not in text:
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

# SettingsScreen top-level param
old_ss = (
    "    onMoveSiteOrder: (String, Boolean) -> Unit,\n"
    "    onDeleteSite: (SiteEntity) -> Unit,\n"
)
new_ss = (
    "    onMoveSiteOrder: (String, Boolean) -> Unit,\n"
    "    onReorderSites: (Int, Int) -> Unit,\n"
    "    onDeleteSite: (SiteEntity) -> Unit,\n"
)
if "onReorderSites: (Int, Int)" not in text and old_ss in text:
    text = text.replace(old_ss, new_ss, 1)
    print("OK: SettingsScreen param")

# Call site
old_call = (
    "                onMoveOrder = onMoveSiteOrder,\n"
    "                onClearSiteSession = onClearSingleSiteData,\n"
)
new_call = (
    "                onMoveOrder = onMoveSiteOrder,\n"
    "                onReorderSites = onReorderSites,\n"
    "                onClearSiteSession = onClearSingleSiteData,\n"
)
if old_call in text and "onReorderSites = onReorderSites" not in text:
    text = text.replace(old_call, new_call, 1)
    print("OK: call site")

# ManageSitesSubScreen signature
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
if old_sig in text:
    text = text.replace(old_sig, new_sig, 1)
    print("OK: ManageSites signature")

# Local drag state
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
    # Only in ManageSites - first occurrence after ManageSitesSubScreen is the right one
    idx = text.find("fun ManageSitesSubScreen")
    if idx != -1 and old_col in text[idx:]:
        text = text[:idx] + text[idx:].replace(old_col, new_col, 1)
        print("OK: drag state")
    else:
        print("WARN: drag state insert point missing")

# Card drag modifier — only the ManageSites card pattern (first after ManageSites)
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
    "                                        val delta = (dragOffsetY / 120f).toInt()\n"
    "                                        val to = (from + delta).coerceIn(0, sites.lastIndex)\n"
    "                                        if (to != from) onReorderSites(from, to)\n"
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
idx = text.find("fun ManageSitesSubScreen")
if idx != -1 and old_card in text[idx:]:
    text = text[:idx] + text[idx:].replace(old_card, new_card, 1)
    print("OK: card drag")
else:
    print("WARN: card pattern missing")

# Replace up/down with drag handle
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
